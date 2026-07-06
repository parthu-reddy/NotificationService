# NotificationService (CommunicationIntegration)

The NotificationService is responsible for dispatching multi-channel communications (Email, SMS, Push) based on Kafka events produced by other microservices.

## Setup & Build
1. Build the service: `mvn clean install`
2. Run the application: `mvn spring-boot:run`
3. Port: `8085`

## Key Responsibilities
- **Event Consumption**: Listens to Kafka topics (e.g., `notification-events`) for generic notification requests.
- **Provider Strategy**: Dynamically selects the appropriate communication provider (e.g., SendGrid for email, Twilio for SMS) based on the channel specified.
- **Security**: Ensures any internal API exposed is guarded by the `PreAuthFilter`.
