package com.fooddelivery.notification.service;

import com.fooddelivery.notification.config.OperatorAlertProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.AggregateType;
import com.fooddelivery.common.constants.EventType;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.common.outbox.service.OutboxProcessor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Puts one operator alert on the notification topic, in the shape the outbox gives every other notification: same
 * topic, same {@code eventType}/{@code aggregateType}/{@code eventId} headers, same synchronous 5 s acknowledgement.
 * No outbox table: the webhook changes no data, so there is no transaction for the publish to be atomic with; a
 * failed publish is reported to Alertmanager, which retries (it retries 5xx only).
 */
@Component
public class OperatorAlertPublisher {

    static final String KEY = "operator-alert";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final OperatorAlertProperties properties;

    public OperatorAlertPublisher(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                                  OperatorAlertProperties properties) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** @return the published event's id */
    public String publish(OperatorAlertFormatter.OperatorAlert alert) {
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .eventName(NotificationTemplate.OPERATOR_ALERT)
                .channel(ChannelType.EMAIL)
                .explicitRecipient(properties.recipient())
                .templateParams(List.of(alert.html()))
                .payload(Map.of("subject", alert.subject()))
                .build();
        ProducerRecord<String, String> record =
                new ProducerRecord<>(KafkaConstants.TOPIC_NOTIFICATIONS_DISPATCH, KEY, json(event));
        record.headers().add(OutboxProcessor.HEADER_EVENT_TYPE,
                EventType.NOTIFICATION_DISPATCH.name().getBytes(StandardCharsets.UTF_8));
        record.headers().add(OutboxProcessor.HEADER_AGGREGATE_TYPE,
                AggregateType.NOTIFICATION.name().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublishFailed(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new PublishFailed(e);
        }
        return event.getEventId();
    }

    private String json(NotificationRequestEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("NotificationRequestEvent did not serialise", e);
        }
    }

    /** The topic did not acknowledge the alert; Alertmanager should retry it. */
    public static final class PublishFailed extends RuntimeException {
        public PublishFailed(Throwable cause) {
            super("operator alert not acknowledged by Kafka: " + cause.getClass().getSimpleName(), cause);
        }
    }
}
