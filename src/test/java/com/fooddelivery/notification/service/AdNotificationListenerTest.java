package com.fooddelivery.notification.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.event.EventBinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A missing eventType header used to return with nothing logged, so a campaign-paused notice with a
 * lost header vanished; validate_ad_platform.py only began scanning this service's listeners on
 * 2026-09-26 and found it.
 */
class AdNotificationListenerTest {

    private final NotificationEventConsumer notifications = mock(NotificationEventConsumer.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdNotificationListener listener = new AdNotificationListener(notifications, objectMapper,
            new EventBinder(objectMapper, jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()));
    private final Logger logger = (Logger) LoggerFactory.getLogger(AdNotificationListener.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final UUID advertiserId = UUID.randomUUID();

    @BeforeEach
    void attachLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogs() {
        logger.detachAppender(logs);
    }

    private String paused() {
        return "{\"campaignId\":\"" + UUID.randomUUID() + "\",\"advertiserId\":\"" + advertiserId + "\",\"status\":\"PAUSED\"}";
    }

    @Test
    void anEventWithoutItsTypeHeader_isLoggedWithItsBody_andNotifiesNobody() {
        String message = paused();

        listener.consumeAdEvent(message, Map.of());

        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).singleElement()
                .satisfies(e -> assertThat(e.getFormattedMessage())
                        .contains("Missing eventType header on ad-events").contains(advertiserId.toString()));
        verifyNoInteractions(notifications);
    }

    @Test
    void aPausedCampaign_notifiesItsAdvertiser() {
        listener.consumeAdEvent(paused(), Map.of("eventType", "AD_CAMPAIGN_PAUSED".getBytes(StandardCharsets.UTF_8)));

        verify(notifications).consumeNotificationEvent(contains(advertiserId.toString()), anyMap());
    }
}
