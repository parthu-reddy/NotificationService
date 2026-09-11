package com.fooddelivery.notification.service;

import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.UserPreference;
import com.fooddelivery.notification.exception.InvalidTemplateException;
import com.fooddelivery.notification.exception.TerminalNotificationException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.repository.UserPreferenceRepository;
import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.RetryableTopic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A missing template is a configuration fault: it fails once, loudly, and is counted.
 *
 * <p>Retrying cannot help — no amount of backoff seeds a template — and the only previous trace was
 * a row in `notification_audit_logs`, which is how nine emitted codes went without one for 73 days.
 */
class MissingTemplateIsTerminalTest {

    private NotificationTemplateRepository templateRepository;
    private SimpleMeterRegistry meterRegistry;
    private NotificationRouterService router;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        templateRepository = mock(NotificationTemplateRepository.class);
        UserPreferenceRepository prefs = mock(UserPreferenceRepository.class);
        meterRegistry = new SimpleMeterRegistry();

        UserPreference allEnabled = new UserPreference();
        allEnabled.setPushEnabled(true);
        allEnabled.setSmsEnabled(true);
        allEnabled.setEmailEnabled(true);
        when(prefs.findByUserId(any())).thenReturn(Optional.of(allEnabled));

        NotificationChannelStrategy push = mock(NotificationChannelStrategy.class);
        when(push.getSupportedChannel()).thenReturn(ChannelType.PUSH);

        router = new NotificationRouterService(templateRepository, prefs,
                mock(UserDeviceRepository.class), mock(NotificationAuditLogRepository.class),
                List.of(push), meterRegistry);
    }

    private NotificationRequestEvent event() {
        NotificationRequestEvent e = new NotificationRequestEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setUserId(userId);
        e.setEventName(NotificationTemplate.DELAY_APPROVAL_REQUESTED);
        e.setChannel(ChannelType.PUSH);
        e.setTemplateParams(List.of(UUID.randomUUID().toString()));
        return e;
    }

    @Test
    void aMissingTemplateThrowsATerminalException() {
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        InvalidTemplateException e = assertThrows(InvalidTemplateException.class,
                () -> router.routeAndDispatch(event()));
        assertInstanceOf(TerminalNotificationException.class, e,
                "retrying cannot seed a template, so it must not be retried");
    }

    @Test
    void theConsumerExcludesTerminalFailuresFromRetry() throws Exception {
        // The exception being terminal only matters if the listener honours it. Assert the wiring,
        // not the intent.
        Method listener = NotificationEventConsumer.class.getMethod("consumeNotificationEvent",
                NotificationRequestEvent.class, java.util.Map.class);
        RetryableTopic retryable = listener.getAnnotation(RetryableTopic.class);
        assertNotNull(retryable, "the listener must carry @RetryableTopic");
        assertTrue(List.of(retryable.exclude()).contains(TerminalNotificationException.class),
                "TerminalNotificationException must be excluded from retry: "
                        + List.of(retryable.exclude()));
    }

    @Test
    void aMissingTemplateIsCounted() {
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTemplateException.class, () -> router.routeAndDispatch(event()));

        double count = meterRegistry.get("notification_template_missing_total")
                .tag("event", NotificationTemplate.DELAY_APPROVAL_REQUESTED.name())
                .tag("channel", ChannelType.PUSH.name())
                .counter().count();
        assertEquals(1.0, count,
                "a silent terminal failure in an audit table is how this went unnoticed; it has to "
                        + "be a metric somebody can alert on");
    }

    @Test
    void theCounterNamesTheCodeSoItCanBeFound() {
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        NotificationRequestEvent first = event();
        NotificationRequestEvent second = event();
        second.setEventName(NotificationTemplate.ORDER_PAID);

        assertThrows(InvalidTemplateException.class, () -> router.routeAndDispatch(first));
        assertThrows(InvalidTemplateException.class, () -> router.routeAndDispatch(second));

        assertEquals(1.0, meterRegistry.get("notification_template_missing_total")
                .tag("event", NotificationTemplate.DELAY_APPROVAL_REQUESTED.name()).counter().count());
        assertEquals(1.0, meterRegistry.get("notification_template_missing_total")
                .tag("event", NotificationTemplate.ORDER_PAID.name()).counter().count());
    }
}
