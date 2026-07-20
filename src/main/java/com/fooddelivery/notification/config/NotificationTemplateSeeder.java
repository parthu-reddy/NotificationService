package com.fooddelivery.notification.config;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationTemplateSeeder implements CommandLineRunner {

    private final NotificationTemplateRepository repository;

    @Override
    public void run(String... args) throws Exception {
        seedTemplate(com.fooddelivery.common.constants.EventType.NOTIFY_NEW_ORDER_DISPATCH, ChannelType.PUSH, "New order #{orderId} is available for you.");
        seedTemplate(com.fooddelivery.common.constants.EventType.NOTIFY_ORDER_ASSIGNED, ChannelType.PUSH, "Order #{orderId} has been assigned to you.");
        seedTemplate(com.fooddelivery.common.constants.EventType.ORDER_CREATED, ChannelType.PUSH, "Your order #{orderId} has been successfully created.");
        seedTemplate(com.fooddelivery.common.constants.EventType.ORDER_DELIVERED, ChannelType.PUSH, "Your order #{orderId} has been delivered. Enjoy!");
        seedTemplate(com.fooddelivery.common.constants.EventType.NOTIFY_DRIVER_ON_THE_WAY, ChannelType.PUSH, "Driver is on the way for order #{orderId}.");
        seedTemplate(com.fooddelivery.common.constants.EventType.NOTIFY_OTP_LOGIN, ChannelType.SMS, "Your OTP is #{otp}. It is valid for 5 minutes.");
        seedTemplate(com.fooddelivery.common.constants.EventType.NOTIFY_OTP_LOGIN, ChannelType.EMAIL, "Your OTP is #{otp}. It is valid for 5 minutes.");
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
        }
    }
}
