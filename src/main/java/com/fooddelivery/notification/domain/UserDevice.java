package com.fooddelivery.notification.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "user_devices", indexes = {
        @Index(name = "idx_user_devices_user_id", columnList = "user_id")
})
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "fcm_token", nullable = false, unique = true)
    private String fcmToken;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_updated_at")
    private OffsetDateTime lastUpdatedAt;
}
