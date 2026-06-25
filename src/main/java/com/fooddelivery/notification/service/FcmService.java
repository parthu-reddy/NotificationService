package com.fooddelivery.notification.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FcmService {

    /**
     * Unicast messaging for targeted user alerts.
     */
    public String sendDirectNotification(String token, String title, String body, Map<String, String> payload) throws FirebaseMessagingException {
        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(token)
                .setNotification(notification)
                .putAllData(payload != null ? payload : Map.of()) // Data payload used for background processing or deep linking
                .build();

        // Returns the message ID string in the format projects/{project_id}/messages/{message_id}
        String response = FirebaseMessaging.getInstance().send(message);
        log.info("FCM Unicast successful. Message ID: {}", response);
        return response;
    }

    /**
     * Multicast messaging for driver fleet broadcasts.
     */
    public void sendMulticastNotification(List<String> tokens, String title, String body) throws FirebaseMessagingException {
        // FCM limits multicast payloads to 500 tokens per batch
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
        
        if (response.getFailureCount() > 0) {
            log.warn("{} messages failed to deliver in multicast batch.", response.getFailureCount());
            // Iterate through responses and remove inactive/unregistered tokens from the database
        }
    }
}
