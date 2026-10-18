package com.fooddelivery.notification.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where Prometheus alerts go, and how Alertmanager proves it is Alertmanager. All three come from OCI Vault through
 * {@code Deployment/notification-service.yml}; startup fails without them in every profile, because operator alerts
 * send for real even on Dev (GatewayScrapeAndAlerting_2026-10-18, owner decision D-4).
 *
 * @param webhookToken the bearer token Alertmanager sends on {@code POST /webhooks/alertmanager}
 * @param recipient    the only address an {@code OPERATOR_ALERT} may be sent to
 * @param sender       the Brevo-verified sender address
 */
@ConfigurationProperties("platform.operator-alerts")
@Validated
public record OperatorAlertProperties(
        @NotBlank @Size(min = 32) String webhookToken,
        @NotBlank @Email String recipient,
        @NotBlank @Email String sender) {
}
