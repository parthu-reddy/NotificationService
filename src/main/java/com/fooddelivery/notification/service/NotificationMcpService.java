package com.fooddelivery.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.controller.NotificationTestController;
import com.fooddelivery.notification.controller.ProviderWebhookController;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Service
public class NotificationMcpService {

    private final NotificationRouterService notificationRouterService;
    private final NotificationTestController testController;
    private final ProviderWebhookController webhookController;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationMcpService(NotificationRouterService notificationRouterService,
                                  NotificationTestController testController,
                                  ProviderWebhookController webhookController) {
        this.notificationRouterService = notificationRouterService;
        this.testController = testController;
        this.webhookController = webhookController;
    }

    @Tool(description = "Route and dispatch a notification request to a specific user via a specified channel (e.g. SMS, EMAIL, PUSH, WHATSAPP)")
    public String dispatchNotification(String userId, String eventName, String channelType, String explicitRecipient) {
        try {
            NotificationRequestEvent event = new NotificationRequestEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setUserId(UUID.fromString(userId));
            event.setEventName(eventName);
            event.setChannel(ChannelType.valueOf(channelType.toUpperCase()));
            event.setExplicitRecipient(explicitRecipient);
            
            notificationRouterService.routeAndDispatch(event);
            return "Notification dispatched successfully.";
        } catch (Exception e) {
            return "Failed to dispatch notification: " + e.getMessage();
        }
    }

    @Tool(description = "Send a test notification event directly to Kafka. Provide JSON string of NotificationRequestEvent.")
    public String sendTestNotification(String notificationRequestEventJson) {
        try {
            NotificationRequestEvent event = objectMapper.readValue(notificationRequestEventJson, NotificationRequestEvent.class);
            return objectMapper.writeValueAsString(testController.sendTestNotification(event).getBody());
        } catch (Exception e) {
            return "Failed to send test notification: " + e.getMessage();
        }
    }

    @Tool(description = "Simulate an Exotel status callback webhook. Provide smsSid and status (e.g., delivered, failed, sent).")
    public String simulateExotelWebhook(String smsSid, String status) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("SmsSid", smsSid);
            payload.put("Status", status);
            return objectMapper.writeValueAsString(webhookController.handleExotelCallback(payload).getStatusCode());
        } catch (Exception e) {
            return "Failed to simulate webhook: " + e.getMessage();
        }
    }
}
