package com.fooddelivery.notification.service.strategy;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.domain.UserDevice;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.service.FcmService;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
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
        String eventName = event.getEventName() != null ? event.getEventName().name() : "NOTIFICATION";
        String title = java.util.Arrays.stream(eventName.toLowerCase().split("_")).map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1)).collect(Collectors.joining(" "));
        String body = hydrateTemplate(template.getContent(), event.getTemplateParams());
        if (devices.size() == 1) {
            return fcmService.sendDirectNotification(devices.get(0).getFcmToken(), title, body, event.getPayload());
        } else {
            fcmService.sendMulticastNotification(devices.stream().map(UserDevice::getFcmToken).collect(Collectors.toList()), title, body);
            return "multicast-" + event.getEventId();
        }
    }

    @java.lang.SuppressWarnings("all")
    public PushNotificationStrategy(final UserDeviceRepository userDeviceRepository, final FcmService fcmService) {
        this.userDeviceRepository = userDeviceRepository;
        this.fcmService = fcmService;
    }
}
