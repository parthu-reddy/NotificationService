package com.fooddelivery.notification.service.strategy;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.service.ExotelSmsService;
import com.fooddelivery.notification.service.TwilioSmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationStrategy implements NotificationChannelStrategy {

    private final ExotelSmsService exotelSmsService;
    private final TwilioSmsService twilioSmsService;
    private final java.util.concurrent.atomic.AtomicInteger consecutiveSmsTimeouts = new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    public ChannelType getSupportedChannel() {
        return ChannelType.SMS;
    }

    @Override
    public String dispatch(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        String content = hydrateTemplate(template.getContent(), event.getTemplateParams());
        try {
            if (consecutiveSmsTimeouts.get() >= 3) {
                return twilioSmsService.dispatchSms(event.getExplicitRecipient(), content);
            }
            String id = exotelSmsService.dispatchSms(event.getExplicitRecipient(), content, "FOODDL", template.getExternalEntityId(), template.getExternalTemplateId());
            consecutiveSmsTimeouts.set(0);
            return id;
        } catch (com.fooddelivery.notification.exception.ProviderGatewayTimeoutException e) {
            int currentFailures = consecutiveSmsTimeouts.incrementAndGet();
            log.warn("Exotel SMS Gateway Timeout. Consecutive failures: {}", currentFailures);
            if (currentFailures >= 3) {
                log.info("Failing over to Twilio SMS Provider");
                return twilioSmsService.dispatchSms(event.getExplicitRecipient(), content);
            }
            throw e;
        }
    }
}
