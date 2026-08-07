package com.fooddelivery.notification.domain;

import com.fooddelivery.common.enums.ChannelType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_templates", uniqueConstraints = {@UniqueConstraint(columnNames = {"event_name", "channel"})})
public class NotificationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
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

    @java.lang.SuppressWarnings("all")
    public NotificationTemplate() {
    }

    @java.lang.SuppressWarnings("all")
    public UUID getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public String getEventName() {
        return this.eventName;
    }

    @java.lang.SuppressWarnings("all")
    public ChannelType getChannel() {
        return this.channel;
    }

    @java.lang.SuppressWarnings("all")
    public String getContent() {
        return this.content;
    }

    @java.lang.SuppressWarnings("all")
    public String getExternalTemplateId() {
        return this.externalTemplateId;
    }

    @java.lang.SuppressWarnings("all")
    public String getExternalEntityId() {
        return this.externalEntityId;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getIsActive() {
        return this.isActive;
    }

    @java.lang.SuppressWarnings("all")
    public OffsetDateTime getCreatedAt() {
        return this.createdAt;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final UUID id) {
        this.id = id;
    }

    @java.lang.SuppressWarnings("all")
    public void setEventName(final String eventName) {
        this.eventName = eventName;
    }

    @java.lang.SuppressWarnings("all")
    public void setChannel(final ChannelType channel) {
        this.channel = channel;
    }

    @java.lang.SuppressWarnings("all")
    public void setContent(final String content) {
        this.content = content;
    }

    @java.lang.SuppressWarnings("all")
    public void setExternalTemplateId(final String externalTemplateId) {
        this.externalTemplateId = externalTemplateId;
    }

    @java.lang.SuppressWarnings("all")
    public void setExternalEntityId(final String externalEntityId) {
        this.externalEntityId = externalEntityId;
    }

    @java.lang.SuppressWarnings("all")
    public void setIsActive(final Boolean isActive) {
        this.isActive = isActive;
    }

    @java.lang.SuppressWarnings("all")
    public void setCreatedAt(final OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof NotificationTemplate)) return false;
        final NotificationTemplate other = (NotificationTemplate) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$isActive = this.getIsActive();
        final java.lang.Object other$isActive = other.getIsActive();
        if (this$isActive == null ? other$isActive != null : !this$isActive.equals(other$isActive)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$eventName = this.getEventName();
        final java.lang.Object other$eventName = other.getEventName();
        if (this$eventName == null ? other$eventName != null : !this$eventName.equals(other$eventName)) return false;
        final java.lang.Object this$channel = this.getChannel();
        final java.lang.Object other$channel = other.getChannel();
        if (this$channel == null ? other$channel != null : !this$channel.equals(other$channel)) return false;
        final java.lang.Object this$content = this.getContent();
        final java.lang.Object other$content = other.getContent();
        if (this$content == null ? other$content != null : !this$content.equals(other$content)) return false;
        final java.lang.Object this$externalTemplateId = this.getExternalTemplateId();
        final java.lang.Object other$externalTemplateId = other.getExternalTemplateId();
        if (this$externalTemplateId == null ? other$externalTemplateId != null : !this$externalTemplateId.equals(other$externalTemplateId)) return false;
        final java.lang.Object this$externalEntityId = this.getExternalEntityId();
        final java.lang.Object other$externalEntityId = other.getExternalEntityId();
        if (this$externalEntityId == null ? other$externalEntityId != null : !this$externalEntityId.equals(other$externalEntityId)) return false;
        final java.lang.Object this$createdAt = this.getCreatedAt();
        final java.lang.Object other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !this$createdAt.equals(other$createdAt)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof NotificationTemplate;
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
        final java.lang.Object $eventName = this.getEventName();
        result = result * PRIME + ($eventName == null ? 43 : $eventName.hashCode());
        final java.lang.Object $channel = this.getChannel();
        result = result * PRIME + ($channel == null ? 43 : $channel.hashCode());
        final java.lang.Object $content = this.getContent();
        result = result * PRIME + ($content == null ? 43 : $content.hashCode());
        final java.lang.Object $externalTemplateId = this.getExternalTemplateId();
        result = result * PRIME + ($externalTemplateId == null ? 43 : $externalTemplateId.hashCode());
        final java.lang.Object $externalEntityId = this.getExternalEntityId();
        result = result * PRIME + ($externalEntityId == null ? 43 : $externalEntityId.hashCode());
        final java.lang.Object $createdAt = this.getCreatedAt();
        result = result * PRIME + ($createdAt == null ? 43 : $createdAt.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "NotificationTemplate(id=" + this.getId() + ", eventName=" + this.getEventName() + ", channel=" + this.getChannel() + ", content=" + this.getContent() + ", externalTemplateId=" + this.getExternalTemplateId() + ", externalEntityId=" + this.getExternalEntityId() + ", isActive=" + this.getIsActive() + ", createdAt=" + this.getCreatedAt() + ")";
    }
}
