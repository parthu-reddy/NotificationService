package com.fooddelivery.notification.service.strategy;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.service.AwsSesEmailService;
import com.fooddelivery.notification.service.BrevoEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailNotificationStrategy implements NotificationChannelStrategy {

    private final AwsSesEmailService awsSesEmailService;
    private final BrevoEmailService brevoEmailService;
    private final String activeEmailProvider;
    private final java.util.concurrent.atomic.AtomicInteger consecutiveEmailTimeouts = new java.util.concurrent.atomic.AtomicInteger(0);

    public EmailNotificationStrategy(AwsSesEmailService awsSesEmailService,
                                     BrevoEmailService brevoEmailService,
                                     @Value("${platform.providers.email.active:aws}") String activeEmailProvider) {
        this.awsSesEmailService = awsSesEmailService;
        this.brevoEmailService = brevoEmailService;
        this.activeEmailProvider = activeEmailProvider;
    }

    @Override
    public ChannelType getSupportedChannel() {
        return ChannelType.EMAIL;
    }

    @Override
    public String dispatch(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        String content = hydrateTemplate(template.getContent(), event.getTemplateParams());
        boolean useBrevo = "brevo".equalsIgnoreCase(activeEmailProvider);
        if (consecutiveEmailTimeouts.get() >= 3) {
            useBrevo = !useBrevo; // Failover to the alternative
        }
        
        try {
            String id;
            if (useBrevo) {
                id = brevoEmailService.sendHtmlEmail("noreply@fooddelivery.com", event.getExplicitRecipient(), "Food Delivery Update", content);
            } else {
                id = awsSesEmailService.sendHtmlEmail("noreply@fooddelivery.com", event.getExplicitRecipient(), "Food Delivery Update", content);
            }
            consecutiveEmailTimeouts.set(0);
            return id;
        } catch (com.fooddelivery.notification.exception.ProviderGatewayTimeoutException e) {
            int currentFailures = consecutiveEmailTimeouts.incrementAndGet();
            log.warn("Email Gateway Timeout. Consecutive failures: {}", currentFailures);
            if (currentFailures >= 3) {
                log.info("Failing over Email Provider");
                if (useBrevo) {
                    return awsSesEmailService.sendHtmlEmail("noreply@fooddelivery.com", event.getExplicitRecipient(), "Food Delivery Update", content);
                } else {
                    return brevoEmailService.sendHtmlEmail("noreply@fooddelivery.com", event.getExplicitRecipient(), "Food Delivery Update", content);
                }
            }
            throw e;
        }
    }
}
