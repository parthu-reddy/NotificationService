package com.fooddelivery.notification.service.strategy;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.service.ExotelSmsService;
import com.fooddelivery.notification.service.TwilioSmsService;
import org.springframework.stereotype.Component;

@Component
@lombok.extern.slf4j.Slf4j
public class SmsNotificationStrategy implements NotificationChannelStrategy {
    @java.lang.SuppressWarnings("all")

    private final ExotelSmsService exotelSmsService;
    private final TwilioSmsService twilioSmsService;
    private final java.util.concurrent.atomic.AtomicInteger consecutiveSmsTimeouts = new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile long lastFailoverTimestamp = 0;
    private static final long FAILOVER_COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes

    @Override
    public ChannelType getSupportedChannel() {
        return ChannelType.SMS;
    }

    @Override
    public String dispatch(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        String content = hydrateTemplate(template.getContent(), event.getTemplateParams());
        try {
            if (consecutiveSmsTimeouts.get() >= 3) {
                // Check if cooldown has passed before probing primary again
                if (System.currentTimeMillis() - lastFailoverTimestamp < FAILOVER_COOLDOWN_MS) {
                    return twilioSmsService.dispatchSms(event.getExplicitRecipient(), content);
                }
                // Cooldown expired — probe Exotel again
                log.info("Failover cooldown expired. Probing Exotel primary provider.");
                consecutiveSmsTimeouts.set(0);
            }
            String id = exotelSmsService.dispatchSms(event.getExplicitRecipient(), content, "FOODDL", template.getExternalEntityId(), template.getExternalTemplateId());
            consecutiveSmsTimeouts.set(0);
            return id;
        } catch (com.fooddelivery.notification.exception.ProviderGatewayTimeoutException e) {
            int currentFailures = consecutiveSmsTimeouts.incrementAndGet();
            log.warn("Exotel SMS Gateway Timeout. Consecutive failures: {}", currentFailures);
            if (currentFailures >= 3) {
                log.info("Failing over to Twilio SMS Provider");
                lastFailoverTimestamp = System.currentTimeMillis();
                return twilioSmsService.dispatchSms(event.getExplicitRecipient(), content);
            }
            throw e;
        }
    }

    @java.lang.SuppressWarnings("all")
    public SmsNotificationStrategy(final ExotelSmsService exotelSmsService, final TwilioSmsService twilioSmsService) {
        this.exotelSmsService = exotelSmsService;
        this.twilioSmsService = twilioSmsService;
    }
}
