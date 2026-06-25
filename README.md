# Centralized Notification Service

A highly-available, multi-channel notification microservice built with **Spring Boot 3** and **Java 21**. It integrates with an external event bus (Kafka) to reliably deliver notifications across different providers, applying fault-tolerant patterns, rate-limiting considerations, and dead-letter queueing (DLT).

## Features

- **Multi-Channel Dispatching**: Supports routing notifications via EMAIL, SMS, WHATSAPP, and PUSH (Firebase).
- **Pluggable Email Providers**: Switch between AWS SES and Brevo using application configuration.
- **Resiliency & Retries**: Utilizes Spring Kafka `@RetryableTopic` for robust processing of transient errors (e.g. 5xx server errors).
- **Dead Letter Topic (DLT)**: Client errors (4xx) like `InvalidTemplateException` or malformed payloads immediately bypass retry logic and route to a DLT for manual intervention, preventing infinite loops.
- **Embedded Test UI**: Comes with a sleek, glassmorphic UI dashboard to instantly dispatch and test notifications directly into Kafka.

## Prerequisites

- **Java 21**
- **Docker & Docker Compose** (for running local Kafka)

## Getting Started

### 1. Start Local Infrastructure

Start the local Kafka cluster using Docker Compose:

```bash
docker-compose up -d
```

### 2. Configure Environment

The application configuration can be found in `src/main/resources/application.yml`. 
To switch email providers, simply update:

```yaml
notification:
  provider:
    email: brevo # or 'aws'
```

### 3. Run the Application

Start the Spring Boot server using the Maven wrapper:

```bash
./mvnw spring-boot:run
```

The application runs on port `8081` by default.

### 4. Test the Service via UI Dashboard

Once the server is running, navigate to the built-in testing dashboard at:
[http://localhost:8081/](http://localhost:8081/)

From the dashboard, you can test end-to-end notification flows for all channels, and observe the processing (and error handling/DLT routing) in the console logs.

## Architecture

- **`NotificationEventConsumer`**: Listens to the `platform.notifications.dispatch` Kafka topic and handles the incoming requests.
- **`NotificationRouterService`**: Routes the notification to the correct provider implementation based on the specified channel.
- **`BrevoEmailService` / `AwsSesEmailService`**: Provider-specific integrations for email dispatch.
- **DLT Consumer**: Safely extracts failure headers for manual resolution.

## Fixes & Hardening

- Resolved `google-auth-library` version mismatch causing Firebase initialization errors on startup.
- Implemented payload validation to enforce required fields.
- Added dynamic frontend validation to the test UI for strict payload constraints.
