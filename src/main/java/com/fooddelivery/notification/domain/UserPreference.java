package com.fooddelivery.notification.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
    @Column(name = "sms_enabled")
    private Boolean smsEnabled = true;
    @Column(name = "email_enabled")
    private Boolean emailEnabled = true;
    @Column(name = "push_enabled")
    private Boolean pushEnabled = true;
    @Column(name = "whatsapp_enabled")
    private Boolean whatsappEnabled = true;

    @java.lang.SuppressWarnings("all")
    public UserPreference() {
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
    public Boolean getSmsEnabled() {
        return this.smsEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getEmailEnabled() {
        return this.emailEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getPushEnabled() {
        return this.pushEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public Boolean getWhatsappEnabled() {
        return this.whatsappEnabled;
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
    public void setSmsEnabled(final Boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public void setEmailEnabled(final Boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public void setPushEnabled(final Boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    @java.lang.SuppressWarnings("all")
    public void setWhatsappEnabled(final Boolean whatsappEnabled) {
        this.whatsappEnabled = whatsappEnabled;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof UserPreference)) return false;
        final UserPreference other = (UserPreference) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        final java.lang.Object this$smsEnabled = this.getSmsEnabled();
        final java.lang.Object other$smsEnabled = other.getSmsEnabled();
        if (this$smsEnabled == null ? other$smsEnabled != null : !this$smsEnabled.equals(other$smsEnabled)) return false;
        final java.lang.Object this$emailEnabled = this.getEmailEnabled();
        final java.lang.Object other$emailEnabled = other.getEmailEnabled();
        if (this$emailEnabled == null ? other$emailEnabled != null : !this$emailEnabled.equals(other$emailEnabled)) return false;
        final java.lang.Object this$pushEnabled = this.getPushEnabled();
        final java.lang.Object other$pushEnabled = other.getPushEnabled();
        if (this$pushEnabled == null ? other$pushEnabled != null : !this$pushEnabled.equals(other$pushEnabled)) return false;
        final java.lang.Object this$whatsappEnabled = this.getWhatsappEnabled();
        final java.lang.Object other$whatsappEnabled = other.getWhatsappEnabled();
        if (this$whatsappEnabled == null ? other$whatsappEnabled != null : !this$whatsappEnabled.equals(other$whatsappEnabled)) return false;
        final java.lang.Object this$id = this.getId();
        final java.lang.Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final java.lang.Object this$userId = this.getUserId();
        final java.lang.Object other$userId = other.getUserId();
        if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof UserPreference;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final java.lang.Object $smsEnabled = this.getSmsEnabled();
        result = result * PRIME + ($smsEnabled == null ? 43 : $smsEnabled.hashCode());
        final java.lang.Object $emailEnabled = this.getEmailEnabled();
        result = result * PRIME + ($emailEnabled == null ? 43 : $emailEnabled.hashCode());
        final java.lang.Object $pushEnabled = this.getPushEnabled();
        result = result * PRIME + ($pushEnabled == null ? 43 : $pushEnabled.hashCode());
        final java.lang.Object $whatsappEnabled = this.getWhatsappEnabled();
        result = result * PRIME + ($whatsappEnabled == null ? 43 : $whatsappEnabled.hashCode());
        final java.lang.Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final java.lang.Object $userId = this.getUserId();
        result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "UserPreference(id=" + this.getId() + ", userId=" + this.getUserId() + ", smsEnabled=" + this.getSmsEnabled() + ", emailEnabled=" + this.getEmailEnabled() + ", pushEnabled=" + this.getPushEnabled() + ", whatsappEnabled=" + this.getWhatsappEnabled() + ")";
    }
}
