package com.fooddelivery.notification.controller;

import com.fooddelivery.common.event.NotificationRequestEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/test/notifications")
public class NotificationTestController {

    private final KafkaTemplate<String, NotificationRequestEvent> kafkaTemplate;

    public NotificationTestController(KafkaTemplate<String, NotificationRequestEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping
    public ResponseEntity<String> sendTestNotification(@RequestBody NotificationRequestEvent event) {
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        
        try {
            kafkaTemplate.send(com.fooddelivery.common.constants.KafkaConstants.TOPIC_NOTIFICATIONS_DISPATCH, event.getUserId().toString(), event)
                .get(3, java.util.concurrent.TimeUnit.SECONDS);
            return ResponseEntity.ok("Event published successfully to platform.notifications.dispatch with ID: " + event.getEventId());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to publish event: " + e.getMessage());
        }
    }
}
