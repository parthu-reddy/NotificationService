package com.fooddelivery.notification.contract;

import com.fooddelivery.common.contract.KafkaStubMessageSender;

import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.service.AdNotificationListener;
import com.fooddelivery.notification.service.NotificationEventConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.StubTrigger;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Consumes CampaignService's real ad_events_paused stub and asserts an advertiser notification is
 * dispatched.
 *
 * Before the EventPayloadUtils fix this listener returned at {@code !payloadNode.has("eventType")},
 * because ad-events carries a flat Campaign with the type in a Kafka header -- so advertisers never
 * received budget-alert or campaign-paused notifications.
 */
@SpringBootTest(classes = AdNotificationConsumerContractTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@ActiveProfiles("contract-test")
@AutoConfigureStubRunner(ids = "com.fooddelivery:campaign-service:+:stubs",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@EmbeddedKafka(partitions = 1, topics = {"ad-events"})
class AdNotificationConsumerContractTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    
    @Import(AdNotificationListener.class)
    static class TestConfig {
        @Bean
        public MessageVerifierSender<Message<?>> kafkaStubMessageSender(KafkaTemplate<String, String> t) {
            return new KafkaStubMessageSender(t);
        }
    }

    @MockBean
    private NotificationEventConsumer notificationConsumer;

    @Autowired
    private StubTrigger stubTrigger;

    @Test
    void dispatchesAnAdvertiserNotificationOnCampaignPaused() {
        stubTrigger.trigger("ad_events_paused");

        ArgumentCaptor<NotificationRequestEvent> captor =
                ArgumentCaptor.forClass(NotificationRequestEvent.class);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(notificationConsumer).consumeNotificationEvent(captor.capture(), any());
            // The enum, not the string. `isEqualTo` takes an Object, so comparing the typed field
            // against "AD_CAMPAIGN_PAUSED" still compiles -- and fails, 15 seconds later, with
            // `expected: "AD_CAMPAIGN_PAUSED" but was: AD_CAMPAIGN_PAUSED`.
            assertThat(captor.getValue().getEventName())
                    .isEqualTo(NotificationTemplate.AD_CAMPAIGN_PAUSED);
            assertThat(captor.getValue().getUserId()).isNotNull();
        });
    }
}
