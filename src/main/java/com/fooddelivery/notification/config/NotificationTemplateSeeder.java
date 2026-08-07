package com.fooddelivery.notification.config;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class NotificationTemplateSeeder implements CommandLineRunner {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationTemplateSeeder.class);
    private final NotificationTemplateRepository repository;

    @Override
    public void run(String... args) throws Exception {
        seedTemplate(com.fooddelivery.common.constants.NotificationTemplate.NEW_ORDER_DISPATCH.name(), ChannelType.PUSH, "New order {1} is available for you.");
        seedTemplate(com.fooddelivery.common.constants.NotificationTemplate.ORDER_ASSIGNED.name(), ChannelType.PUSH, "Order {1} has been assigned to you.");
        seedTemplate(com.fooddelivery.common.constants.EventType.ORDER_CREATED.name(), ChannelType.PUSH, "Your order {1} has been successfully created.");
        seedTemplate(com.fooddelivery.common.constants.EventType.ORDER_DELIVERED.name(), ChannelType.PUSH, "Your order {1} has been delivered. Enjoy!");
        seedTemplate(com.fooddelivery.common.constants.NotificationTemplate.DRIVER_ON_THE_WAY.name(), ChannelType.PUSH, "Driver is on the way for order {1}.");
        seedTemplate(com.fooddelivery.common.constants.NotificationTemplate.OTP_LOGIN.name(), ChannelType.SMS, "Your OTP is {1}. It is valid for 5 minutes.");
        seedTemplate(com.fooddelivery.common.constants.NotificationTemplate.OTP_LOGIN.name(), ChannelType.EMAIL, "Your OTP is {1}. It is valid for 5 minutes.");
        log.info("Finished seeding notification templates.");
    }

    private void seedTemplate(String eventName, ChannelType channel, String content) {
        Optional<NotificationTemplate> existing = repository.findByEventNameAndChannelAndIsActiveTrue(eventName, channel);
        if (existing.isEmpty()) {
            NotificationTemplate template = new NotificationTemplate();
            template.setEventName(eventName);
            template.setChannel(channel);
            template.setContent(content);
            template.setIsActive(true);
            repository.save(template);
            log.info("Seeded template for event {} on channel {}", eventName, channel);
        } else if (!existing.get().getContent().equals(content)) {
            NotificationTemplate template = existing.get();
            template.setContent(content);
            repository.save(template);
            log.info("Updated template content for event {} on channel {}", eventName, channel);
        }
    }

    @java.lang.SuppressWarnings("all")
    public NotificationTemplateSeeder(final NotificationTemplateRepository repository) {
        this.repository = repository;
    }
}
