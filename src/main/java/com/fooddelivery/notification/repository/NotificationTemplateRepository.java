package com.fooddelivery.notification.repository;

import com.fooddelivery.notification.domain.ChannelType;
import com.fooddelivery.notification.domain.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    Optional<NotificationTemplate> findByEventNameAndChannelAndIsActiveTrue(String eventName, ChannelType channel);
}
