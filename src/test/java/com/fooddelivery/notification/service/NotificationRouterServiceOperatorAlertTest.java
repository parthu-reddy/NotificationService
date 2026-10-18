package com.fooddelivery.notification.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationAuditLog;
import com.fooddelivery.notification.exception.InvalidPayloadException;
import com.fooddelivery.notification.config.OperatorAlertProperties;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.repository.UserPreferenceRepository;
import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The router's OPERATOR_ALERT branch: only the configured operator, real Brevo send, customer path untouched. */
class NotificationRouterServiceOperatorAlertTest {

    private static final String OPERATOR = "operator@example.test";
    private final NotificationTemplateRepository templates = mock(NotificationTemplateRepository.class);
    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final NotificationAuditLogRepository audit = mock(NotificationAuditLogRepository.class);
    private final BrevoEmailService brevo = mock(BrevoEmailService.class);
    private final NotificationChannelStrategy emailStrategy = mock(NotificationChannelStrategy.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
    private NotificationRouterService router;

    @BeforeEach
    void setUp() {
        when(emailStrategy.getSupportedChannel()).thenReturn(ChannelType.EMAIL);
        var sender = new OperatorAlertSender(brevo,
                new OperatorAlertProperties("test-operator-alert-token-0123456789abcdef", OPERATOR, "alerts@example.test"), metrics);
        router = new NotificationRouterService(templates, preferences, mock(UserDeviceRepository.class), audit,
                List.of(emailStrategy), metrics, sender);
        var template = new com.fooddelivery.notification.domain.NotificationTemplate();
        template.setContent("{1}");
        when(templates.findByEventNameAndChannelAndIsActiveTrue("OPERATOR_ALERT", ChannelType.EMAIL)).thenReturn(Optional.of(template));
    }

    private static NotificationRequestEvent alert(String recipient, ChannelType channel, UUID user) {
        return NotificationRequestEvent.builder().eventName(NotificationTemplate.OPERATOR_ALERT).channel(channel)
                .userId(user).explicitRecipient(recipient).templateParams(List.of("<p>body {2}</p>"))
                .payload(Map.of("subject", "[FIRING:1] X")).build();
    }

    private double sent(String result) {
        return metrics.counter("operator.alerts.email", "result", result).count();
    }

    @Test
    void anOperatorAlertIsSentThroughBrevoAndAuditedWithoutAUser() throws Exception {
        when(brevo.sendHtmlEmail(any(), any(), any(), any())).thenReturn("brevo-1");
        router.routeAndDispatch(alert(OPERATOR, ChannelType.EMAIL, null));
        // The body keeps its literal "{2}": one placeholder only.
        verify(brevo).sendHtmlEmail("alerts@example.test", OPERATOR, "[FIRING:1] X", "<p>body {2}</p>");
        verify(emailStrategy, never()).dispatch(any(), any());
        verifyNoInteractions(preferences);
        var row = ArgumentCaptor.forClass(NotificationAuditLog.class);
        verify(audit).save(row.capture());
        assertNull(row.getValue().getUserId());
        assertEquals("brevo-1", row.getValue().getProviderMessageId());
        assertEquals(1.0, sent("sent"));
    }

    @Test
    void anyOtherRecipientIsRefusedBeforeAnythingIsSent() throws Exception {
        assertThrows(InvalidPayloadException.class, () -> router.routeAndDispatch(alert("victim@example.test", ChannelType.EMAIL, null)));
        verifyNoInteractions(brevo);
        verify(emailStrategy, never()).dispatch(any(), any());
    }

    @Test
    void anOperatorAlertOnAnotherChannelOrForAUserIsRefused() {
        assertThrows(InvalidPayloadException.class, () -> router.routeAndDispatch(alert(OPERATOR, ChannelType.SMS, null)));
        assertThrows(InvalidPayloadException.class, () -> router.routeAndDispatch(alert(OPERATOR, ChannelType.EMAIL, UUID.randomUUID())));
        verifyNoInteractions(brevo);
    }

    @Test
    void aBrevoFailureIsCountedAndRetried() throws Exception {
        when(brevo.sendHtmlEmail(any(), any(), any(), any())).thenThrow(new RuntimeException("Brevo Dispatch Failed. HTTP 500"));
        var failure = assertThrows(RuntimeException.class, () -> router.routeAndDispatch(alert(OPERATOR, ChannelType.EMAIL, null)));
        assertFalse(failure instanceof com.fooddelivery.notification.exception.TerminalNotificationException);
        assertEquals(1.0, sent("failed"));
        verify(audit, never()).save(any());
    }

    @Test
    void aCustomerEmailStillGoesThroughTheChannelStrategy() throws Exception {
        var user = UUID.randomUUID();
        var template = new com.fooddelivery.notification.domain.NotificationTemplate();
        when(templates.findByEventNameAndChannelAndIsActiveTrue("ORDER_DELIVERED", ChannelType.EMAIL)).thenReturn(Optional.of(template));
        when(preferences.findByUserId(user)).thenReturn(Optional.empty());
        var event = NotificationRequestEvent.builder().eventName(NotificationTemplate.ORDER_DELIVERED)
                .channel(ChannelType.EMAIL).userId(user).explicitRecipient("customer@example.test").build();
        when(emailStrategy.dispatch(event, template)).thenReturn("strategy-1");
        router.routeAndDispatch(event);
        verify(emailStrategy).dispatch(event, template);
        verifyNoInteractions(brevo);
    }
}
