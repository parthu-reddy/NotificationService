package com.fooddelivery.notification.service;

import com.fooddelivery.notification.dto.AlertmanagerWebhook;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Turns one Alertmanager notification into the operator's email. Label and annotation values are written by whoever
 * writes alert rules and by the labels of the metrics they query, so every one is HTML-escaped here:
 * {@code OperatorAlertSender} inserts the body into the template unescaped (it does not use {@code hydrateTemplate}).
 */
@Component
public class OperatorAlertFormatter {

    /** Runbook links are emitted only for this repository host; anything else is shown as text. */
    static final String RUNBOOK_PREFIX = "https://github.com/parthu-reddy/";

    /** The email the publisher puts on the notification topic: a plain-text subject and an escaped HTML body. */
    public record OperatorAlert(String subject, String html) {
    }

    public OperatorAlert format(AlertmanagerWebhook webhook) {
        return new OperatorAlert(subject(webhook), body(webhook));
    }

    /** Plain text (an email subject, not HTML), so not escaped. */
    String subject(AlertmanagerWebhook webhook) {
        String name = webhook.groupLabels() != null && webhook.groupLabels().get("alertname") != null
                ? webhook.groupLabels().get("alertname")
                : webhook.commonLabels() != null && webhook.commonLabels().get("alertname") != null
                        ? webhook.commonLabels().get("alertname")
                        : "alerts";
        long firing = webhook.alerts().stream().filter(a -> "firing".equals(a.status())).count();
        return "firing".equals(webhook.status()) ? "[FIRING:" + firing + "] " + name : "[RESOLVED] " + name;
    }

    String body(AlertmanagerWebhook webhook) {
        StringBuilder html = new StringBuilder();
        for (AlertmanagerWebhook.Alert alert : webhook.alerts()) {
            html.append("<h3>").append(HtmlUtils.htmlEscape(text(alert.status()).toUpperCase()))
                    .append(": ").append(value(alert.labels(), "alertname")).append("</h3>\n");
            html.append("<p>").append(value(alert.annotations(), "summary")).append("</p>\n");
            html.append("<p>").append(value(alert.annotations(), "description")).append("</p>\n");
            html.append("<ul>\n");
            for (Map.Entry<String, String> label : new TreeMap<>(alert.labels() == null ? Map.<String, String>of() : alert.labels()).entrySet())
                html.append("<li>").append(HtmlUtils.htmlEscape(label.getKey())).append(" = ")
                        .append(HtmlUtils.htmlEscape(label.getValue())).append("</li>\n");
            html.append("</ul>\n");
            html.append("<p>Started ").append(HtmlUtils.htmlEscape(text(alert.startsAt())));
            if ("resolved".equals(alert.status()))
                html.append(", resolved ").append(HtmlUtils.htmlEscape(text(alert.endsAt())));
            html.append("</p>\n");
            String runbook = value(alert.annotations(), "runbook_url");
            if (runbook.startsWith(RUNBOOK_PREFIX))
                html.append("<p><a href=\"").append(runbook).append("\">Runbook</a></p>\n");
            else if (!runbook.isEmpty())
                html.append("<p>Runbook: ").append(runbook).append("</p>\n");
        }
        return html.toString();
    }

    /** One map value, escaped; empty when the map or the key is absent. */
    private static String value(Map<String, String> map, String key) {
        return HtmlUtils.htmlEscape(map == null ? "" : map.getOrDefault(key, ""));
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }
}
