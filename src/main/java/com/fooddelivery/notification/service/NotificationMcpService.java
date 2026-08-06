package com.fooddelivery.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;

import com.fooddelivery.notification.controller.ProviderWebhookController;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NotificationMcpService {

    private final NotificationRouterService notificationRouterService;
    private final ProviderWebhookController webhookController;
    private final com.fooddelivery.notification.controller.DeviceController deviceController;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @org.springframework.beans.factory.annotation.Value("${platform.webhook.secret}")
    private String webhookSecret;

    public NotificationMcpService(NotificationRouterService notificationRouterService,
                                  ProviderWebhookController webhookController,
                                  com.fooddelivery.notification.controller.DeviceController deviceController) {
        this.notificationRouterService = notificationRouterService;
        this.webhookController = webhookController;
        this.deviceController = deviceController;
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


    @Tool(description = "Simulate an Exotel status callback webhook. Provide smsSid and status (e.g., delivered, failed, sent).")
    public String simulateExotelWebhook(String smsSid, String status) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("SmsSid", smsSid);
            payload.put("Status", status);
            return objectMapper.writeValueAsString(webhookController.handleExotelCallback(payload, webhookSecret).getStatusCode());
        } catch (Exception e) {
            return "Failed to simulate webhook: " + e.getMessage();
        }
    }

    private java.security.Principal createMockPrincipal(String userId) {
        return () -> userId;
    }

    @Tool(description = "Register a device FCM token. Provide userId, fcmToken, and platform.")
    public String registerDevice(String userId, String fcmToken, String platform) {
        try {
            com.fooddelivery.notification.controller.DeviceController.DeviceRegistrationRequest req = new com.fooddelivery.notification.controller.DeviceController.DeviceRegistrationRequest();
            req.setFcmToken(fcmToken);
            req.setPlatform(platform);
            return objectMapper.writeValueAsString(deviceController.registerDevice(createMockPrincipal(userId), req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Unregister a device FCM token. Provide userId and fcmToken.")
    public String unregisterDevice(String userId, String fcmToken) {
        try {
            return objectMapper.writeValueAsString(deviceController.unregisterDevice(createMockPrincipal(userId), fcmToken).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
