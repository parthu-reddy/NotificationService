package com.fooddelivery.notification.config;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Dev validates/routes/audits notifications without sending to external recipients. */
@Configuration(proxyBeanMethods = false)
@Profile("dev & !prod")
public class DevNotificationChannelConfiguration {
    @Bean NotificationChannelStrategy devSms(MeterRegistry metrics) { return mock(ChannelType.SMS, metrics); }
    @Bean NotificationChannelStrategy devEmail(MeterRegistry metrics) { return mock(ChannelType.EMAIL, metrics); }
    @Bean NotificationChannelStrategy devWhatsApp(MeterRegistry metrics) { return mock(ChannelType.WHATSAPP, metrics); }
    @Bean NotificationChannelStrategy devPush(MeterRegistry metrics) { return mock(ChannelType.PUSH, metrics); }

    private static NotificationChannelStrategy mock(ChannelType channel, MeterRegistry metrics) {
        return new NotificationChannelStrategy() {
            @Override public ChannelType getSupportedChannel() { return channel; }
            @Override public String dispatch(NotificationRequestEvent event, NotificationTemplate template) {
                metrics.counter("notifications.dev.mock", "channel", channel.name()).increment();
                return "dev-mock:" + channel.name() + ":" + event.getEventId();
            }
        };
    }
}
