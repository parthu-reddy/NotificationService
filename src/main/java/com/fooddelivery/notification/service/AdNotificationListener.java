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

        private final com.fooddelivery.common.event.EventBinder eventBinder;

public AdNotificationListener(NotificationEventConsumer notificationConsumer, ObjectMapper objectMapper, com.fooddelivery.common.event.EventBinder eventBinder) {
        this.eventBinder = eventBinder;
        this.notificationConsumer = notificationConsumer;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(attempts = "5", backoff = @Backoff(delay = 1000, multiplier = 2.0), autoCreateTopics = "true", dltStrategy = DltStrategy.FAIL_ON_ERROR, exclude = {com.fooddelivery.common.event.EventBindingException.class}, traversingCauses = "true")
    @KafkaListener(topics = KafkaConstants.TOPIC_AD_EVENTS, groupId = KafkaConstants.GROUP_NOTIFICATION_SERVICE + "-adnotificationlistener")
    public void consumeAdEvent(@Payload String message, @org.springframework.messaging.handler.annotation.Headers java.util.Map<String, Object> headers) {
        try {
            // ad-events carries the event type in a Kafka header and CampaignChangedEvent flat in
            // the body. A body-only check used to return immediately here, so advertisers never
            // received budget-alert or campaign-paused notifications at all.
            String eventTypeStr = com.fooddelivery.common.util.KafkaHeaderUtils.extractEventType(headers, null);
            if (eventTypeStr == null) {
                return;
            }
            EventType eventType;
            try {
                eventType = EventType.valueOf(eventTypeStr);
            } catch (IllegalArgumentException e) {
                return;
            }
            if (eventType == EventType.AD_CAMPAIGN_PAUSED) {
                log.info("Processing Advertisement notification for event type: {}", eventType);
                com.fooddelivery.common.event.CampaignChangedEvent event = eventBinder.bindIf(
                        eventType, eventTypeStr, message,
                        com.fooddelivery.common.event.CampaignChangedEvent.class)
                        .orElseThrow(() -> new IllegalStateException(
                                "bindIf returned empty for " + eventTypeStr
                                        + " despite an exact event-type match"));
                if (event.getAdvertiserId() == null) {
                    log.warn("Cannot send ad notification: missing advertiserId");
                    return;
                }
                String advertiserIdStr = event.getAdvertiserId().toString();
                
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
                        .eventName(com.fooddelivery.common.constants.NotificationTemplate.AD_CAMPAIGN_PAUSED)
                        .payload(Map.of("message", "Campaign event: " + eventType.name()))
                        .build();
                        
                // Dispatch to the internal notification pipeline
                notificationConsumer.consumeNotificationEvent(objectMapper.writeValueAsString(notification), headers);
            }        } catch (Exception e) {
            log.error("Failed to process ad event for notifications", e);
            throw new RuntimeException(e);
        }
    }

    @DltHandler
    public void handleDlt(Object message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        System.err.println("Message failed 5 times and sent to DLT: " + topic + " - " + message);
    }
}
