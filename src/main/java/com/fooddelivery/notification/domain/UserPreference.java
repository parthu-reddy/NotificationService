package com.fooddelivery.notification.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
}
