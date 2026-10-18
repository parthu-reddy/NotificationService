package com.fooddelivery.notification;

import com.fooddelivery.common.test.BaseIntegrationTest;

/**
 * An unauthenticated GET of /actuator/prometheus on a real port, through every servlet filter and this service's own
 * security chain, answers 200 with metric samples: what Prometheus or an operator on the private network sees
 * (PrometheusRegistry_2026-10-17). It fails with 404 without the registry and with 401/403 when the chain refuses.
 *
 * <p>Boot setup copied from NotificationServiceApplicationTests.
 * No servlet filter is mocked (a mock ends the request with an empty 200) and no security auto-configuration is
 * excluded.
 */
@org.springframework.boot.test.context.SpringBootTest(classes = com.fooddelivery.notification.NotificationServiceApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.redis.enabled=false",
        "brevo.api.key=dummy",
        "exotel.account.sid=dummy-sid",
        "exotel.api.key=dummy",
        "exotel.api.token=dummy-token",
        "gupshup.api.key=dummy",
        "gupshup.source.number=910000000000",
        "platform.webhook.base-url=http://localhost",
        "platform.webhook.secret=dummy-webhook-secret",
        "platform.operator-alerts.webhook-token=test-operator-alert-token-0123456789abcdef",
        "platform.operator-alerts.recipient=operator@example.test",
        "platform.operator-alerts.sender=alerts@example.test",
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.main.web-application-type=servlet"
})
@org.springframework.test.context.ActiveProfiles("dev")
// Boot's test support swaps every metrics exporter for a SimpleMeterRegistry; keep the real ones, as Dev runs them.
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability(tracing = false)
class PrometheusScrapeTest extends BaseIntegrationTest {

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;

    @org.junit.jupiter.api.Test
    void anUnauthenticatedScrapeIsServed() throws Exception {
        com.fooddelivery.common.test.PrometheusScrape.assertServedAnonymously(port);
    }
}
