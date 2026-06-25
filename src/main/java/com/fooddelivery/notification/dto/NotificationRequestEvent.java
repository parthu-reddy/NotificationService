package com.fooddelivery.notification.dto;

import com.fooddelivery.notification.domain.ChannelType;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class NotificationRequestEvent {
    private String eventId = UUID.randomUUID().toString();
    private UUID userId;
    private ChannelType channel;
    private String eventName;
    private String explicitRecipient; // Use for SMS/Email if preferred over looking up
    private List<String> templateParams;
    private Map<String, String> payload;
}
