---
name: notification-service-developer
description: Deep context on the Centralized Notification Service architecture, patterns, and best practices. Use this when fixing bugs or adding new features (like new providers) to the notification service.
---

# Notification Service Developer Guide

You are working on the Centralized Notification Service, built with Spring Boot 3.x and Java 21.

## Architecture
- **Event-Driven**: The service is a Kafka consumer. It listens to the `notification-events` topic.
- **Data Store**: PostgreSQL is used to store `UserDevice` (FCM tokens, phone numbers), `NotificationTemplate` (message templates), `UserPreference` (opt-outs), and `NotificationAuditLog`.
- **Cache / Rate Limiting**: Redis is used via Bucket4j to rate limit notifications globally.

## Adding a New Provider
If a user requests adding a new provider (e.g., Twilio for SMS):
1. **Implement `NotificationProviderService`**: Create a new service class that implements the interface.
2. **Update Router**: Inject the new service into `NotificationRouterService` and route requests to it based on the channel and provider preferences.
3. **Handle Callbacks**: If the provider uses async delivery status callbacks, add a new endpoint in `ProviderWebhookController` and map the callback to update the `NotificationAuditLog`.

## Rate Limiting
- Defined in `RateLimitingService`.
- Currently uses a Bucket4j configuration with Redis (Lettuce) for distributed enforcement.
- Rate limits are typically applied per `userId` + `channel`. Check for `RateLimitExceededException`.

## Kafka Resiliency
- Managed in `NotificationEventConsumer`.
- Uses Spring Kafka `@RetryableTopic` for non-blocking retries.
- Exceptions like `UserOptedOutException` or `RateLimitExceededException` are excluded from retries to prevent wasted resources.

## Local Development
- Run `docker-compose up -d` in the root directory to start Kafka, Zookeeper, PostgreSQL, and Redis.
- Use `./mvnw clean compile` to check for compilation errors.
