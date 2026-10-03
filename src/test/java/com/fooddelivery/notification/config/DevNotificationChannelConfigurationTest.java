package com.fooddelivery.notification.config;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class DevNotificationChannelConfigurationTest {
    @Test void devHasExactlyOneMockPerChannelAndNeverInitializesFirebase() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            context.registerBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new);
            context.register(DevNotificationChannelConfiguration.class, FirebaseConfig.class);
            context.refresh();
            var strategies = context.getBeansOfType(NotificationChannelStrategy.class);
            assertEquals(4, strategies.size());
            assertEquals(java.util.EnumSet.allOf(ChannelType.class), strategies.values().stream()
                    .map(NotificationChannelStrategy::getSupportedChannel).collect(java.util.stream.Collectors.toSet()));
            assertTrue(context.getBeansOfType(FirebaseConfig.class).isEmpty());
            for (var strategy : strategies.values()) {
                var event = NotificationRequestEvent.builder().eventId("profile-check").build();
                assertEquals("dev-mock:" + strategy.getSupportedChannel() + ":profile-check", strategy.dispatch(event, null));
            }
        }
    }
    @Test void productionCannotEnableMocksEvenIfDevProfileIsAlsoPresent() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev", "prod");
            context.register(DevNotificationChannelConfiguration.class);
            context.refresh();
            assertTrue(context.getBeansOfType(NotificationChannelStrategy.class).isEmpty());
        }
    }
    @Test void missingFirebaseCredentialsFailStartupOutsideDev() throws Exception {
        try (var credentials = org.mockito.Mockito.mockStatic(com.google.auth.oauth2.GoogleCredentials.class);
             var apps = org.mockito.Mockito.mockStatic(com.google.firebase.FirebaseApp.class)) {
            apps.when(com.google.firebase.FirebaseApp::getApps).thenReturn(java.util.List.of());
            credentials.when(com.google.auth.oauth2.GoogleCredentials::getApplicationDefault)
                    .thenThrow(new java.io.IOException("credentials unavailable"));
            var failure = assertThrows(IllegalStateException.class, () -> new FirebaseConfig().initialize());
            assertTrue(failure.getMessage().contains("required outside Dev"));
        }
    }
}
