package com.fooddelivery.notification.service;

import com.fooddelivery.notification.domain.*;
import com.fooddelivery.notification.dto.NotificationRequestEvent;
import com.fooddelivery.notification.exception.InvalidTemplateException;
import com.fooddelivery.notification.exception.UserOptedOutException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.repository.UserPreferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationRouterService {

    private final RateLimitingService rateLimitingService;
    private final NotificationTemplateRepository templateRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final NotificationAuditLogRepository auditLogRepository;
    
    private final FcmService fcmService;
    private final ExotelSmsService exotelSmsService;
    private final GupshupWhatsAppService gupshupWhatsAppService;
    private final AwsSesEmailService awsSesEmailService;
    private final BrevoEmailService brevoEmailService;

    @org.springframework.beans.factory.annotation.Value("${platform.providers.email.active:aws}")
    private String activeEmailProvider;

    public NotificationRouterService(RateLimitingService rateLimitingService,
                                     NotificationTemplateRepository templateRepository,
                                     UserPreferenceRepository userPreferenceRepository,
                                     UserDeviceRepository userDeviceRepository,
                                     NotificationAuditLogRepository auditLogRepository,
                                     FcmService fcmService,
                                     ExotelSmsService exotelSmsService,
                                     GupshupWhatsAppService gupshupWhatsAppService,
                                     AwsSesEmailService awsSesEmailService,
                                     BrevoEmailService brevoEmailService) {
        this.rateLimitingService = rateLimitingService;
        this.templateRepository = templateRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.auditLogRepository = auditLogRepository;
        this.fcmService = fcmService;
        this.exotelSmsService = exotelSmsService;
        this.gupshupWhatsAppService = gupshupWhatsAppService;
        this.awsSesEmailService = awsSesEmailService;
        this.brevoEmailService = brevoEmailService;
    }

    @Transactional
    public void routeAndDispatch(NotificationRequestEvent event) {
        if (event == null || event.getUserId() == null || event.getEventName() == null || event.getChannel() == null) {
            throw new IllegalArgumentException("Invalid event payload: userId, eventName, and channel are required.");
        }

        if (event.getChannel() != ChannelType.PUSH && (event.getExplicitRecipient() == null || event.getExplicitRecipient().isBlank())) {
            throw new IllegalArgumentException("Recipient address is missing for channel " + event.getChannel());
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
            switch (event.getChannel()) {
                case PUSH:
                    providerMessageId = handlePush(event, template);
                    break;
                case SMS:
                    providerMessageId = handleSms(event, template);
                    break;
                case WHATSAPP:
                    providerMessageId = handleWhatsApp(event, template);
                    break;
                case EMAIL:
                    providerMessageId = handleEmail(event, template);
                    break;
                default:
                    throw new UnsupportedOperationException("Channel not supported");
            }
        } catch (Exception e) {
            log.error("Failed to dispatch notification for event {}", event.getEventId(), e);
            createAuditLog(event, template, null, DeliveryStatus.FAILED, e.getMessage());
            throw new RuntimeException(e); // Let Kafka retry
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
        auditLog.setUserId(event.getUserId());
        auditLog.setChannel(event.getChannel());
        auditLog.setTemplate(template);
        auditLog.setProviderMessageId(providerMessageId);
        auditLog.setStatus(status);
        auditLog.setErrorReason(error);
        auditLog.setRecipientAddress(event.getExplicitRecipient() != null ? event.getExplicitRecipient() : "looked_up_from_db"); // Real app would look up
        auditLogRepository.save(auditLog);
    }

    private String handlePush(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndIsActiveTrue(event.getUserId());
        if (devices.isEmpty()) {
            throw new InvalidTemplateException("No active devices found for user.");
        }
        
        String title = "Notification";
        String body = hydrateTemplate(template.getContent(), event.getTemplateParams());
        
        if (devices.size() == 1) {
            return fcmService.sendDirectNotification(devices.get(0).getFcmToken(), title, body, event.getPayload());
        } else {
            fcmService.sendMulticastNotification(devices.stream().map(UserDevice::getFcmToken).collect(Collectors.toList()), title, body);
            return "multicast-" + event.getEventId();
        }
    }

    private String handleSms(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        String content = hydrateTemplate(template.getContent(), event.getTemplateParams());
        return exotelSmsService.dispatchSms(event.getExplicitRecipient(), content, "FOODDL", template.getExternalEntityId(), template.getExternalTemplateId());
    }

    private String handleWhatsApp(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        return gupshupWhatsAppService.dispatchWhatsAppTemplate(event.getExplicitRecipient(), template.getExternalTemplateId(), event.getTemplateParams());
    }

    private String handleEmail(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        String content = hydrateTemplate(template.getContent(), event.getTemplateParams());
        if ("brevo".equalsIgnoreCase(activeEmailProvider)) {
            return brevoEmailService.sendHtmlEmail("noreply@fooddelivery.com", event.getExplicitRecipient(), "Food Delivery Update", content);
        } else {
            return awsSesEmailService.sendHtmlEmail("noreply@fooddelivery.com", event.getExplicitRecipient(), "Food Delivery Update", content);
        }
    }

    private String hydrateTemplate(String template, List<String> params) {
        if (params == null || params.isEmpty()) {
            return template;
        }
        String hydrated = template;
        for (int i = 0; i < params.size(); i++) {
            hydrated = hydrated.replace("{" + (i + 1) + "}", params.get(i));
        }
        return hydrated;
    }
}
