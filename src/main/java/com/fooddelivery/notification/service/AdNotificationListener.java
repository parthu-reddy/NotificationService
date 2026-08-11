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
public class AdNotificationListener {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdNotificationListener.class);
    private final NotificationEventConsumer notificationConsumer;
    private final ObjectMapper objectMapper;

    public AdNotificationListener(NotificationEventConsumer notificationConsumer, ObjectMapper objectMapper) {
        this.notificationConsumer = notificationConsumer;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0), autoCreateTopics = "true", dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = KafkaConstants.TOPIC_AD_EVENTS, groupId = KafkaConstants.GROUP_NOTIFICATION_SERVICE)
    public void consumeAdEvent(@Payload String message) {
        try {
            JsonNode payloadNode = objectMapper.readTree(message);
            if (!payloadNode.has("eventType")) {
                return;
            }
            String eventTypeStr = payloadNode.get("eventType").asText();
            EventType eventType;
            try {
                eventType = EventType.valueOf(eventTypeStr);
            } catch (IllegalArgumentException e) {
                return;
            }
            if (eventType == EventType.AD_BUDGET_ALERT || eventType == EventType.AD_CAMPAIGN_PAUSED) {
                log.info("Processing Advertisement notification for event type: {}", eventType);
                String advertiserIdStr = payloadNode.path("advertiserId").asText(null);
                if (advertiserIdStr == null && payloadNode.has("payload")) {
                    JsonNode nested = objectMapper.readTree(payloadNode.get("payload").asText());
                    advertiserIdStr = nested.path("advertiserId").asText(null);
                }
                if (advertiserIdStr == null || advertiserIdStr.isBlank()) {
                    log.warn("Cannot send ad notification: missing advertiserId");
                    return;
                }
                NotificationRequestEvent notification = NotificationRequestEvent.builder().eventId(UUID.randomUUID().toString()).userId(UUID.fromString(advertiserIdStr)).channel(ChannelType.EMAIL).eventName(eventType.name()).payload(Map.of("message", "Campaign event: " + eventType.name())).build();
                // Dispatch to the internal notification pipeline
                notificationConsumer.consumeNotificationEvent(notification);
            }
        } catch (Exception e) {
            log.error("Failed to process ad event for notifications", e);
        }
    }

    @DltHandler
    public void handleDlt(Object message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        System.err.println("Message failed 5 times and sent to DLT: " + topic + " - " + message);
    }
}
