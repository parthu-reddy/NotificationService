package com.fooddelivery.notification.controller;

import com.fooddelivery.notification.domain.DeliveryStatus;
import com.fooddelivery.notification.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/webhooks/providers")
public class ProviderWebhookController {

    @Value("${platform.webhook.secret}")
    private String webhookSecret;

    private final AuditLogService auditLogService;

    public ProviderWebhookController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Handles Exotel SMS Status Callbacks.
     * Exotel POSTs form-urlencoded data including SmsSid and Status.
     */
    @PostMapping(value = "/exotel/status", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Void> handleExotelCallback(
            @RequestParam Map<String, String> payload,
            @RequestParam(value = "token", required = false) String token) {
        String smsSid = payload.get("SmsSid");
        String status = payload.get("Status"); // Values: sent, delivered, failed, failed-dnd
        String detailedStatus = payload.get("DetailedStatus");

        log.debug("Received Exotel callback for SID: {} with status: {}", smsSid, status);

        if (token == null || !token.equals(webhookSecret)) {
            log.warn("Unauthorized webhook access attempt for SID: {}", smsSid);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (status == null) {
            return ResponseEntity.badRequest().build();
        }

        DeliveryStatus domainStatus = switch (status.toLowerCase()) {
            case "delivered", "sent" -> DeliveryStatus.DELIVERED;
            case "failed", "failed-dnd" -> DeliveryStatus.FAILED;
            default -> DeliveryStatus.QUEUED;
        };

        auditLogService.updateLogStatus(smsSid, domainStatus, detailedStatus);
        
        // Return 200 OK rapidly to acknowledge receipt
        return ResponseEntity.ok().build();
    }
}
