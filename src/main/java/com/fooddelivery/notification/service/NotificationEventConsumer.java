package com.fooddelivery.notification.service;

import com.fooddelivery.notification.domain.DeliveryStatus;
import com.fooddelivery.notification.domain.NotificationAuditLog;
import com.fooddelivery.notification.dto.NotificationRequestEvent;
import com.fooddelivery.notification.exception.InvalidTemplateException;
import com.fooddelivery.notification.exception.RateLimitExceededException;
import com.fooddelivery.notification.exception.UserOptedOutException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
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
                    IllegalArgumentException.class,
                    UserOptedOutException.class,
                    InvalidTemplateException.class,
                    RateLimitExceededException.class
            }
    )
    @KafkaListener(topics = "platform.notifications.dispatch", groupId = "notification-service-group")
    public void consumeNotificationEvent(@Payload NotificationRequestEvent event) {
        log.info("Received notification request for user {} on channel {}", event.getUserId(), event.getChannel());
        
        // The router service is responsible for rate-limiting checks and provider delegation
        routerService.routeAndDispatch(event);
    }

    @KafkaListener(topics = "platform.notifications.dispatch.DLT", groupId = "notification-service-group")
    public void processDeadLetterTopic(@Payload NotificationRequestEvent failedEvent) {
        log.error("Terminal failure for event {}. Moving to manual intervention queue.", failedEvent.getEventId());
        
        // Persist final failure state to the notification_audit_logs table to mark as FAILED
        NotificationAuditLog auditLog = new NotificationAuditLog();
        auditLog.setUserId(failedEvent.getUserId());
        auditLog.setChannel(failedEvent.getChannel());
        auditLog.setRecipientAddress(failedEvent.getExplicitRecipient() != null ? failedEvent.getExplicitRecipient() : "unknown");
        auditLog.setStatus(DeliveryStatus.FAILED);
        auditLog.setErrorReason("Max retries exceeded");
        auditLogRepository.save(auditLog);
    }
}
