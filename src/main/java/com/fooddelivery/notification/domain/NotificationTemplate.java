package com.fooddelivery.notification.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "notification_templates", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_name", "channel"})
})
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType channel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "external_template_id", length = 100)
    private String externalTemplateId;

    @Column(name = "external_entity_id", length = 100)
    private String externalEntityId;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
