# **Enterprise Communication and Notification Architecture for Food Delivery Platforms**

The success of a modern food delivery ecosystem is inextricably linked to the reliability, speed, and precision of its communication layer. From the moment a customer places an order to the final delivery handoff, real-time notifications orchestrate complex logistics across a triad of participants: the customer, the restaurant, and the delivery partner. This research report details an exhaustive, production-ready architecture for integrating a centralized Communication and Notification microservice using Java and Spring Boot.  
The architecture encompasses push notifications via Firebase Cloud Messaging (FCM), Short Message Service (SMS) delivery with rigorous Distributed Ledger Technology (DLT) compliance via Exotel, WhatsApp Business API integration via Gupshup, and high-volume transactional email routing via Amazon Web Services (AWS) Simple Email Service (SES) v2. Furthermore, it outlines distributed rate-limiting mechanisms using Bucket4j and Redis to prevent abuse, alongside asynchronous message processing and fault tolerance utilizing Apache Kafka. The following sections provide step-by-step guidance, structured Java implementation, relational database schemas, and continuous deployment strategies designed specifically for handoff to advanced engineering and DevOps teams.

## **1\. Architectural Paradigm and System Design**

In a distributed microservices environment, communication modules must be completely decoupled from the synchronous execution paths of core business domains. If the Order Management Service waits for an SMS gateway to respond before confirming an order, the entire platform becomes vulnerable to third-party network latency and API rate limits.  
The optimal design employs a purely event-driven architecture. Upstream microservices, such as identity verification or logistics routing, publish standardized notification event payloads to an Apache Kafka message broker. A dedicated Notification Service operates autonomously as a Kafka consumer. This service is responsible for interpreting the event, hydrating dynamic templates with contextual data, applying rate-limiting security constraints, routing the message to the appropriate external provider API, and asynchronously processing delivery receipts via webhooks.

### **1.1 The Step-by-Step Integration Lifecycle**

Developing and deploying this communication layer follows a rigorous, phase-based methodology to ensure production readiness:

1. **Phase 1: Data Persistence and Schema Design.** Establish the foundational relational database models to track user communication preferences, store provider-approved templates (including regulatory metadata), and maintain an immutable audit trail of every dispatch attempt.  
2. **Phase 2: Event Ingestion and Message Brokering.** Configure Apache Kafka producers and consumers, establishing non-blocking retry mechanisms and dead-letter queues (DLQs) to handle transient network failures gracefully.  
3. **Phase 3: Gateway Security and Abuse Prevention.** Implement a distributed token-bucket rate limiter using Redis to protect the platform from costly SMS toll fraud and OTP bombing attacks.  
4. **Phase 4: Provider SDK and REST Integrations.** Develop modular service classes in Java to interface with FCM, Exotel, Gupshup, and AWS SES v2, mapping domain models to provider-specific payloads.  
5. **Phase 5: Asynchronous Callback Management.** Expose secure webhook endpoints to receive delivery receipts and read statuses from external providers, updating the audit logs in real-time.  
6. **Phase 6: Containerization and Orchestration.** Package the application using Docker, define Kubernetes deployment manifests, and establish automated CI/CD pipelines for seamless deployment.

## **2\. Phase 1: Relational Database Schema and Domain Modeling**

A highly normalized database schema forms the bedrock of the notification microservice. It must accommodate dynamic template resolution, enforce regulatory constraints, and provide comprehensive auditing capabilities. The following architecture utilizes PostgreSQL for data persistence.

### **2.1 Core Tables and Schema Definition**

The database must isolate internal event triggers from external provider configurations. For example, an ORDER\_DISPATCHED event might trigger an SMS, a Push Notification, and a WhatsApp message simultaneously.

| Table Name | Primary Purpose | Architectural Considerations |
| :---- | :---- | :---- |
| user\_devices | Stores active FCM registration tokens for push notifications. | Must handle multiple active devices per user. Requires an index on user\_id and a uniqueness constraint on fcm\_token to prevent duplicate dispatches1. |
| notification\_templates | Stores channel-specific message templates, bridging internal event names to external identifiers. | Must store external metadata, such as the dlt\_template\_id mandated by Indian telecom operators, to prevent silent message drops3. |
| notification\_audit\_logs | Acts as the authoritative ledger for every outbound message attempt and its ultimate delivery state. | Requires partitioning by date for performance. Needs composite indexes on status and provider\_message\_id to facilitate rapid webhook updates6. |
| user\_preferences | Manages fine-grained opt-in and opt-out toggles for various communication channels. | Critical for compliance with anti-spam regulations and maintaining high domain reputation with email providers8. |

The physical schema is established using the following Data Definition Language (DDL) scripts. These scripts enforce referential integrity and optimize query execution for webhook callbacks.

SQL  
CREATE TYPE channel\_enum AS ENUM ('SMS', 'EMAIL', 'PUSH', 'WHATSAPP');  
CREATE TYPE status\_enum AS ENUM ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'READ');

CREATE TABLE user\_devices (  
    id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
    user\_id UUID NOT NULL,  
    fcm\_token VARCHAR(255) NOT NULL UNIQUE,  
    platform VARCHAR(50) NOT NULL,  
    is\_active BOOLEAN DEFAULT TRUE,  
    last\_updated\_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT\_TIMESTAMP  
);  
CREATE INDEX idx\_user\_devices\_user\_id ON user\_devices(user\_id);

CREATE TABLE notification\_templates (  
    id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
    event\_name VARCHAR(100) NOT NULL,   
    channel channel\_enum NOT NULL,  
    content TEXT NOT NULL,  
    external\_template\_id VARCHAR(100),   
    external\_entity\_id VARCHAR(100),     
    is\_active BOOLEAN DEFAULT TRUE,  
    created\_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT\_TIMESTAMP,  
    UNIQUE(event\_name, channel)  
);

CREATE TABLE notification\_audit\_logs (  
    id UUID PRIMARY KEY DEFAULT gen\_random\_uuid(),  
    user\_id UUID NOT NULL,  
    channel channel\_enum NOT NULL,  
    recipient\_address VARCHAR(255) NOT NULL,   
    template\_id UUID REFERENCES notification\_templates(id),  
    provider\_message\_id VARCHAR(255) UNIQUE,  
    status status\_enum DEFAULT 'QUEUED',  
    error\_reason TEXT,  
    created\_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT\_TIMESTAMP,  
    updated\_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT\_TIMESTAMP  
);  
CREATE INDEX idx\_audit\_logs\_provider\_msg\_id ON notification\_audit\_logs(provider\_message\_id);

### **2.2 Java Persistence API (JPA) Entity Mapping**

To bridge the relational database to the structured Spring Boot codebase, JPA entities are utilized. The NotificationAuditLog entity is critical for tracking state transitions initiated by provider webhooks.

Java  
import jakarta.persistence.\*;  
import lombok.Data;  
import org.hibernate.annotations.CreationTimestamp;  
import org.hibernate.annotations.UpdateTimestamp;  
import java.time.OffsetDateTime;  
import java.util.UUID;

@Data  
@Entity  
@Table(name \= "notification\_audit\_logs", indexes \= {  
    @Index(name \= "idx\_provider\_msg\_id", columnList \= "provider\_message\_id")  
})  
public class NotificationAuditLog {

    @Id  
    @GeneratedValue(strategy \= GenerationType.UUID)  
    private UUID id;

    @Column(name \= "user\_id", nullable \= false)  
    private UUID userId;

    @Enumerated(EnumType.STRING)  
    @Column(nullable \= false)  
    private ChannelType channel;

    @Column(name \= "recipient\_address", nullable \= false)  
    private String recipientAddress;

    @ManyToOne(fetch \= FetchType.LAZY)  
    @JoinColumn(name \= "template\_id")  
    private NotificationTemplate template;

    @Column(name \= "provider\_message\_id", unique \= true)  
    private String providerMessageId;

    @Enumerated(EnumType.STRING)  
    @Column(nullable \= false)  
    private DeliveryStatus status \= DeliveryStatus.QUEUED;

    @Column(name \= "error\_reason", columnDefinition \= "TEXT")  
    private String errorReason;

    @CreationTimestamp  
    @Column(name \= "created\_at", updatable \= false)  
    private OffsetDateTime createdAt;

    @UpdateTimestamp  
    @Column(name \= "updated\_at")  
    private OffsetDateTime updatedAt;  
}

By ensuring the provider\_message\_id is indexed, the system can efficiently process thousands of asynchronous webhooks per second—a necessity when providers like Exotel or Gupshup confirm the terminal state (e.g., delivered or failed) of a dispatched message6.

## **3\. Phase 2: Event Ingestion and Fault Tolerance with Apache Kafka**

The communication microservice receives instructions via Apache Kafka. When an external API (like WhatsApp or an SMS gateway) experiences an outage, the system must not lose messages. Instead, it must utilize intelligent retry strategies.

### **3.1 Consumer Configuration and Non-Blocking Retries**

Spring Kafka facilitates resilient consumer design through the @RetryableTopic annotation10. Unlike blocking retries—which stall the entire consumer thread while waiting for a timeout—non-blocking retries automatically route failed messages to auxiliary topics with increasing backoff intervals10. This ensures that high-priority notifications (like a new order alert) bypass stalled notifications (like a failed promotional SMS).  
The Kafka consumer listens for a serialized JSON event, processes the payload, and delegates it to the routing layer.

Java  
import org.springframework.kafka.annotation.KafkaListener;  
import org.springframework.kafka.annotation.RetryableTopic;  
import org.springframework.retry.annotation.Backoff;  
import org.springframework.stereotype.Component;  
import org.springframework.messaging.handler.annotation.Payload;  
import lombok.extern.slf4j.Slf4j;

@Slf4j  
@Component  
public class NotificationEventConsumer {

    private final NotificationRouterService routerService;

    public NotificationEventConsumer(NotificationRouterService routerService) {  
        this.routerService \= routerService;  
    }

    @RetryableTopic(  
            attempts \= "4", // Initial attempt \+ 3 retries  
            backoff \= @Backoff(delay \= 2000, multiplier \= 2.0, maxDelay \= 10000), // 2s, 4s, 8s backoff  
            autoCreateTopics \= "true",  
            exclude \= {  
                IllegalArgumentException.class,   
                UserOptedOutException.class,  
                InvalidTemplateException.class  
            }   
    )  
    @KafkaListener(topics \= "platform.notifications.dispatch", groupId \= "notification-service-group")  
    public void consumeNotificationEvent(@Payload NotificationRequestEvent event) {  
        log.info("Received notification request for user {} on channel {}", event.getUserId(), event.getChannel());  
          
        // The router service is responsible for rate-limiting checks and provider delegation  
        routerService.routeAndDispatch(event);  
    }

    @KafkaListener(topics \= "platform.notifications.dispatch.DLT", groupId \= "notification-service-group")  
    public void processDeadLetterTopic(@Payload NotificationRequestEvent failedEvent) {  
        log.error("Terminal failure for event {}. Moving to manual intervention queue.", failedEvent.getEventId());  
        // Persist final failure state to the notification\_audit\_logs table to mark as FAILED  
    }  
}

The configuration explicitly excludes exceptions indicating irreversible logical errors, such as UserOptedOutException or IllegalArgumentException11. Retrying a request to a deactivated phone number wastes API calls and processing power. Transitory network exceptions or HTTP 500 errors from the providers will automatically trigger the retry cascade13.

## **4\. Phase 3: Gateway Security and Distributed Rate Limiting**

Food delivery applications are frequent targets for malicious actors who exploit notification endpoints to execute OTP bombing or incur fraudulent SMS charges. Implementing application-layer rate limiting is non-negotiable.

### **4.1 Token Bucket Algorithm via Bucket4j and Redis**

Bucket4j is an advanced Java rate-limiting library based on the token bucket algorithm14. By utilizing Redis as the underlying state management layer, rate limit counters are shared globally across all horizontally scaled pods of the Notification Service16.  
The architecture applies multidimensional limits. For instance, a single user ID may be limited to generating 3 OTPs per minute (to prevent rapid-fire bombing) and a maximum of 10 OTPs per hour (to prevent sustained abuse)19.  
**Maven Dependencies:**

XML  
\<dependency\>  
    \<groupId\>com.bucket4j\</groupId\>  
    \<artifactId\>bucket4j-core\</artifactId\>  
    \<version\>8.15.0\</version\>  
\</dependency\>  
\<dependency\>  
    \<groupId\>com.bucket4j\</groupId\>  
    \<artifactId\>bucket4j-redis\</artifactId\>  
    \<version\>8.15.0\</version\>  
\</dependency\>

**Rate Limiter Implementation:**

Java  
import io.github.bucket4j.Bandwidth;  
import io.github.bucket4j.BucketConfiguration;  
import io.github.bucket4j.Refill;  
import io.github.bucket4j.distributed.BucketProxy;  
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;  
import org.springframework.stereotype.Service;  
import java.time.Duration;

@Service  
public class RateLimitingService {

    private final LettuceBasedProxyManager\<byte\[\]\> proxyManager;

    public RateLimitingService(LettuceBasedProxyManager\<byte\[\]\> proxyManager) {  
        this.proxyManager \= proxyManager;  
    }

    /\*\*  
     \* Resolves the bucket for a specific target (e.g., Phone Number or User ID).  
     \*/  
    public BucketProxy resolveBucket(String targetIdentifier, String eventType) {  
        String cacheKey \= "rate\_limit:" \+ eventType \+ ":" \+ targetIdentifier;  
          
        BucketConfiguration configuration \= BucketConfiguration.builder()  
                // Greedy refill: tokens are added fractionally over time  
                .addLimit(Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(1))))  
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1))))  
                .build();

        return proxyManager.builder().build(cacheKey.getBytes(), configuration);  
    }

    /\*\*  
     \* Executes the rate limit check.  
     \*/  
    public void enforceRateLimit(String targetIdentifier, String eventType) {  
        BucketProxy bucket \= resolveBucket(targetIdentifier, eventType);  
        if (\!bucket.tryConsume(1)) {  
            throw new RateLimitExceededException("Rate limit exhausted for target: " \+ targetIdentifier);  
        }  
    }  
}

In this design, the tryConsume(1) method evaluates the Redis state atomically using Lua scripts under the hood, ensuring thread safety and preventing race conditions during concurrent request spikes20. When the rate limit is exceeded, the custom RateLimitExceededException is thrown. This exception must be registered in the @RetryableTopic exclusions to prevent the Kafka consumer from endlessly retrying a rate-limited action11.

## **5\. Phase 4: Provider Integrations and Step-by-Step Code Construction**

The core functionality of the Notification Service lies in its ability to translate internal templates into provider-specific REST API calls.

### **5.1 Push Notifications via Firebase Cloud Messaging (FCM)**

Firebase Cloud Messaging (FCM) supports payloads up to 4096 bytes and operates cross-platform22. Within the food delivery domain, FCM handles high-frequency updates, such as a delivery driver's GPS location changes, due to its low latency and absence of direct per-message costs.  
The integration utilizes the firebase-admin SDK. The SDK must be initialized securely using service account credentials injected into the container environment2.  
**FCM Dispatch Service:**

Java  
import com.google.firebase.messaging.\*;  
import org.springframework.stereotype.Service;  
import java.util.List;  
import java.util.Map;  
import lombok.extern.slf4j.Slf4j;

@Slf4j  
@Service  
public class FcmService {

    /\*\*  
     \* Unicast messaging for targeted user alerts.  
     \*/  
    public String sendDirectNotification(String token, String title, String body, Map\<String, String\> payload) throws FirebaseMessagingException {  
        Notification notification \= Notification.builder()  
                .setTitle(title)  
                .setBody(body)  
                .build();

        Message message \= Message.builder()  
                .setToken(token)  
                .setNotification(notification)  
                .putAllData(payload) // Data payload used for background processing or deep linking  
                .build();

        // Returns the message ID string in the format projects/{project\_id}/messages/{message\_id}  
        String response \= FirebaseMessaging.getInstance().send(message);  
        log.info("FCM Unicast successful. Message ID: {}", response);  
        return response;  
    }

    /\*\*  
     \* Multicast messaging for driver fleet broadcasts.  
     \*/  
    public void sendMulticastNotification(List\<String\> tokens, String title, String body) throws FirebaseMessagingException {  
        // FCM limits multicast payloads to 500 tokens per batch  
        MulticastMessage message \= MulticastMessage.builder()  
                .addAllTokens(tokens)  
                .setNotification(Notification.builder()  
                        .setTitle(title)  
                        .setBody(body)  
                        .build())  
                .build();

        BatchResponse response \= FirebaseMessaging.getInstance().sendEachForMulticast(message);  
          
        if (response.getFailureCount() \> 0) {  
            log.warn("{} messages failed to deliver in multicast batch.", response.getFailureCount());  
            // Iterate through responses and remove inactive/unregistered tokens from the database  
        }  
    }  
}

The sendEachForMulticast method is essential for operational efficiency. When broadcasting a new gig opportunity to all delivery drivers within a specific geofence, compiling up to 500 registration tokens into a single API call drastically reduces network overhead and prevents thread starvation in the Spring Boot application1. If the BatchResponse indicates a failure, the application must identify unregistered tokens and prune them from the user\_devices table to maintain system hygiene2.

### **5.2 SMS Delivery and Strict DLT Compliance via Exotel**

SMS serves as the ultimate fallback channel. For platforms operating in India, the integration must navigate the complex Distributed Ledger Technology (DLT) framework mandated by the Telecom Regulatory Authority of India (TRAI)3.  
Every commercial SMS requires a registered Principal Entity (PE) ID, an approved Sender ID (Header), and an approved Content Template ID7. Exotel supports template variables using %s, %d, or {\#var\#}. If the compiled message diverges from the registered DLT template format, the telecom operator will reject it, and Exotel will log a FAILED\_DLT\_SCRUBBING\_ERROR3.  
**Exotel REST Integration:**

Java  
import org.springframework.beans.factory.annotation.Value;  
import org.springframework.stereotype.Service;  
import java.net.URI;  
import java.net.http.HttpClient;  
import java.net.http.HttpRequest;  
import java.net.http.HttpResponse;  
import java.net.URLEncoder;  
import java.nio.charset.StandardCharsets;  
import java.util.Base64;  
import java.util.Map;  
import java.util.stream.Collectors;  
import com.fasterxml.jackson.databind.JsonNode;  
import com.fasterxml.jackson.databind.ObjectMapper;

@Service  
public class ExotelSmsService {

    @Value("${exotel.api.key}")  
    private String apiKey;

    @Value("${exotel.api.token}")  
    private String apiToken;

    @Value("${exotel.account.sid}")  
    private String accountSid;

    @Value("${exotel.subdomain:api.in.exotel.com}")  
    private String subdomain;  
      
    @Value("${platform.webhook.base-url}")  
    private String webhookBaseUrl;

    private final HttpClient httpClient \= HttpClient.newBuilder().build();  
    private final ObjectMapper objectMapper \= new ObjectMapper();

    public String dispatchSms(String recipient, String content, String senderId, String dltEntityId, String dltTemplateId) throws Exception {  
        String url \= String.format("https://%s/v1/Accounts/%s/Sms/send.json", subdomain, accountSid);  
        String authHeader \= "Basic " \+ Base64.getEncoder().encodeToString((apiKey \+ ":" \+ apiToken).getBytes(StandardCharsets.UTF\_8));

        Map\<String, String\> formData \= Map.of(  
                "From", senderId,  
                "To", recipient,  
                "Body", content,  
                "DltEntityId", dltEntityId,  
                "DltTemplateId", dltTemplateId,  
                "StatusCallback", webhookBaseUrl \+ "/webhooks/exotel/status" // Instructs Exotel to POST back delivery updates  
        );

        String formBody \= formData.entrySet().stream()  
                .map(e \-\> e.getKey() \+ "=" \+ URLEncoder.encode(e.getValue(), StandardCharsets.UTF\_8))  
                .collect(Collectors.joining("&"));

        HttpRequest request \= HttpRequest.newBuilder()  
                .uri(URI.create(url))  
                .header("Authorization", authHeader)  
                .header("Content-Type", "application/x-www-form-urlencoded")  
                .POST(HttpRequest.BodyPublishers.ofString(formBody))  
                .build();

        HttpResponse\<String\> response \= httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() \== 200) {  
            JsonNode rootNode \= objectMapper.readTree(response.body());  
            // A 200 OK means the message is 'queued', not delivered. The Sid is used for tracking.  
            return rootNode.path("SMSMessage").path("Sid").asText();   
        } else {  
            throw new RuntimeException("Exotel SMS Failed. HTTP " \+ response.statusCode() \+ " Response: " \+ response.body());  
        }  
    }  
}

Architecturally, setting the subdomain to api.in.exotel.com is optimized for traffic originating and terminating within India, minimizing TCP handshake latency7. The response from the Exotel API is asynchronous; a 200 OK simply confirms the message is queued7. The actual delivery status must be resolved by tracking the returned Sid against the StatusCallback webhook event7.

### **5.3 WhatsApp Business API via Gupshup**

WhatsApp achieves unparalleled engagement rates, with statistics indicating that 95% of messages are read within 3 minutes28. In a food delivery architecture, WhatsApp is utilized for interactive notifications, such as real-time tracking links or rich-media invoice delivery9.  
To initiate a conversation outside of a 24-hour customer service window, the platform must use a pre-approved Highly Structured Message (HSM) template6.  
**Gupshup Integration Parameters:**

| Parameter | Type | Functional Requirement |
| :---- | :---- | :---- |
| source | String | The registered WhatsApp Business API phone number in E.164 format (e.g., 919876543210\)29. |
| destination | String | The end-user's verified phone number29. |
| template | JSON Object | Must contain the id of the approved template and a params array containing variables exactly matching the template placeholders29. |

**Gupshup WhatsApp Service:**

Java  
import com.fasterxml.jackson.databind.ObjectMapper;  
import org.springframework.beans.factory.annotation.Value;  
import org.springframework.stereotype.Service;  
import java.net.URI;  
import java.net.http.HttpClient;  
import java.net.http.HttpRequest;  
import java.net.http.HttpResponse;  
import java.net.URLEncoder;  
import java.nio.charset.StandardCharsets;  
import java.util.List;  
import java.util.Map;

@Service  
public class GupshupWhatsAppService {

    @Value("${gupshup.api.key}")  
    private String apiKey;

    @Value("${gupshup.source.number}")  
    private String sourceNumber;

    private final HttpClient httpClient \= HttpClient.newBuilder().build();  
    private final ObjectMapper objectMapper \= new ObjectMapper();

    public String dispatchWhatsAppTemplate(String destination, String templateId, List\<String\> templateParams) throws Exception {  
        String endpoint \= "https://api.gupshup.io/wa/api/v1/template/msg";

        // Construct the strictly required template object  
        Map\<String, Object\> templateObj \= Map.of(  
                "id", templateId,  
                "params", templateParams  
        );  
        String templateJson \= objectMapper.writeValueAsString(templateObj);

        String requestBody \= "source=" \+ URLEncoder.encode(sourceNumber, StandardCharsets.UTF\_8) \+  
                "\&destination=" \+ URLEncoder.encode(destination, StandardCharsets.UTF\_8) \+  
                "\&template=" \+ URLEncoder.encode(templateJson, StandardCharsets.UTF\_8);

        HttpRequest request \= HttpRequest.newBuilder()  
                .uri(URI.create(endpoint))  
                .header("apikey", apiKey)  
                .header("Content-Type", "application/x-www-form-urlencoded")  
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))  
                .build();

        HttpResponse\<String\> response \= httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() \>= 200 && response.statusCode() \< 300) {  
            JsonNode rootNode \= objectMapper.readTree(response.body());  
            // Successful API requests process asynchronously and return status 'submitted'  
            return rootNode.path("messageId").asText();  
        } else {  
            throw new RuntimeException("Gupshup Dispatch Failed: " \+ response.body());  
        }  
    }  
}

The Gupshup API evaluates the params array strictly by order of appearance. For example, if the template is Hello {1}, your order {2} is confirmed, the array must be passed as \["John", "\#ORD999"\]29. Gupshup provides a 2XX response with a messageId and a status of submitted; subsequent updates (enqueued, sent, delivered, read) must be tracked via webhooks6.

### **5.4 Transactional Email Delivery via AWS SES v2**

Email remains the system of record for highly detailed documents, such as end-of-month driver summaries, customer invoices, and regulatory policy updates. AWS Simple Email Service (SES) v2 provides an API optimized for high throughput and robust deliverability8.  
By utilizing the modern AWS SDK for Java 2.x, the application leverages non-blocking HTTP implementations, mitigating the performance bottlenecks historically associated with SMTP or older AWS v1 clients33.  
**AWS SES v2 Dispatch Service:**

Java  
import software.amazon.awssdk.regions.Region;  
import software.amazon.awssdk.services.sesv2.SesV2Client;  
import software.amazon.awssdk.services.sesv2.model.\*;  
import org.springframework.stereotype.Service;  
import javax.annotation.PostConstruct;  
import javax.annotation.PreDestroy;

@Service  
public class AwsSesEmailService {

    private SesV2Client sesV2Client;

    @Value("${aws.ses.configuration-set-name}")  
    private String configurationSetName;

    @PostConstruct  
    public void initializeClient() {  
        // Initializes using the DefaultCredentialsProvider chain (e.g., IAM roles, Env Vars)  
        this.sesV2Client \= SesV2Client.builder()  
                .region(Region.AP\_SOUTH\_1) // Region matching the core infrastructure  
                .build();  
    }

    @PreDestroy  
    public void cleanup() {  
        if (sesV2Client \!= null) {  
            sesV2Client.close();  
        }  
    }

    public String sendHtmlEmail(String senderAddress, String recipientAddress, String subject, String htmlBody) {  
        Destination destination \= Destination.builder()  
                .toAddresses(recipientAddress)  
                .build();

        Message message \= Message.builder()  
                .subject(Content.builder().data(subject).build())  
                .body(Body.builder().html(Content.builder().data(htmlBody).build()).build())  
                .build();

        EmailContent emailContent \= EmailContent.builder()  
                .simple(message)  
                .build();

        SendEmailRequest emailRequest \= SendEmailRequest.builder()  
                .fromEmailAddress(senderAddress)  
                .destination(destination)  
                .content(emailContent)  
                .configurationSetName(configurationSetName) // Required for tracking bounces/complaints  
                .build();

        try {  
            SendEmailResponse response \= sesV2Client.sendEmail(emailRequest);  
            return response.messageId();  
        } catch (SesV2Exception e) {  
            throw new RuntimeException("AWS SES exception: " \+ e.awsErrorDetails().errorMessage());  
        }  
    }  
}

The incorporation of a configurationSetName is a critical architecture requirement for maintaining domain reputation8. AWS rigorously monitors bounce and complaint rates; exceeding the threshold can lead to immediate account suspension. The Configuration Set directs SES to route bounce, complaint, and delivery events to an Amazon SNS topic, which subsequently triggers an HTTP webhook on the Notification Service8. If a recipient's email address triggers a hard bounce, the platform must programmatically halt future communications to that address. Furthermore, SES supports complex raw MIME formats, allowing for the generation of attachments like PDF invoices by specifying Content-Type: application/pdf alongside Base64 encoding34.

## **6\. Phase 5: Asynchronous Webhook and Callback Management**

All external providers (Exotel, Gupshup, AWS SES) utilize asynchronous processing. When the Java service issues a dispatch command, the provider returns a unique identifier (e.g., Sid or messageId), but the ultimate fate of the message (delivered, read, failed) is communicated asynchronously via HTTP POST callbacks (webhooks)6.

### **6.1 Exotel Delivery Receipt Controller**

The application exposes a dedicated REST controller to ingest these events, cross-reference the unique identifier against the notification\_audit\_logs table, and update the delivery status in real time.

Java  
import org.springframework.http.ResponseEntity;  
import org.springframework.web.bind.annotation.\*;  
import lombok.extern.slf4j.Slf4j;  
import java.util.Map;

@Slf4j  
@RestController  
@RequestMapping("/webhooks/providers")  
public class ProviderWebhookController {

    private final AuditLogService auditLogService;

    public ProviderWebhookController(AuditLogService auditLogService) {  
        this.auditLogService \= auditLogService;  
    }

    /\*\*  
     \* Handles Exotel SMS Status Callbacks.  
     \* Exotel POSTs form-urlencoded data including SmsSid and Status.  
     \*/  
    @PostMapping(value \= "/exotel/status", consumes \= "application/x-www-form-urlencoded")  
    public ResponseEntity\<Void\> handleExotelCallback(@RequestParam Map\<String, String\> payload) {  
        String smsSid \= payload.get("SmsSid");  
        String status \= payload.get("Status"); // Values: sent, delivered, failed, failed-dnd  
        String detailedStatus \= payload.get("DetailedStatus");

        log.debug("Received Exotel callback for SID: {} with status: {}", smsSid, status);

        DeliveryStatus domainStatus \= switch (status.toLowerCase()) {  
            case "delivered", "sent" \-\> DeliveryStatus.DELIVERED;  
            case "failed", "failed-dnd" \-\> DeliveryStatus.FAILED;  
            default \-\> DeliveryStatus.QUEUED;  
        };

        auditLogService.updateLogStatus(smsSid, domainStatus, detailedStatus);  
          
        // Return 200 OK rapidly to acknowledge receipt  
        return ResponseEntity.ok().build();  
    }  
}

The StatusCallback implementation must return a 200 OK rapidly to prevent the provider from retrying the webhook payload27. For Exotel, specific status codes like failed-dnd indicate the recipient is registered on the National Customer Preference Register (NCPR), providing actionable data to the platform to avoid future promotional attempts to that number3.

## **7\. Phase 6: Production-Ready Deployment and Orchestration Processes**

To facilitate a seamless handoff to development operations (the "antigravity" process), the codebase must be rigorously structured for automated CI/CD pipelines, containerization, and configuration management.

### **7.1 Dockerfile Optimization**

A multi-stage Docker build process ensures the final production image is minimal, omitting heavy compilation dependencies.

Dockerfile  
\# Stage 1: Dependency resolution and compilation  
FROM maven:3.9.4\-eclipse-temurin-21 AS build  
WORKDIR /workspace  
COPY pom.xml .  
\# Download dependencies to cache the layer  
RUN mvn dependency:go-offline  
COPY src ./src  
RUN mvn clean package \-DskipTests

\# Stage 2: Minimal Runtime Environment  
FROM eclipse-temurin:21\-jre-alpine  
WORKDIR /app

\# Non-root user setup for security  
RUN addgroup \-S spring && adduser \-S spring \-G spring  
USER spring:spring

COPY \--from=build /workspace/target/notification-service-1.0.0.jar app.jar

\# JVM Tuning for container environments  
ENV JAVA\_OPTS="-XX:MaxRAMPercentage=75.0 \-XX:+UseG1GC"

EXPOSE 8080  
ENTRYPOINT \["sh", "-c", "java $JAVA\_OPTS \-jar app.jar"\]

### **7.2 Kubernetes Orchestration and Secret Management**

In a Kubernetes environment, application configuration must be entirely externalized. Hardcoding Exotel API tokens, Gupshup keys, or AWS access credentials into the image is a severe security vulnerability.  
**Deployment Manifest (deployment.yaml):**

YAML  
apiVersion: apps/v1  
kind: Deployment  
metadata:  
  name: notification-service  
  namespace: food-delivery-core  
spec:  
  replicas: 3  
  selector:  
    matchLabels:  
      app: notification-service  
  template:  
    metadata:  
      labels:  
        app: notification-service  
    spec:  
      containers:  
      \- name: notification-service  
        image: repository.internal/antigravity/notification-service:1.0.0  
        ports:  
        \- containerPort: 8080  
        resources:  
          requests:  
            memory: "512Mi"  
            cpu: "250m"  
          limits:  
            memory: "1Gi"  
            cpu: "1000m"  
        envFrom:  
        \- configMapRef:  
            name: notification-config  
        \- secretRef:  
            name: notification-secrets  
        volumeMounts:  
        \- name: firebase-credentials  
          mountPath: /etc/secrets/firebase  
          readOnly: true  
        livenessProbe:  
          httpGet:  
            path: /actuator/health/liveness  
            port: 8080  
          initialDelaySeconds: 20  
          periodSeconds: 10  
      volumes:  
      \- name: firebase-credentials  
        secret:  
          secretName: firebase-admin-json

The Firebase service account JSON is securely injected via a Kubernetes Volume Mount from a Secret, allowing the Spring Boot FirebaseApp initializer to locate the file safely2.

### **7.3 Continuous Integration and Deployment (CI/CD)**

The automated deployment process relies on a continuous integration pipeline. The pipeline dictates the structured lifecycle of the code from commit to production:

1. **Code Compilation and Static Analysis**: The pipeline executes mvn clean verify and runs static code analysis tools (like SonarQube) to enforce high test coverage and prevent code smells.  
2. **Container Build and Security Scan**: The Docker image is constructed. Tools like Trivy are integrated to scan the resulting image for CVEs (Common Vulnerabilities and Exposures) prior to registry upload.  
3. **Infrastructure as Code (IaC) Application**: A deployment utility, such as Helm or ArgoCD, detects the new image tag and initiates a rolling update on the Kubernetes cluster. The rolling update guarantees zero downtime, ensuring the Kafka consumers are gracefully shut down and rebalanced without dropping events.  
4. **Telemetry and Monitoring**: Using Spring Boot Actuator and Micrometer, the service automatically exports telemetry metrics. A Prometheus and Grafana stack scrapes these metrics, monitoring variables such as API response latency from Exotel and Gupshup, current Token Bucket exhaustion rates, and the depth of the Kafka Dead Letter Topics.

By combining strict domain modeling, resilient event-driven execution, robust API interactions, and automated container orchestration, this architecture guarantees that the platform's communication fabric operates with deterministic reliability under heavy production loads.

#### **Works cited**

1. firebase-admin-java/src/test/java/com/google/firebase/snippets/FirebaseMessagingSnippets.java at main \- GitHub, [https://github.com/firebase/firebase-admin-java/blob/master/src/test/java/com/google/firebase/snippets/FirebaseMessagingSnippets.java](https://github.com/firebase/firebase-admin-java/blob/master/src/test/java/com/google/firebase/snippets/FirebaseMessagingSnippets.java)  
2. Send a message using Firebase Admin SDK, [https://firebase.google.com/docs/cloud-messaging/send/admin-sdk](https://firebase.google.com/docs/cloud-messaging/send/admin-sdk)  
3. exotel Messaging Apis User Guide \- Manuals.plus, [https://manuals.plus/m/9e2de0b283cbd98d51cb9540f931bfbd11e25dfb040f77e93d3f96044dce0660\_optim.pdf](https://manuals.plus/m/9e2de0b283cbd98d51cb9540f931bfbd11e25dfb040f77e93d3f96044dce0660_optim.pdf)  
4. ForthFocus SMS – OTP Verification, Order Notifications & Indian DLT for WooCommerce, [https://wordpress.org/plugins/forthfocus-sms-otp/](https://wordpress.org/plugins/forthfocus-sms-otp/)  
5. How to send SMS using Exotel with DLT Template Scrubbing?, [https://docs.exotel.com/messaging-apis/how-to-send-sms-using-exotel-with-dlt-template-scrubbing](https://docs.exotel.com/messaging-apis/how-to-send-sms-using-exotel-with-dlt-template-scrubbing)  
6. WhatsApp Business API \- Gupshup Console, [https://console-docs.gupshup.io/docs/whatsapp-business-api](https://console-docs.gupshup.io/docs/whatsapp-business-api)  
7. Quickstart: Send Your First SMS | Exotel Developer Docs, [https://developer.exotel.com/docs/sms-api/quickstart](https://developer.exotel.com/docs/sms-api/quickstart)  
8. Sending email through Amazon SES using an AWS SDK \- Amazon Simple Email Service, [https://docs.aws.amazon.com/ses/latest/dg/send-an-email-using-sdk-programmatically.html](https://docs.aws.amazon.com/ses/latest/dg/send-an-email-using-sdk-programmatically.html)  
9. Best WhatsApp message template \- Gupshup, [https://www.gupshup.ai/resources/blog/all-you-need-to-know-about-whatsapp-messaging-templates/](https://www.gupshup.ai/resources/blog/all-you-need-to-know-about-whatsapp-messaging-templates/)  
10. Configuration :: Spring Kafka, [https://docs.spring.io/spring-kafka/reference/retrytopic/retry-config.html](https://docs.spring.io/spring-kafka/reference/retrytopic/retry-config.html)  
11. Implementing Retry in Kafka Consumer \- Baeldung, [https://www.baeldung.com/spring-retry-kafka-consumer](https://www.baeldung.com/spring-retry-kafka-consumer)  
12. lydtechconsulting/kafka-consumer-retry: Spring Boot application demonstrating Kafka stateless and stateful retry \- GitHub, [https://github.com/lydtechconsulting/kafka-consumer-retry](https://github.com/lydtechconsulting/kafka-consumer-retry)  
13. Building Fault-Tolerant Kafka Consumers in Spring Boot \- DZone, [https://dzone.com/articles/building-fault-tolerant-kafka-consumers-in-spring](https://dzone.com/articles/building-fault-tolerant-kafka-consumers-in-spring)  
14. Rate Limiting a Spring API Using Bucket4j \- GeeksforGeeks, [https://www.geeksforgeeks.org/advance-java/rate-limiting-a-spring-api-using-bucket4j/](https://www.geeksforgeeks.org/advance-java/rate-limiting-a-spring-api-using-bucket4j/)  
15. Bucket4j 8.9.0 Reference, [https://bucket4j.com/8.9.0/toc.html](https://bucket4j.com/8.9.0/toc.html)  
16. IP-Based Rate Limiting for Spring APIs using Bucket4j (3 Alternatives) | by Okan Ardıç, [https://medium.com/@okan.ardic/ip-based-rate-limiting-for-spring-apis-using-bucket4j-3-alternatives-1ad62ca0579f](https://medium.com/@okan.ardic/ip-based-rate-limiting-for-spring-apis-using-bucket4j-3-alternatives-1ad62ca0579f)  
17. Rate Limiting in Spring Boot REST APIs: Bucket4j \+ Redis \- DEV Community, [https://dev.to/shubham\_bhati/rate-limiting-in-spring-boot-rest-apis-bucket4j-redis-1ph3](https://dev.to/shubham_bhati/rate-limiting-in-spring-boot-rest-apis-bucket4j-redis-1ph3)  
18. Rate Limiting with Spring Boot, Bucket4j, and Redis \- INNOQ, [https://www.innoq.com/en/blog/2024/03/distributed-rate-limiting-with-spring-boot-and-redis/](https://www.innoq.com/en/blog/2024/03/distributed-rate-limiting-with-spring-boot-and-redis/)  
19. Rate Limiting a Spring API Using Bucket4j \- Baeldung, [https://www.baeldung.com/spring-bucket4j](https://www.baeldung.com/spring-bucket4j)  
20. API Rate Limits with Spring Boot and Redis Buckets \- Medium, [https://medium.com/@AlexanderObregon/api-rate-limits-with-spring-boot-and-redis-buckets-4816b67526f5](https://medium.com/@AlexanderObregon/api-rate-limits-with-spring-boot-and-redis-buckets-4816b67526f5)  
21. Rate Limiting in Java Spring with Redis: Fixed Window Implementation, [https://redis.io/tutorials/rate-limiting-in-java-spring-with-redis/](https://redis.io/tutorials/rate-limiting-in-java-spring-with-redis/)  
22. Firebase Cloud Messaging, [https://firebase.google.com/docs/cloud-messaging](https://firebase.google.com/docs/cloud-messaging)  
23. Firebase Cloud Messaging Integration: Enabling Push Notifications \- Mindbowser, [https://www.mindbowser.com/firebase-cloud-messaging-integration/](https://www.mindbowser.com/firebase-cloud-messaging-integration/)  
24. MulticastMessage | Firebase \- Google, [https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/messaging/MulticastMessage](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/messaging/MulticastMessage)  
25. Communication Platform as a Service Complete Guide 2026 \- Bonvoice, [https://bonvoice.com/insights/communication-platform-as-a-service-guide/](https://bonvoice.com/insights/communication-platform-as-a-service-guide/)  
26. DLT impact on SMS \- MoEngage, [https://moengage.com/docs/user-guide/campaigns-and-channels/sms-mms-and-rcs/compliance-and-deliverability/dlt-impact-on-sms](https://moengage.com/docs/user-guide/campaigns-and-channels/sms-mms-and-rcs/compliance-and-deliverability/dlt-impact-on-sms)  
27. Send SMS API \- Exotel Developer Portal, [https://developer.exotel.com/api/sms](https://developer.exotel.com/api/sms)  
28. What is an SMS API? Learn How It Works \- Gupshup, [https://www.gupshup.ai/resources/blog/everything-you-wanted-to-know-about-sms-api/](https://www.gupshup.ai/resources/blog/everything-you-wanted-to-know-about-sms-api/)  
29. Template messages \- Gupshup Documentation, [https://docs.gupshup.io/docs/template-messages](https://docs.gupshup.io/docs/template-messages)  
30. SEND whatspp message Gupshup with template \- APIs \- Bubble Forum, [https://forum.bubble.io/t/send-whatspp-message-gupshup-with-template/277090](https://forum.bubble.io/t/send-whatspp-message-gupshup-with-template/277090)  
31. How do I send a template message using the API? \- Gupshup, [https://support.gupshup.io/hc/en-us/articles/360017344280-How-do-I-send-a-template-message-using-the-API](https://support.gupshup.io/hc/en-us/articles/360017344280-How-do-I-send-a-template-message-using-the-API)  
32. Sending emails programmatically through the Amazon SES SMTP interface, [https://docs.aws.amazon.com/ses/latest/dg/send-using-smtp-programmatically.html](https://docs.aws.amazon.com/ses/latest/dg/send-using-smtp-programmatically.html)  
33. Amazon SES API v2 examples using SDK for Java 2.x, [https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java\_sesv2\_code\_examples.html](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_sesv2_code_examples.html)  
34. Sending raw email using the Amazon SES API v2 \- AWS Documentation, [https://docs.aws.amazon.com/ses/latest/dg/send-email-raw.html](https://docs.aws.amazon.com/ses/latest/dg/send-email-raw.html)  
35. Sending Push Notifications Using Spring Boot and Firebase \- Medium, [https://medium.com/@AlexanderObregon/sending-push-notifications-using-spring-boot-and-firebase-e1227a7eea99](https://medium.com/@AlexanderObregon/sending-push-notifications-using-spring-boot-and-firebase-e1227a7eea99)