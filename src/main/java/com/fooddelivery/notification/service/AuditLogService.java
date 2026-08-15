package com.fooddelivery.notification.service;

import com.fooddelivery.notification.domain.DeliveryStatus;
import com.fooddelivery.notification.domain.NotificationAuditLog;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.extern.slf4j.Slf4j
public class AuditLogService {
    @java.lang.SuppressWarnings("all")

    private final NotificationAuditLogRepository auditLogRepository;

    public AuditLogService(NotificationAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void updateLogStatus(String providerMessageId, DeliveryStatus status, String detailedStatus) {
        auditLogRepository.findByProviderMessageId(providerMessageId).ifPresentOrElse(logEntry -> {
            logEntry.setStatus(status);
            if (detailedStatus != null && !detailedStatus.isEmpty()) {
                logEntry.setErrorReason(detailedStatus);
            }
            auditLogRepository.save(logEntry);
            log.info("Updated audit log for messageId {} to status {}", providerMessageId, status);
        }, () -> log.warn("Audit log not found for provider messageId: {}", providerMessageId));
    }
}
