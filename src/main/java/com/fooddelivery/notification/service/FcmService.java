package com.fooddelivery.notification.service;

import com.google.firebase.messaging.*;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
@lombok.extern.slf4j.Slf4j
public class FcmService {
    @java.lang.SuppressWarnings("all")

    private final com.fooddelivery.notification.repository.UserDeviceRepository userDeviceRepository;

    public FcmService(com.fooddelivery.notification.repository.UserDeviceRepository userDeviceRepository) {
        this.userDeviceRepository = userDeviceRepository;
    }

    /**
     * Unicast messaging for targeted user alerts.
     */
    public String sendDirectNotification(String token, String title, String body, Map<String, String> payload) throws FirebaseMessagingException {
        Notification notification = Notification.builder().setTitle(title).setBody(body).build();
        Message message =  // Data payload used for background processing or deep linking
        Message.builder().setToken(token).setNotification(notification).putAllData(payload != null ? payload : Map.of()).build();
        try {
            // Returns the message ID string in the format projects/{project_id}/messages/{message_id}
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM Unicast successful. Message ID: {}", response);
            return response;
        } catch (FirebaseMessagingException e) {
            if ("UNREGISTERED".equals(e.getErrorCode().name()) || "messaging/registration-token-not-registered".equals(e.getErrorCode().name())) {
                log.warn("FCM Token is unregistered. Cleaning up token: {}", token);
                userDeviceRepository.findByFcmToken(token).ifPresent(device -> {
                    device.setIsActive(false);
                    userDeviceRepository.save(device);
                });
            }
            throw e;
        }
    }

    /**
     * Multicast messaging for driver fleet broadcasts.
     */
    public void sendMulticastNotification(List<String> tokens, String title, String body, Map<String, String> payload) throws FirebaseMessagingException {
        // FCM limits multicast payloads to 500 tokens per batch
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(payload != null ? payload : Map.of())
                .build();
        BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
        if (response.getFailureCount() > 0) {
            log.warn("{} messages failed to deliver in multicast batch.", response.getFailureCount());
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isSuccessful()) {
                    FirebaseMessagingException e = responses.get(i).getException();
                    if (e != null && ("UNREGISTERED".equals(e.getErrorCode().name()) || "messaging/registration-token-not-registered".equals(e.getErrorCode().name()))) {
                        String deadToken = tokens.get(i);
                        log.warn("FCM Token is unregistered. Cleaning up token: {}", deadToken);
                        userDeviceRepository.findByFcmToken(deadToken).ifPresent(device -> {
                            device.setIsActive(false);
                            userDeviceRepository.save(device);
                        });
                    }
                }
            }
        }
    }
}
