package com.fooddelivery.notification.exception;

/**
 * The provider refused our credentials (HTTP 401/403): a wrong, revoked or unpermitted API key, not a bad message.
 * Terminal like the other provider 4xx -- a retry seconds later sends the same key -- but its own type, so the log line
 * and the dead-letter reason say "credentials", not "payload".
 */
public class ProviderAuthenticationException extends TerminalNotificationException {
    public ProviderAuthenticationException(String message) {
        super(message);
    }
}
