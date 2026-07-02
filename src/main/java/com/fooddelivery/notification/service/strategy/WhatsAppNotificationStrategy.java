package com.fooddelivery.notification.service.strategy;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.service.GupshupWhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhatsAppNotificationStrategy implements NotificationChannelStrategy {

    private final GupshupWhatsAppService gupshupWhatsAppService;

    @Override
    public ChannelType getSupportedChannel() {
        return ChannelType.WHATSAPP;
    }

    @Override
    public String dispatch(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        return gupshupWhatsAppService.dispatchWhatsAppTemplate(event.getExplicitRecipient(), template.getExternalTemplateId(), event.getTemplateParams());
    }
}
