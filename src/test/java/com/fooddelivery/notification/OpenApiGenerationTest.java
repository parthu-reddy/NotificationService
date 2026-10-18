package com.fooddelivery.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

@SpringBootTest(classes = OpenApiGenerationTest.TestApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb_openapi;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "springdoc.writer-with-default-pretty-printer=true",
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092", "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.fail-fast=true",
    "spring.flyway.enabled=false",    "spring.sql.init.mode=never",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.redis.enabled=false",
    "management.health.redis.enabled=false",    "jwt.secret=dummy",
    "jwt.expiration=3600000",
    "google.maps.api.key=dummy",
    "stripe.api.key=dummy",
    "stripe.webhook.secret=dummy",
    "platform.webhook.secret=dummy",
    "razorpay.api.key=dummy",
    "razorpay.api.secret=dummy",
    "aws.accessKeyId=dummy",
    "aws.secretKey=dummy",
    "aws.s3.bucket=dummy",
    "aws.region=dummy",
    "twilio.account_sid=dummy",
    "twilio.auth_token=dummy",
    "twilio.phone_number=dummy",
    "brevo.api.key=dummy",
    "cashfree.client.id=dummy",
    "cashfree.client.secret=dummy",
    "exotel.account.sid=dummy",
    "exotel.api.key=dummy",
    "exotel.api.token=dummy",
    "gupshup.api.key=dummy",
    "gupshup.source.number=dummy",
    "olamaps.api.key=dummy",
    "platform.default-currency=USD",
    "platform.webhook.base-url=http://localhost",
    "r2.access-key=dummy",
    "r2.bucket-name=dummy",
    "r2.endpoint=https://dummy.com",
    "r2.public-url=dummy",
    "r2.secret-key=dummy",
    "razorpay.key.id=dummy",
    "razorpay.key.secret=dummy",
    "razorpay.webhook.secret=dummy",
    "spring.kafka.consumer.group-id=test",
    "vyapargateway.api.key=dummy",
    "vyapargateway.webhook.secret=dummy"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureWebTestClient
public class OpenApiGenerationTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.notification.service.AuditLogService auditLogService;


    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.notification.repository.UserDeviceRepository userDeviceRepository;

    // AlertmanagerWebhookController's collaborators: the scan below reaches only the controller package.
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.notification.config.OperatorAlertProperties operatorAlertProperties;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.notification.service.OperatorAlertFormatter operatorAlertFormatter;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.notification.service.OperatorAlertPublisher operatorAlertPublisher;

    @org.springframework.context.annotation.Configuration
    // Relabels structured responses from */* to application/json. Without it every
    // generated Zod response validator degrades to z.void(); the scoped scan below
    // does not reach com.fooddelivery.common.config.
    @org.springframework.context.annotation.Import({com.fooddelivery.common.config.OpenApiJsonMediaTypeCustomizer.class, com.fooddelivery.common.config.OpenApiPaginationRequiredCustomizer.class})
    @org.springframework.context.annotation.ComponentScan(basePackages = {"com.fooddelivery.notification.controller"})
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(excludeName = {"org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration", "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration", "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration", "org.springframework.boot.actuate.autoconfigure.security.reactive.ManagementReactiveSecurityAutoConfiguration", "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"})
    static class TestApp {
    }


    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean(name = "kafkaTemplate")

    private org.springframework.kafka.core.KafkaTemplate kafkaTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;


    // Mock critical infrastructure so the context loads
    
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;
    @MockBean
    private com.fooddelivery.common.service.RateLimitingService rateLimitingService;
    @MockBean
    private com.fooddelivery.notification.config.NotificationTemplateSeeder notificationTemplateSeeder;

    @Autowired(required = false)
    private MockMvc mockMvc;

    @Autowired(required = false)
    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext context;

    @Test
    public void generateOpenApi() throws Exception {
        String openApiJson = null;

        // Try MockMvc first (for WebMVC)
        if (mockMvc != null) {
            openApiJson = mockMvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } else if (webTestClient != null) {
            // Try WebTestClient (for WebFlux)
            byte[] responseBody = webTestClient.get().uri("/v3/api-docs").exchange()
                    .expectStatus().isOk()
                    .expectBody().returnResult().getResponseBody();
            if (responseBody != null) {
                openApiJson = new String(responseBody, StandardCharsets.UTF_8);
            }
        } else {
            throw new IllegalStateException("Neither MockMvc nor WebTestClient is available.");
        }

        if (openApiJson != null && !openApiJson.isEmpty()) {
            Path path = Paths.get("target/openapi.json");
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, openApiJson.getBytes(StandardCharsets.UTF_8));
            assertCommittedSpecCurrent(openApiJson);
            System.out.println("OpenAPI spec written to target/openapi.json");
        } else {
            throw new IllegalStateException("Failed to retrieve OpenAPI spec.");
        }
    }

    // openapi.json at the module root is what the UI client and the validators are generated from. Red here
    // when the controllers no longer produce it, so it cannot go stale unnoticed. Parsed trees are compared
    // because springdoc's key order is not stable between runs.
    private static void assertCommittedSpecCurrent(String openApiJson) throws java.io.IOException {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode generated = mapper.readTree(openApiJson);
        com.fasterxml.jackson.databind.JsonNode committed = mapper.readTree(
                java.nio.file.Files.readString(java.nio.file.Paths.get("openapi.json")));
        if (generated.equals(committed)) {
            return;
        }
        java.util.List<String> changed = new java.util.ArrayList<>();
        for (String section : new String[] {"/paths", "/components/schemas"}) {
            java.util.Set<String> names = new java.util.TreeSet<>();
            generated.at(section).fieldNames().forEachRemaining(names::add);
            committed.at(section).fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (!generated.at(section).path(name).equals(committed.at(section).path(name))) {
                    changed.add(section + "/" + name);
                }
            }
        }
        org.junit.jupiter.api.Assertions.fail("openapi.json is stale (differs at "
                + (changed.isEmpty() ? "info/servers/tags" : changed) + "). Copy target/openapi.json over it;"
                + " for a UI service FoodDeliveryAppUI/scripts/generate-api-types.sh does that and regenerates the client.");
    }
}
