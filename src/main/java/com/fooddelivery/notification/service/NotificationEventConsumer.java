package com.fooddelivery.notification.service;

import com.fooddelivery.notification.domain.DeliveryStatus;
import com.fooddelivery.notification.domain.NotificationAuditLog;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.entity.IdempotencyKey;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.notification.exception.TerminalNotificationException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@lombok.extern.slf4j.Slf4j
public class NotificationEventConsumer {

    private final NotificationRouterService routerService;
    private final NotificationAuditLogRepository auditLogRepository;
    private final com.fooddelivery.common.event.EventBinder eventBinder;

    public NotificationEventConsumer(NotificationRouterService routerService, NotificationAuditLogRepository auditLogRepository, com.fooddelivery.common.event.EventBinder eventBinder) {
        this.routerService = routerService;
        this.auditLogRepository = auditLogRepository;
        this.eventBinder = eventBinder;
    }

    // Initial attempt + 3 retries
    // 2s, 4s, 8s backoff
    @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000), autoCreateTopics = "true", exclude = {com.fooddelivery.common.event.EventBindingException.class, TerminalNotificationException.class}, traversingCauses = "true")
    @KafkaListener(topics = com.fooddelivery.common.constants.KafkaConstants.TOPIC_NOTIFICATIONS_DISPATCH, groupId = com.fooddelivery.common.constants.KafkaConstants.GROUP_NOTIFICATION_SERVICE + "-notificationeventconsumer")
    public void consumeNotificationEvent(String payload, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        NotificationRequestEvent event = eventBinder.bind(payload, NotificationRequestEvent.class);

        log.info("Received notification request for user {} on channel {}", event.getUserId(), event.getChannel());
        
        String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
        final String resolvedEventId = (extractedEventId != null) ? extractedEventId : event.getEventId();

        if (resolvedEventId == null) {
            log.warn("Missing eventId for NotificationRequestEvent. Processing without idempotency key.");
            routerService.routeAndDispatch(event);
            return;
        }

        // We rely on NotificationRouterService's native idempotency via NotificationAuditLogRepository 
        // to avoid holding a PostgreSQL connection open during external network calls (Twilio, SendGrid).
        event.setEventId(resolvedEventId);
        routerService.routeAndDispatch(event);
    }

    @DltHandler
    public void processDeadLetterTopic(@Payload(required = false) String failedPayload, @org.springframework.messaging.handler.annotation.Header(name = org.springframework.kafka.support.KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
                                       @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        String replay = com.fooddelivery.common.util.KafkaHeaderUtils.deadLetterPosition(headers);
        if (failedPayload == null) {
            log.error("Received bad payload in DLT. Exception: {} replay={}", exceptionMessage, replay);
            return;
        }
        
        NotificationRequestEvent failedEvent = null;
        try {
            failedEvent = eventBinder.bind(failedPayload, NotificationRequestEvent.class);
        } catch (Exception e) {
            log.warn("Could not parse payload in DLT: {}", failedPayload);
        }
        
        String eventId = failedEvent != null && failedEvent.getEventId() != null ? failedEvent.getEventId() : "UNKNOWN";
        log.error("Terminal failure for event {}. Moving to manual intervention queue. Exception: {} replay={}", eventId, exceptionMessage, replay);
        
        NotificationAuditLog auditLog = new NotificationAuditLog();
        // Provide fallbacks for malformed payloads to avoid DB constraint violations
        auditLog.setUserId(failedEvent != null && failedEvent.getUserId() != null ? failedEvent.getUserId() : new java.util.UUID(0L, 0L));
        auditLog.setChannel(failedEvent != null && failedEvent.getChannel() != null ? failedEvent.getChannel() : com.fooddelivery.common.enums.ChannelType.EMAIL);
        auditLog.setRecipientAddress(failedEvent != null && failedEvent.getExplicitRecipient() != null && !failedEvent.getExplicitRecipient().isBlank() ? failedEvent.getExplicitRecipient() : "unknown");
        auditLog.setStatus(DeliveryStatus.FAILED);
        auditLog.setErrorReason("DLT Intervention: " + exceptionMessage);
        auditLogRepository.save(auditLog);
    }
}
