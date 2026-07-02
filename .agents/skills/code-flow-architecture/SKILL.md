---
name: code-flow-architecture
description: Explains the architecture, event-driven workflow, provider strategy pattern, and the code flow in the Communication Integration (Notification Service). Use this to understand how notifications are processed internally.
---

# Code Flow & Architecture Guide (Communication Integration)

This document describes the architectural patterns used in the Communication Integration service, which is the centralized notification dispatcher for the Food Delivery platform.

## Architecture (Microservice)

This application runs on Java 21 and Spring Boot 3.3.0 on **port 8085** (default).

The codebase is organized under `com.fooddelivery.notification`:
- **`controller`**: REST endpoints for testing (`NotificationTestController`) and provider delivery status webhooks (`ProviderWebhookController`).
- **`service`**: Core routing (`NotificationRouterService`), rate limiting (`RateLimitingService`), audit logging (`AuditLogService`), and Kafka consumer (`NotificationEventConsumer`).
- **`service/strategy`**: Channel strategy pattern — `PushNotificationStrategy`, `SmsNotificationStrategy`, `EmailNotificationStrategy`, `WhatsAppNotificationStrategy`.
- **`domain`**: JPA entities — `NotificationAuditLog`, `NotificationTemplate`, `UserDevice`, `UserPreference`.

## Supported Notification Providers

| Channel | Provider(s) | Implementation Class |
|---|---|---|
| **PUSH** | Firebase Cloud Messaging (FCM) | `FcmService` |
| **SMS** | Exotel, Twilio | `ExotelSmsService`, `TwilioSmsService` |
| **EMAIL** | AWS SES, Brevo | `AwsSesEmailService`, `BrevoEmailService` |
| **WHATSAPP** | Gupshup | `GupshupWhatsAppService` |

## Key Engineering Standards
- **Database**: Schema initialized using Flyway (`notification_db`).
- **Kafka Consumer**: `NotificationEventConsumer` uses `@RetryableTopic` for non-blocking retries. Terminal exceptions (`UserOptedOutException`, `RateLimitExceededException`) are excluded from retry to prevent wasted resources.
- **Rate Limiting**: Bucket4j + Redis (Lettuce) distributed rate limiting per `userId` + `channel`.

## Event Processing Flow

1. **Ingestion**: `NotificationEventConsumer` consumes `NotificationRequestEvent` from `platform.notifications.dispatch` topic.
2. **Opt-Out Check**: `UserPreferenceRepository` checks if user opted out of the requested channel.
3. **Rate Limiting**: `RateLimitingService` checks Redis bucket for `userId` + `channel`.
4. **Template Resolution**: `NotificationTemplateRepository` resolves `templateCode` and replaces `{{placeholders}}` with `templateParams`.
5. **Recipient Resolution**: Uses `explicitRecipient` if provided, otherwise queries `UserDeviceRepository` for the user's FCM token, phone, or email.
6. **Channel Strategy Dispatch**: `NotificationRouterService` selects the appropriate `NotificationChannelStrategy` and dispatches.
7. **Audit Logging**: Creates a `NotificationAuditLog` record with initial status (`PENDING` or `SUCCESS`).
8. **Async Delivery Callbacks**: Provider webhooks (e.g., `POST /webhooks/providers/exotel/status`) update the audit log to `DELIVERED` or `FAILED`.

## Kafka Integration

### Consumed Events
| Topic | Event | Action |
|---|---|---|
| `platform.notifications.dispatch` | `NotificationRequestEvent` | Routes to the appropriate channel provider |

### DLQ
- Failed messages (after retries) go to `notifications-dlq`.

## Database
- **PostgreSQL** database: `notification_db`
- **Flyway migrations**: `src/main/resources/db/migration/`
- Key tables: `user_devices`, `notification_templates`, `user_preferences`, `notification_audit_log`

For visual diagrams, see `Deployment/flow_diagram.md` and `README.md` in the repository root.
