package com.fooddelivery.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.Map;

@Component
@lombok.extern.slf4j.Slf4j
public class AdNotificationListener {
    @java.lang.SuppressWarnings("all")

    private final NotificationEventConsumer notificationConsumer;
    private final ObjectMapper objectMapper;

    public AdNotificationListener(NotificationEventConsumer notificationConsumer, ObjectMapper objectMapper) {
        this.notificationConsumer = notificationConsumer;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0), autoCreateTopics = "true", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = KafkaConstants.TOPIC_AD_EVENTS, groupId = KafkaConstants.GROUP_NOTIFICATION_SERVICE)
    public void consumeAdEvent(@Payload String message, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        try {
            JsonNode root = objectMapper.readTree(message);
            // ad-events carries a FLAT Campaign with the event type in a Kafka header, so the
            // previous body-only check returned immediately and advertisers never received
            // budget-alert or campaign-paused notifications.
            String eventTypeStr = com.fooddelivery.common.util.EventPayloadUtils.resolveEventType(root, headers);
            if (eventTypeStr == null) {
                return;
            }
            JsonNode payloadNode = com.fooddelivery.common.util.EventPayloadUtils.unwrapPayload(root);
            EventType eventType;
            try {
                eventType = EventType.valueOf(eventTypeStr);
            } catch (IllegalArgumentException e) {
                return;
            }
            if (eventType == EventType.AD_BUDGET_ALERT || eventType == EventType.AD_CAMPAIGN_PAUSED) {
                log.info("Processing Advertisement notification for event type: {}", eventType);
                // unwrapPayload has already resolved the envelope-vs-flat distinction, including
                // the double-encoded payload string, so no nested fallback is needed here.
                String advertiserIdStr = payloadNode.path("advertiserId").asText(null);
                if (advertiserIdStr == null || advertiserIdStr.isBlank()) {
                    log.warn("Cannot send ad notification: missing advertiserId");
                    return;
                }
                
                String extractedEventId = com.fooddelivery.common.util.KafkaHeaderUtils.extractHeaderValue(headers, "eventId");
                final String resolvedEventId;
                if (extractedEventId == null) {
                    resolvedEventId = UUID.nameUUIDFromBytes(message.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                } else {
                    resolvedEventId = extractedEventId;
                }

                NotificationRequestEvent notification = NotificationRequestEvent.builder()
                        .eventId(resolvedEventId)
                        .userId(UUID.fromString(advertiserIdStr))
                        .channel(ChannelType.EMAIL)
                        .eventName(eventType.name())
                        .payload(Map.of("message", "Campaign event: " + eventType.name()))
                        .build();
                        
                // Dispatch to the internal notification pipeline
                notificationConsumer.consumeNotificationEvent(notification, headers);
            }
        } catch (Exception e) {
            log.error("Failed to process ad event for notifications", e);
            throw new RuntimeException(e);
        }
    }

    @DltHandler
    public void handleDlt(Object message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        System.err.println("Message failed 5 times and sent to DLT: " + topic + " - " + message);
    }
}
