package com.fooddelivery.notification.service;

import com.fooddelivery.notification.domain.DeliveryStatus;
import com.fooddelivery.notification.domain.NotificationAuditLog;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.exception.TerminalNotificationException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class NotificationEventConsumer {

    private final NotificationRouterService routerService;
    private final NotificationAuditLogRepository auditLogRepository;

    public NotificationEventConsumer(NotificationRouterService routerService, NotificationAuditLogRepository auditLogRepository) {
        this.routerService = routerService;
        this.auditLogRepository = auditLogRepository;
    }

    @RetryableTopic(
            attempts = "4", // Initial attempt + 3 retries
            backOff = @BackOff(delay = 2000, multiplier = 2.0, maxDelay = 10000), // 2s, 4s, 8s backoff
            autoCreateTopics = "true",
            exclude = {
                    TerminalNotificationException.class
            }
    )
    @KafkaListener(topics = "platform.notifications.dispatch", groupId = "notification-service-group")
    public void consumeNotificationEvent(@Payload NotificationRequestEvent event) {
        log.info("Received notification request for user {} on channel {}", event.getUserId(), event.getChannel());
        
        // The router service is responsible for rate-limiting checks and provider delegation
        routerService.routeAndDispatch(event);
    }

    @DltHandler
    public void processDeadLetterTopic(@Payload(required = false) NotificationRequestEvent failedEvent, @org.springframework.messaging.handler.annotation.Header(name = org.springframework.kafka.support.KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        if (failedEvent == null) {
            log.error("Received bad payload in DLT. Exception: {}", exceptionMessage);
            return;
        }

        log.error("Terminal failure for event {}. Moving to manual intervention queue. Exception: {}", failedEvent.getEventId(), exceptionMessage);

        NotificationAuditLog auditLog = new NotificationAuditLog();
        // Provide fallbacks for malformed payloads to avoid DB constraint violations
        auditLog.setUserId(failedEvent.getUserId() != null ? failedEvent.getUserId() : java.util.UUID.randomUUID());
        auditLog.setChannel(failedEvent.getChannel() != null ? failedEvent.getChannel() : com.fooddelivery.common.enums.ChannelType.EMAIL);
        auditLog.setRecipientAddress(failedEvent.getExplicitRecipient() != null && !failedEvent.getExplicitRecipient().isBlank() ? failedEvent.getExplicitRecipient() : "unknown");
        auditLog.setStatus(DeliveryStatus.FAILED);
        auditLog.setErrorReason("DLT Intervention: " + exceptionMessage);
        auditLogRepository.save(auditLog);
    }
}
