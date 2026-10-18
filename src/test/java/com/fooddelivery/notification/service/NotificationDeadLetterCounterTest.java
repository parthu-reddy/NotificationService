package com.fooddelivery.notification.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.EventBinder;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Dead letters are counted by template, and every series exists at 0 from startup: increase() over a series that first
 * appears at 1 is 0, so OperatorAlertDeadLettered could never fire on the first dead letter otherwise.
 */
class NotificationDeadLetterCounterTest {

    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationEventConsumer consumer = new NotificationEventConsumer(mock(NotificationRouterService.class),
            mock(NotificationAuditLogRepository.class), new EventBinder(mapper, Validation.buildDefaultValidatorFactory().getValidator()),
            metrics);

    private double deadLettered(String template) {
        var counter = metrics.find("notifications.dead_lettered").tag("template", template).counter();
        assertNotNull(counter, "no series for " + template);
        return counter.count();
    }

    @Test
    void everyTemplateHasASeriesAtZeroFromStartup() {
        for (NotificationTemplate template : NotificationTemplate.values()) assertEquals(0.0, deadLettered(template.name()));
        assertEquals(0.0, deadLettered("UNKNOWN"));
    }

    @Test
    void aDeadLetteredOperatorAlertIsCountedUnderItsTemplate() throws Exception {
        String payload = mapper.writeValueAsString(NotificationRequestEvent.builder().eventName(NotificationTemplate.OPERATOR_ALERT)
                .channel(ChannelType.EMAIL).explicitRecipient("operator@example.test").templateParams(List.of("x")).build());
        consumer.processDeadLetterTopic(payload, "Brevo Dispatch Failed", Map.of());
        consumer.processDeadLetterTopic(null, "unreadable", Map.of());
        assertEquals(1.0, deadLettered("OPERATOR_ALERT"));
        assertEquals(1.0, deadLettered("UNKNOWN"));
    }
}
