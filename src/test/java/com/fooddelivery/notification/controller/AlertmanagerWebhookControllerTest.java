package com.fooddelivery.notification.controller;

import com.fooddelivery.notification.service.OperatorAlertFormatter;
import com.fooddelivery.notification.service.OperatorAlertPublisher;
import com.fooddelivery.notification.service.OperatorAlertSender;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fooddelivery.common.test.BaseIntegrationTest;
import com.fooddelivery.notification.service.NotificationRouterService;
import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * POST /webhooks/alertmanager on a real port, through every servlet filter and the security chain, in the profile Dev
 * runs. The publisher is the only mock (a service bean, not a filter); the record it writes is checked against the
 * real consumer in OperatorAlertRoundTripTest.
 */
@org.springframework.boot.test.context.SpringBootTest(classes = com.fooddelivery.notification.NotificationServiceApplication.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.redis.enabled=false",
        "brevo.api.key=dummy",
        "exotel.account.sid=dummy-sid",
        "exotel.api.key=dummy",
        "exotel.api.token=dummy-token",
        "gupshup.api.key=dummy",
        "gupshup.source.number=910000000000",
        "platform.webhook.base-url=http://localhost",
        "platform.webhook.secret=dummy-webhook-secret",
        "platform.operator-alerts.webhook-token=" + AlertmanagerWebhookControllerTest.TOKEN,
        "platform.operator-alerts.recipient=operator@example.test",
        "platform.operator-alerts.sender=alerts@example.test",
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.main.web-application-type=servlet"
})
@org.springframework.test.context.ActiveProfiles("dev")
class AlertmanagerWebhookControllerTest extends BaseIntegrationTest {

    static final String TOKEN = "test-operator-alert-token-0123456789abcdef";

    @LocalServerPort
    private int port;

    @MockBean
    private OperatorAlertPublisher publisher;

    @Autowired
    private NotificationRouterService router;

    @Autowired
    private OperatorAlertSender sender;

    private static String sample() throws Exception {
        try (var in = AlertmanagerWebhookControllerTest.class.getResourceAsStream("/alertmanager/webhook-v4-firing.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private HttpResponse<String> post(String authorization, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/webhooks/alertmanager"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) request.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void withoutTheTokenNothingIsPublished() throws Exception {
        assertEquals(401, post(null, sample()).statusCode());
        verifyNoInteractions(publisher);
    }

    @Test
    void aWrongTokenIsRefused() throws Exception {
        assertEquals(401, post("Bearer " + TOKEN + "x", sample()).statusCode());
        assertEquals(401, post(TOKEN, sample()).statusCode());
        verifyNoInteractions(publisher);
    }

    @Test
    void aNonV4BodyIsRefusedWithoutRetry() throws Exception {
        assertEquals(400, post("Bearer " + TOKEN, sample().replace("\"version\": \"4\"", "\"version\": \"3\"")).statusCode());
        verifyNoInteractions(publisher);
    }

    @Test
    void theRecordedAlertIsQueued() throws Exception {
        when(publisher.publish(any())).thenReturn("event-1");
        assertEquals(202, post("Bearer " + TOKEN, sample()).statusCode());
        var alert = ArgumentCaptor.forClass(OperatorAlertFormatter.OperatorAlert.class);
        verify(publisher).publish(alert.capture());
        assertEquals("[FIRING:1] PartnerApplicationReviewOverdue", alert.getValue().subject());
    }

    @Test
    void aPublishKafkaDidNotAcknowledgeAsksAlertmanagerToRetry() throws Exception {
        when(publisher.publish(any())).thenThrow(new OperatorAlertPublisher.PublishFailed(new java.util.concurrent.TimeoutException()));
        assertEquals(503, post("Bearer " + TOKEN, sample()).statusCode());
    }

    @Test
    void customerChannelsStayMockedOnDevWhileOperatorAlertsAreReal() {
        @SuppressWarnings("unchecked")
        var strategies = (Map<?, NotificationChannelStrategy>) ReflectionTestUtils.getField(router, "strategyMap");
        assertEquals(4, strategies.size());
        strategies.values().forEach(s -> assertTrue(s.getClass().getName().contains("DevNotificationChannelConfiguration"),
                s.getClass().getName() + " is a real channel on Dev"));
        assertSame(sender, ReflectionTestUtils.getField(router, "operatorAlerts"));
    }
}
