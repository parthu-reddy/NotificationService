package com.fooddelivery.notification.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Alertmanager's webhook body (version "4"): {@code template.Data} plus {@code version}, {@code groupKey} and
 * {@code truncatedAlerts}, as {@code notify/webhook/webhook.go} and {@code template/template.go} define it. Times stay
 * strings: they are only shown, and a firing alert's {@code endsAt} is Go's zero time.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertmanagerWebhook(
        String version,
        String status,
        String receiver,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        Map<String, String> commonAnnotations,
        String externalURL,
        List<Alert> alerts) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alert(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            String startsAt,
            String endsAt,
            String fingerprint) {
    }
}
