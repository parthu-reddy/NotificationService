package com.fooddelivery.notification.domain;

import com.fooddelivery.common.enums.ChannelType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_audit_logs", indexes = {@Index(name = "idx_provider_msg_id", columnList = "provider_message_id")})
public class NotificationAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    @Column(name = "event_id", unique = true)
    private String eventId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType channel;
    @Column(name = "recipient_address", nullable = false)
    private String recipientAddress;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private NotificationTemplate template;
    @Column(name = "provider_message_id", unique = true)
    private String providerMessageId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.QUEUED;
    @Column(name = "error_reason", columnDefinition = "TEXT")
    private String errorReason;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @java.lang.SuppressWarnings("all")
    public NotificationAuditLog() {
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public String getEventId() {
        return this.eventId;
    }

    @java.lang.SuppressWarnings("all")
    public UUID getUserId() {
        return this.userId;
    }

    @java.lang.SuppressWarnings("all")
    public ChannelType getChannel() {
        return this.channel;
    }

    @java.lang.SuppressWarnings("all")
    public String getRecipientAddress() {
        return this.recipientAddress;
    }

    @java.lang.SuppressWarnings("all")
    public NotificationTemplate getTemplate() {
        return this.template;
    }

    @java.lang.SuppressWarnings("all")
    public String getProviderMessageId() {
        return this.providerMessageId;
    }

    @java.lang.SuppressWarnings("all")
    public DeliveryStatus getStatus() {
        return this.status;
    }

    @java.lang.SuppressWarnings("all")
    public String getErrorReason() {
        return this.errorReason;
    }

    @java.lang.SuppressWarnings("all")
    public Instant getCreatedAt() {
        return this.createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setEventId(final String eventId) {
        this.eventId = eventId;
    }

    @java.lang.SuppressWarnings("all")
    public void setUserId(final UUID userId) {
        this.userId = userId;
    }

    @java.lang.SuppressWarnings("all")
    public void setChannel(final ChannelType channel) {
        this.channel = channel;
    }

    @java.lang.SuppressWarnings("all")
    public void setRecipientAddress(final String recipientAddress) {
        this.recipientAddress = recipientAddress;
    }

    @java.lang.SuppressWarnings("all")
    public void setTemplate(final NotificationTemplate template) {
        this.template = template;
    }

    @java.lang.SuppressWarnings("all")
    public void setProviderMessageId(final String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    @java.lang.SuppressWarnings("all")
    public void setStatus(final DeliveryStatus status) {
        this.status = status;
    }

    @java.lang.SuppressWarnings("all")
    public void setErrorReason(final String errorReason) {
        this.errorReason = errorReason;
    }

    @java.lang.SuppressWarnings("all")
    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof NotificationAuditLog)) return false;
        final NotificationAuditLog other = (NotificationAuditLog) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$eventId = this.getEventId();
        final java.lang.Object other$eventId = other.getEventId();
        if (this$eventId == null ? other$eventId != null : !this$eventId.equals(other$eventId)) return false;
        final java.lang.Object this$userId = this.getUserId();
        final java.lang.Object other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
        final java.lang.Object this$channel = this.getChannel();
        final java.lang.Object other$channel = other.getChannel();
        if (this$channel == null ? other$channel != null : !this$channel.equals(other$channel)) return false;
        final java.lang.Object this$recipientAddress = this.getRecipientAddress();
        final java.lang.Object other$recipientAddress = other.getRecipientAddress();
        if (this$recipientAddress == null ? other$recipientAddress != null : !this$recipientAddress.equals(other$recipientAddress)) return false;
        final java.lang.Object this$template = this.getTemplate();
        final java.lang.Object other$template = other.getTemplate();
        if (this$template == null ? other$template != null : !this$template.equals(other$template)) return false;
        final java.lang.Object this$providerMessageId = this.getProviderMessageId();
        final java.lang.Object other$providerMessageId = other.getProviderMessageId();
        if (this$providerMessageId == null ? other$providerMessageId != null : !this$providerMessageId.equals(other$providerMessageId)) return false;
        final java.lang.Object this$status = this.getStatus();
        final java.lang.Object other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) return false;
        final java.lang.Object this$errorReason = this.getErrorReason();
        final java.lang.Object other$errorReason = other.getErrorReason();
        if (this$errorReason == null ? other$errorReason != null : !this$errorReason.equals(other$errorReason)) return false;
        final java.lang.Object this$createdAt = this.getCreatedAt();
        final java.lang.Object other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
        final java.lang.Object this$updatedAt = this.getUpdatedAt();
        final java.lang.Object other$updatedAt = other.getUpdatedAt();
        if (this$updatedAt == null ? other$updatedAt != null : !this$updatedAt.equals(other$updatedAt)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof NotificationAuditLog;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $eventId = this.getEventId();
        result = result * PRIME + ($eventId == null ? 43 : $eventId.hashCode());
        final java.lang.Object $userId = this.getUserId();
        result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
        final java.lang.Object $channel = this.getChannel();
        result = result * PRIME + ($channel == null ? 43 : $channel.hashCode());
        final java.lang.Object $recipientAddress = this.getRecipientAddress();
        result = result * PRIME + ($recipientAddress == null ? 43 : $recipientAddress.hashCode());
        final java.lang.Object $template = this.getTemplate();
        result = result * PRIME + ($template == null ? 43 : $template.hashCode());
        final java.lang.Object $providerMessageId = this.getProviderMessageId();
        result = result * PRIME + ($providerMessageId == null ? 43 : $providerMessageId.hashCode());
        final java.lang.Object $status = this.getStatus();
        result = result * PRIME + ($status == null ? 43 : $status.hashCode());
        final java.lang.Object $errorReason = this.getErrorReason();
        result = result * PRIME + ($errorReason == null ? 43 : $errorReason.hashCode());
        final java.lang.Object $createdAt = this.getCreatedAt();
        result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
        final java.lang.Object $updatedAt = this.getUpdatedAt();
        result = result * PRIME + ($updatedAt == null ? 43 : $updatedAt.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "NotificationAuditLog(id=" + this.getId() + ", eventId=" + this.getEventId() + ", userId=" + this.getUserId() + ", channel=" + this.getChannel() + ", recipientAddress=" + this.getRecipientAddress() + ", template=" + this.getTemplate() + ", providerMessageId=" + this.getProviderMessageId() + ", status=" + this.getStatus() + ", errorReason=" + this.getErrorReason() + ", createdAt=" + this.getCreatedAt() + ", updatedAt=" + this.getUpdatedAt() + ")";
    }
}
