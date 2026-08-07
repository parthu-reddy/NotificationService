package com.fooddelivery.notification.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_devices", indexes = {@Index(name = "idx_user_devices_user_id", columnList = "user_id")})
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

    @java.lang.SuppressWarnings("all")
    public UserDevice() {
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getUserId() {
        return this.userId;
    }

    @java.lang.SuppressWarnings("all")
    public String getFcmToken() {
        return this.fcmToken;
    }

    @java.lang.SuppressWarnings("all")
    public String getPlatform() {
        return this.platform;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getIsActive() {
        return this.isActive;
    }

    @java.lang.SuppressWarnings("all")
    public OffsetDateTime getLastUpdatedAt() {
        return this.lastUpdatedAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setUserId(final UUID userId) {
        this.userId = userId;
    }

    @java.lang.SuppressWarnings("all")
    public void setFcmToken(final String fcmToken) {
        this.fcmToken = fcmToken;
    }

    @java.lang.SuppressWarnings("all")
    public void setPlatform(final String platform) {
        this.platform = platform;
    }

    @java.lang.SuppressWarnings("all")
    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    @java.lang.SuppressWarnings("all")
    public void setLastUpdatedAt(final OffsetDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof UserDevice)) return false;
        final UserDevice other = (UserDevice) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$isActive = this.getIsActive();
        final java.lang.Object other$isActive = other.getIsActive();
        if (this$isActive == null ? other$isActive != null : !this$isActive.equals(other$isActive)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$userId = this.getUserId();
        final java.lang.Object other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
        final java.lang.Object this$fcmToken = this.getFcmToken();
        final java.lang.Object other$fcmToken = other.getFcmToken();
        if (this$fcmToken == null ? other$fcmToken != null : !this$fcmToken.equals(other$fcmToken)) return false;
        final java.lang.Object this$platform = this.getPlatform();
        final java.lang.Object other$platform = other.getPlatform();
        if (this$platform == null ? other$platform != null : !this$platform.equals(other$platform)) return false;
        final java.lang.Object this$lastUpdatedAt = this.getLastUpdatedAt();
        final java.lang.Object other$lastUpdatedAt = other.getLastUpdatedAt();
        if (this$lastUpdatedAt == null ? other$lastUpdatedAt != null : !this$lastUpdatedAt.equals(other$lastUpdatedAt)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof UserDevice;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $isActive = this.getIsActive();
        result = result * PRIME + ($isActive == null ? 43 : $isActive.hashCode());
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $userId = this.getUserId();
        result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
        final java.lang.Object $fcmToken = this.getFcmToken();
        result = result * PRIME + ($fcmToken == null ? 43 : $fcmToken.hashCode());
        final java.lang.Object $platform = this.getPlatform();
        result = result * PRIME + ($platform == null ? 43 : $platform.hashCode());
        final java.lang.Object $lastUpdatedAt = this.getLastUpdatedAt();
        result = result * PRIME + ($lastUpdatedAt == null ? 43 : $lastUpdatedAt.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "UserDevice(id=" + this.getId() + ", userId=" + this.getUserId() + ", fcmToken=" + this.getFcmToken() + ", platform=" + this.getPlatform() + ", isActive=" + this.getIsActive() + ", lastUpdatedAt=" + this.getLastUpdatedAt() + ")";
    }
}
