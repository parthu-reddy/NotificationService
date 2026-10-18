package com.fooddelivery.notification.controller;

import com.fooddelivery.notification.dto.AlertmanagerWebhook;
import com.fooddelivery.notification.service.OperatorAlertFormatter;
import com.fooddelivery.notification.config.OperatorAlertProperties;
import com.fooddelivery.notification.service.OperatorAlertPublisher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alertmanager's webhook receiver. Reachable on the compose network only: the gateway routes no {@code /webhooks/**}
 * path but {@code /webhooks/providers/**} here, and communication-integration publishes on loopback. Anonymous in
 * {@code CommonSecurityConfig} like every webhook; the bearer token is the authentication.
 *
 * <p>Answers: 401 without the token, 400 for a body that is not an Alertmanager v4 notification (Alertmanager does not
 * retry 4xx), 503 when Kafka did not acknowledge (Alertmanager retries 5xx), 202 once the alert is on the topic.
 */
@RestController
@lombok.extern.slf4j.Slf4j
public class AlertmanagerWebhookController {

    private final OperatorAlertProperties properties;
    private final OperatorAlertFormatter formatter;
    private final OperatorAlertPublisher publisher;

    public AlertmanagerWebhookController(OperatorAlertProperties properties, OperatorAlertFormatter formatter,
                                         OperatorAlertPublisher publisher) {
        this.properties = properties;
        this.formatter = formatter;
        this.publisher = publisher;
    }

    @PostMapping(path = "/webhooks/alertmanager", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                        @RequestBody AlertmanagerWebhook webhook) {
        if (!authorized(authorization)) {
            log.warn("Alertmanager webhook refused: missing or wrong bearer token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"4".equals(webhook.version()) || webhook.alerts() == null || webhook.alerts().isEmpty()) {
            log.warn("Alertmanager webhook refused: version={} alerts={}", webhook.version(),
                    webhook.alerts() == null ? null : webhook.alerts().size());
            return ResponseEntity.badRequest().build();
        }
        OperatorAlertFormatter.OperatorAlert alert = formatter.format(webhook);
        try {
            String eventId = publisher.publish(alert);
            log.info("Operator alert queued: eventId={} subject={} alerts={}", eventId, alert.subject(), webhook.alerts().size());
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (OperatorAlertPublisher.PublishFailed e) {
            log.warn("Operator alert not queued, Alertmanager will retry: subject={} cause={}", alert.subject(), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /** Constant time: String.equals returns at the first differing character, which leaks the token's prefix. */
    private boolean authorized(String authorization) {
        byte[] expected = ("Bearer " + properties.webhookToken()).getBytes(StandardCharsets.UTF_8);
        return authorization != null
                && MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8), expected);
    }
}
