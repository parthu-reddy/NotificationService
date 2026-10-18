package com.fooddelivery.notification.service;

import com.fooddelivery.notification.dto.AlertmanagerWebhook;
import com.fooddelivery.notification.config.OperatorAlertProperties;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.outbox.service.OutboxProcessor;
import com.fooddelivery.notification.domain.NotificationAuditLog;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.repository.UserPreferenceRepository;
import com.fooddelivery.notification.service.BrevoEmailService;
import com.fooddelivery.notification.service.NotificationEventConsumer;
import com.fooddelivery.notification.service.NotificationRouterService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The record OperatorAlertPublisher writes is the record the real consumer, EventBinder and router accept: published,
 * consumed with its own headers, and sent through Brevo to the operator, without a broker (the module has none in
 * tests). A shape drift between the two ends fails here, not on Dev.
 */
class OperatorAlertRoundTripTest {

    private static final String OPERATOR = "operator@example.test";

    @Test
    @SuppressWarnings("unchecked")
    void aWebhookAlertIsPublishedConsumedAndEmailedToTheOperator() throws Exception {
        var properties = new OperatorAlertProperties("test-operator-alert-token-0123456789abcdef", OPERATOR, "alerts@example.test");
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var mapper = new ObjectMapper();
        var publisher = new OperatorAlertPublisher(kafka, mapper, properties);

        AlertmanagerWebhook webhook;
        try (var in = getClass().getResourceAsStream("/alertmanager/webhook-v4-firing.json")) {
            webhook = mapper.readValue(in, AlertmanagerWebhook.class);
        }
        String eventId = publisher.publish(new OperatorAlertFormatter().format(webhook));

        var sentRecord = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sentRecord.capture());
        ProducerRecord<String, String> record = sentRecord.getValue();
        assertEquals(KafkaConstants.TOPIC_NOTIFICATIONS_DISPATCH, record.topic());
        assertEquals(OperatorAlertPublisher.KEY, record.key());
        Map<String, Object> headers = new HashMap<>();
        for (Header h : record.headers()) headers.put(h.key(), h.value());
        assertEquals("NOTIFICATION_DISPATCH", new String((byte[]) headers.get(OutboxProcessor.HEADER_EVENT_TYPE), StandardCharsets.UTF_8));
        assertEquals("NOTIFICATION", new String((byte[]) headers.get(OutboxProcessor.HEADER_AGGREGATE_TYPE), StandardCharsets.UTF_8));
        assertEquals(eventId, new String((byte[]) headers.get("eventId"), StandardCharsets.UTF_8));

        var templates = mock(NotificationTemplateRepository.class);
        var template = new com.fooddelivery.notification.domain.NotificationTemplate();
        template.setContent("{1}");
        when(templates.findByEventNameAndChannelAndIsActiveTrue("OPERATOR_ALERT", ChannelType.EMAIL)).thenReturn(Optional.of(template));
        var audit = mock(NotificationAuditLogRepository.class);
        var brevo = mock(BrevoEmailService.class);
        when(brevo.sendHtmlEmail(any(), any(), any(), any())).thenReturn("brevo-1");
        var metrics = new SimpleMeterRegistry();
        var router = new NotificationRouterService(templates, mock(UserPreferenceRepository.class), mock(UserDeviceRepository.class),
                audit, List.of(), metrics, new OperatorAlertSender(brevo, properties, metrics));
        var consumer = new NotificationEventConsumer(router, audit,
                new EventBinder(mapper, Validation.buildDefaultValidatorFactory().getValidator()), metrics);

        consumer.consumeNotificationEvent(record.value(), headers);

        var html = ArgumentCaptor.forClass(String.class);
        verify(brevo).sendHtmlEmail(eq("alerts@example.test"), eq(OPERATOR), eq("[FIRING:1] PartnerApplicationReviewOverdue"), html.capture());
        assertTrue(html.getValue().contains("Partner applications waiting for review"), html.getValue());
        var row = ArgumentCaptor.forClass(NotificationAuditLog.class);
        verify(audit).save(row.capture());
        assertEquals(eventId, row.getValue().getEventId());
        assertEquals(1.0, metrics.counter("operator.alerts.email", "result", "sent").count());
    }
}
