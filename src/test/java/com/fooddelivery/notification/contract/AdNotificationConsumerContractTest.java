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
@AutoConfigureStubRunner(ids = "com.fooddelivery:campaign-service:+:stubs")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@EmbeddedKafka(partitions = 1, topics = {"ad-events"})
class AdNotificationConsumerContractTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    
    // EventBinder: the consumer now needs one, and a sliced context does not inherit the
// application's scan of com.fooddelivery.common. Imported directly rather than via a
// helper @Configuration in common-test -- such a class sits in an unlayered package and
// depending on ..event.. (the Service layer) fails ArchitectureEnforcementTest in every
// module. Spring builds it from the context's ObjectMapper and Validator.
@Import({AdNotificationListener.class, com.fooddelivery.common.event.EventBinder.class})
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

        // The listener takes the raw String and binds inside, the way Spring Kafka delivers it --
        // the platform uses a String deserializer, so capturing a typed argument here would assert
        // a signature production does not have. Capture the wire payload and bind it the same way
        // the consumer does.
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(notificationConsumer).consumeNotificationEvent(captor.capture(), any());
            NotificationRequestEvent event = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(captor.getValue(), NotificationRequestEvent.class);
            // The enum, not the string. `isEqualTo` takes an Object, so comparing the typed field
            // against "AD_CAMPAIGN_PAUSED" still compiles -- and fails, 15 seconds later, with
            // `expected: "AD_CAMPAIGN_PAUSED" but was: AD_CAMPAIGN_PAUSED`.
            assertThat(event.getEventName()).isEqualTo(NotificationTemplate.AD_CAMPAIGN_PAUSED);
            assertThat(event.getUserId()).isNotNull();
        });
    }
}
