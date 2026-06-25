---
name: notification-service-integration
description: Guides other services or agents on how to trigger notifications by publishing events to the Centralized Notification Service. Use this when you need to send a notification from another microservice.
---

# Notification Service Integration Guide

The Centralized Notification Service handles all outbound communication (Push, SMS, WhatsApp, Email). You DO NOT call it via REST to send messages; instead, you publish events to a Kafka topic.

## How to Send a Notification

1. **Kafka Topic**: `notification-events`
2. **Message Schema**: The payload should be serialized as JSON representing the `NotificationRequestEvent` object.

### JSON Schema for `NotificationRequestEvent`
```json
{
  "eventId": "uuid-string-for-idempotency",
  "userId": "string-user-id",
  "templateCode": "string-template-identifier",
  "channel": "PUSH | SMS | WHATSAPP | EMAIL",
  "explicitRecipient": "optional-string-override-address",
  "templateParams": {
    "key1": "value1",
    "name": "John"
  },
  "payload": {
    "action_url": "https://example.com"
  }
}
```

### Field Definitions
- **eventId**: A unique identifier for the request, used for deduplication and tracing.
- **userId**: The internal ID of the user. The service will look up their devices/contact info in the database.
- **templateCode**: The code of the `NotificationTemplate` to use (e.g., `ORDER_SHIPPED`).
- **channel**: Must be one of the supported `ChannelType` values (`PUSH`, `SMS`, `WHATSAPP`, `EMAIL`).
- **explicitRecipient**: (Optional) Provide a direct email, phone number, or FCM token to override the user's saved contact info.
- **templateParams**: (Optional) Key-value map of dynamic variables to inject into the template.
- **payload**: (Optional) Key-value map of extra data payload (mostly used for PUSH data payloads).

## Best Practices
- **Async Nature**: The notification service is purely asynchronous. You will not get a synchronous success/failure response.
- **Idempotency**: Always provide a unique `eventId`. If the same event is published twice, the notification service will drop the duplicate.
