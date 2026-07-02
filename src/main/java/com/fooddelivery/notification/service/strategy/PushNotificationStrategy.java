package com.fooddelivery.notification.service.strategy;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.domain.UserDevice;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.service.FcmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PushNotificationStrategy implements NotificationChannelStrategy {

    private final UserDeviceRepository userDeviceRepository;
    private final FcmService fcmService;

    @Override
    public ChannelType getSupportedChannel() {
        return ChannelType.PUSH;
    }

    @Override
    public String dispatch(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        List<UserDevice> devices = userDeviceRepository.findByUserIdAndIsActiveTrue(event.getUserId());
        if (devices.isEmpty()) {
            throw new com.fooddelivery.notification.exception.RecipientUnreachableException("No active devices found for user.");
        }
        
        String title = "Notification";
        String body = hydrateTemplate(template.getContent(), event.getTemplateParams());
        
        if (devices.size() == 1) {
            return fcmService.sendDirectNotification(devices.get(0).getFcmToken(), title, body, event.getPayload());
        } else {
            fcmService.sendMulticastNotification(devices.stream().map(UserDevice::getFcmToken).collect(Collectors.toList()), title, body);
            return "multicast-" + event.getEventId();
        }
    }
}
