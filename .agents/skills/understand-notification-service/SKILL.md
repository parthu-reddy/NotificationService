---
name: understand-notification-service
description: Architectural overview and troubleshooting guide for the NotificationService. Use this when fixing bugs or adding new notification providers.
---

# Understand NotificationService

The NotificationService is built using the Strategy Pattern to allow seamless addition of new notification providers.

## Architecture

- **Strategy Pattern**: The `NotificationProvider` interface is implemented by classes like `TwilioProvider` and `SendGridProvider`. The `NotificationFactory` resolves the correct implementation at runtime based on the `channel` specified in the event.
- **Resiliency**: Kafka consumer retries and Dead Letter Queues (DLQ) are configured for failed message deliveries (e.g., if Twilio API is down).
- **Security**: As a standard, all incoming internal REST calls are authenticated via `PreAuthFilter`.

## Troubleshooting

- **Messages Not Sending**: Check the Kafka consumer offsets to ensure messages are being picked up.
- **Provider API Errors**: Inspect the application logs for HTTP 4xx or 5xx from SendGrid/Twilio.
- **Adding a New Provider**: To add a new provider (e.g., Firebase Push), implement the `NotificationProvider` interface and register it with the factory. No changes are needed in the consuming services.
