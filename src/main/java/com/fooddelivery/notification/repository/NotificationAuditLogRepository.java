package com.fooddelivery.notification.repository;

import com.fooddelivery.notification.domain.NotificationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationAuditLogRepository extends JpaRepository<NotificationAuditLog, UUID> {
    Optional<NotificationAuditLog> findByProviderMessageId(String providerMessageId);
}
