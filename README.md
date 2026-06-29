# Communication Integration

The Communication Integration microservice acts as the central notification dispatcher for the Food Delivery platform. It abstracts away third-party communication providers (e.g., AWS SES, Brevo, Twilio, Exotel, Gupshup, Firebase).

## Responsibilities

1. **Event Consumption**: Listens to the `notification-events` Kafka topic for standard `NotificationRequestEvent` messages (defined in CommonLibrary).
2. **Strategy Routing**: Dynamically routes the notification payload to the appropriate channel (SMS, EMAIL, PUSH, WHATSAPP, VOICE) using the Strategy Pattern.
3. **Auditing**: Logs all outgoing notifications to the `notification_db` for tracking and compliance.

## Notification Flow

```mermaid
sequenceDiagram
    participant K as Kafka (notification-events)
    participant Consumer as NotificationEventConsumer
    participant Router as NotificationRouterService
    participant Provider as External API (Twilio/SES)
    participant DB as Notification DB

    K->>Consumer: Consume NotificationRequestEvent
    Consumer->>Router: Route Request
    Router->>Provider: Send message via respective Client
    Router->>DB: Save NotificationAuditLog (Status: SENT)
```

## Setup

Requires PostgreSQL (`notification_db`) and Kafka. Run `mvn spring-boot:run`.
