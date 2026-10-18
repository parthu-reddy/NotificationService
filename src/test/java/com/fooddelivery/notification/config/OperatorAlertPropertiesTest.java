package com.fooddelivery.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/** The three operator-alert settings are required in every profile: a missing or weak one stops startup. */
class OperatorAlertPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(OperatorAlertConfiguration.class);

    private static final String TOKEN = "platform.operator-alerts.webhook-token=test-operator-alert-token-0123456789abcdef";
    private static final String TO = "platform.operator-alerts.recipient=operator@example.test";
    private static final String FROM = "platform.operator-alerts.sender=alerts@example.test";

    @Test
    void completeSettingsBind() {
        runner.withPropertyValues(TOKEN, TO, FROM).withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals("operator@example.test", context.getBean(OperatorAlertProperties.class).recipient());
        });
    }

    @Test
    void aMissingRecipientStopsStartupEvenOnDev() {
        runner.withPropertyValues(TOKEN, FROM, "spring.profiles.active=dev")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void aShortTokenStopsStartup() {
        runner.withPropertyValues("platform.operator-alerts.webhook-token=short", TO, FROM)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
