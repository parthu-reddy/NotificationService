-- A legitimate signup OTP precedes creation of its user. Keep that identity absent,
-- rather than inventing an account UUID. The router restricts this to addressed OTPs.
ALTER TABLE notification_audit_logs ALTER COLUMN user_id DROP NOT NULL;
