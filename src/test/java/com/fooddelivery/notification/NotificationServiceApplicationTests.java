package com.fooddelivery.notification;

import com.fooddelivery.common.test.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * The properties are declared here rather than inherited: a @SpringBootTest on the subclass takes
 * precedence over the one on BaseIntegrationTest, so nothing declared there reaches this class.
 *
 * allow-bean-definition-overriding covers the collision every service in this repo has --
 * the application class and CommonLibrary's OutboxConfiguration both declare @EnableJpaRepositories
 * over com.fooddelivery.common.outbox.repository.
 *
 * platform.webhook.secret has no default anywhere, which is right for a real secret: production
 * supplies it. Booting the context needs ProviderWebhookController, so the test supplies a dummy.
 */
@SpringBootTest(properties = {
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
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false"
})
class NotificationServiceApplicationTests extends BaseIntegrationTest {

	@Test
	void contextLoads() {
	}

}
