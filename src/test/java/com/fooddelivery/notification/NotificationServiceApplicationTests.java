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
@org.springframework.test.context.ActiveProfiles("dev")
class NotificationServiceApplicationTests extends BaseIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext context;

    @org.springframework.beans.factory.annotation.Autowired
    private com.fooddelivery.notification.repository.NotificationAuditLogRepository auditLogs;

    @Test
    void aPreaccountNotificationAuditPersistsWithoutAnInventedUser() {
        var row = new com.fooddelivery.notification.domain.NotificationAuditLog();
        row.setEventId(java.util.UUID.randomUUID().toString());
        row.setChannel(com.fooddelivery.common.enums.ChannelType.SMS);
        row.setRecipientAddress("8999123456");
        var saved = auditLogs.saveAndFlush(row);
        org.junit.jupiter.api.Assertions.assertNull(auditLogs.findById(saved.getId()).orElseThrow().getUserId());
    }

	@Test
	void contextLoads() {

        org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("firebaseConfig"));
        var channels = context.getBeansOfType(com.fooddelivery.notification.service.strategy.NotificationChannelStrategy.class);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("devSms", "devEmail", "devWhatsApp", "devPush"), channels.keySet());
	}

}
