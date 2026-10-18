package com.fooddelivery.notification.service;

import com.fooddelivery.notification.dto.AlertmanagerWebhook;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperatorAlertFormatterTest {

    private final OperatorAlertFormatter formatter = new OperatorAlertFormatter();

    private static AlertmanagerWebhook sample() throws Exception {
        try (var in = OperatorAlertFormatterTest.class.getResourceAsStream("/alertmanager/webhook-v4-firing.json")) {
            return new ObjectMapper().readValue(in, AlertmanagerWebhook.class);
        }
    }

    private static AlertmanagerWebhook.Alert alert(String status, Map<String, String> labels, Map<String, String> annotations) {
        return new AlertmanagerWebhook.Alert(status, labels, annotations, "2026-10-18T04:46:30Z", "2026-10-18T05:46:30Z", "f");
    }

    @Test
    void theRecordedFiringNotificationBecomesOneSubjectAndBody() throws Exception {
        var mail = formatter.format(sample());
        assertEquals("[FIRING:1] PartnerApplicationReviewOverdue", mail.subject());
        assertTrue(mail.html().contains("Partner applications waiting for review"), mail.html());
        assertTrue(mail.html().contains("service = restaurant-service"), mail.html());
        assertTrue(mail.html().contains("<a href=\"https://github.com/parthu-reddy/Deployment/blob/main/runbooks/"), mail.html());
    }

    @Test
    void aResolvedGroupSaysResolvedAndShowsWhenItEnded() {
        var a = alert("resolved", Map.of("alertname", "MoneyDlqDepth"), Map.of());
        var mail = formatter.format(new AlertmanagerWebhook("4", "resolved", "r", Map.of("alertname", "MoneyDlqDepth"),
                Map.of(), Map.of(), "", List.of(a)));
        assertEquals("[RESOLVED] MoneyDlqDepth", mail.subject());
        assertTrue(mail.html().contains("resolved 2026-10-18T05:46:30Z"), mail.html());
    }

    @Test
    void everyLabelAndAnnotationIsEscaped() {
        String attack = "<script>alert(1)</script>";
        var a = alert("firing", Map.of("alertname", attack, attack, attack),
                Map.of("summary", attack, "description", attack, "runbook_url", attack));
        String html = formatter.format(new AlertmanagerWebhook("4", "firing", "r", Map.of(), Map.of(), Map.of(), "",
                List.of(a))).html();
        assertFalse(html.contains("<script>"), html);
        assertTrue(html.contains("&lt;script&gt;"), html);
    }

    @Test
    void onlyRepositoryRunbooksBecomeLinks() {
        var a = alert("firing", Map.of("alertname", "X"), Map.of("runbook_url", "https://evil.example/phish"));
        String html = formatter.format(new AlertmanagerWebhook("4", "firing", "r", Map.of(), Map.of(), Map.of(), "",
                List.of(a))).html();
        assertFalse(html.contains("<a "), html);
        assertTrue(html.contains("Runbook: https://evil.example/phish"), html);
    }

    @Test
    void severalFiringAlertsAreCountedInTheSubject() {
        var a = alert("firing", Map.of("alertname", "X"), Map.of());
        var mail = formatter.format(new AlertmanagerWebhook("4", "firing", "r", Map.of("alertname", "X"), Map.of(),
                Map.of(), "", List.of(a, a, alert("resolved", Map.of("alertname", "X"), Map.of()))));
        assertEquals("[FIRING:2] X", mail.subject());
    }
}
