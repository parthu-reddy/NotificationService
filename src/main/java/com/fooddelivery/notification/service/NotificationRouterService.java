package com.fooddelivery.notification.service;

import com.fooddelivery.common.enums.ChannelType;

import com.fooddelivery.notification.domain.*;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.exception.InvalidTemplateException;
import com.fooddelivery.notification.exception.UserOptedOutException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.repository.UserPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationRouterService {

    private final RateLimitingService rateLimitingService;
    private final NotificationTemplateRepository templateRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final NotificationAuditLogRepository auditLogRepository;
    
    private final Map<ChannelType, NotificationChannelStrategy> strategyMap;

    public NotificationRouterService(RateLimitingService rateLimitingService,
                                     NotificationTemplateRepository templateRepository,
                                     UserPreferenceRepository userPreferenceRepository,
                                     UserDeviceRepository userDeviceRepository,
                                     NotificationAuditLogRepository auditLogRepository,
                                     List<NotificationChannelStrategy> strategies) {
        this.rateLimitingService = rateLimitingService;
        this.templateRepository = templateRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.auditLogRepository = auditLogRepository;
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(NotificationChannelStrategy::getSupportedChannel, Function.identity()));
    }

    public void routeAndDispatch(NotificationRequestEvent event) {
        if (event == null || event.getUserId() == null || event.getEventName() == null || event.getChannel() == null) {
            throw new com.fooddelivery.notification.exception.InvalidPayloadException("Invalid event payload: userId, eventName, and channel are required.");
        }

        if (event.getChannel() != ChannelType.PUSH && (event.getExplicitRecipient() == null || event.getExplicitRecipient().isBlank())) {
            throw new com.fooddelivery.notification.exception.InvalidPayloadException("Recipient address is missing for channel " + event.getChannel());
        }

        // Idempotency check: if eventId is already processed, skip
        if (event.getEventId() != null && auditLogRepository.existsByEventId(event.getEventId())) {
            log.info("Event {} already processed. Skipping.", event.getEventId());
            return;
        }

        // Enforce rate limiting
        rateLimitingService.enforceRateLimit(event.getUserId().toString(), event.getEventName());

        // Check user preferences
        UserPreference prefs = userPreferenceRepository.findByUserId(event.getUserId())
                .orElse(new UserPreference()); // default to true

        if (!isChannelEnabled(prefs, event.getChannel())) {
            throw new UserOptedOutException("User opted out of " + event.getChannel() + " channel.");
        }

        // Fetch template
        NotificationTemplate template = templateRepository.findByEventNameAndChannelAndIsActiveTrue(event.getEventName(), event.getChannel())
                .orElseThrow(() -> new InvalidTemplateException("Template not found for " + event.getEventName() + " on " + event.getChannel()));

        String providerMessageId = null;

        try {
            NotificationChannelStrategy strategy = strategyMap.get(event.getChannel());
            if (strategy == null) {
                throw new com.fooddelivery.notification.exception.InvalidPayloadException("Channel not supported: " + event.getChannel());
            }
            providerMessageId = strategy.dispatch(event, template);
        } catch (com.fooddelivery.notification.exception.TerminalNotificationException e) {
            log.error("Terminal failure for event {}", event.getEventId(), e);
            throw e; // Rethrow as is so Kafka DLT logic catches it and logs it
        } catch (Exception e) {
            log.error("Failed to dispatch notification for event {}", event.getEventId(), e);
            throw new RuntimeException(e); // Let Kafka retry, DLT will log if all retries fail
        }

        if (providerMessageId != null) {
            createAuditLog(event, template, providerMessageId, DeliveryStatus.QUEUED, null);
        }
    }

    private boolean isChannelEnabled(UserPreference prefs, ChannelType channel) {
        switch (channel) {
            case SMS: return prefs.getSmsEnabled();
            case PUSH: return prefs.getPushEnabled();
            case EMAIL: return prefs.getEmailEnabled();
            case WHATSAPP: return prefs.getWhatsappEnabled();
            default: return false;
        }
    }

    private void createAuditLog(NotificationRequestEvent event, NotificationTemplate template, String providerMessageId, DeliveryStatus status, String error) {
        NotificationAuditLog auditLog = new NotificationAuditLog();
        auditLog.setEventId(event.getEventId());
        auditLog.setUserId(event.getUserId());
        auditLog.setChannel(event.getChannel());
        auditLog.setTemplate(template);
        auditLog.setProviderMessageId(providerMessageId);
        auditLog.setStatus(status);
        auditLog.setErrorReason(error);
        auditLog.setRecipientAddress(event.getExplicitRecipient() != null ? event.getExplicitRecipient() : "looked_up_from_db"); // Real app would look up
        auditLogRepository.save(auditLog);
    }

}
