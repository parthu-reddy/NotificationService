---
name: notification-service-api
description: API reference and integration guide for the NotificationService. Use this to understand how to trigger notifications from other services via Kafka.
---

# NotificationService API Reference

The NotificationService is primarily event-driven. Downstream services do not call it via REST; instead, they publish events to Kafka.

## Kafka Topics

### `notification-events`
- **Producer**: Any service (e.g., CustomerApplication, IdentityService).
- **Payload (`NotificationEvent` DTO)**:
  ```json
  {
    "userId": "uuid",
    "channel": "EMAIL|SMS|PUSH",
    "templateId": "ORDER_CONFIRMATION",
    "contextData": {
      "orderId": "12345"
    }
  }
  ```

## Internal API
If a direct internal trigger is required (e.g., for testing or manual triggers), it can be called internally (protected by `PreAuthFilter`).
- `POST /api/v1/internal/notifications/send`
