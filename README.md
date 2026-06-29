# Communication Integration (Notification Service)

The Communication Integration service is a lightweight, decoupled microservice responsible for dispatching external notifications (Push, SMS, Email). 

## Key Responsibilities
- Listens to the `notification-events` Kafka topic.
- Integrates with third-party providers (e.g., Twilio, Firebase Cloud Messaging, SendGrid) to deliver messages.
- Acts as an isolated boundary so core services do not block on external HTTP calls.

## Running Locally

```bash
./mvnw spring-boot:run
```
