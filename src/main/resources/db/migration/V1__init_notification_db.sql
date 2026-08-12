CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    event_name VARCHAR(100) NOT NULL,
    channel VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    external_template_id VARCHAR(100),
    external_entity_id VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE
);
ALTER TABLE notification_templates ADD CONSTRAINT uk_notification_templates_event_name_channel UNIQUE (event_name, channel);

CREATE TABLE notification_audit_logs (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) UNIQUE,
    user_id UUID NOT NULL,
    channel VARCHAR(255) NOT NULL,
    recipient_address VARCHAR(255) NOT NULL,
    template_id UUID,
    provider_message_id VARCHAR(255) UNIQUE,
    status VARCHAR(255) NOT NULL,
    error_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (template_id) REFERENCES notification_templates(id)
);
CREATE INDEX idx_provider_msg_id ON notification_audit_logs(provider_message_id);

CREATE TABLE user_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    fcm_token VARCHAR(255) NOT NULL UNIQUE,
    platform VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    last_updated_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_user_devices_user_id ON user_devices(user_id);

CREATE TABLE user_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    sms_enabled BOOLEAN DEFAULT TRUE,
    email_enabled BOOLEAN DEFAULT TRUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    whatsapp_enabled BOOLEAN DEFAULT TRUE
);


