package com.fooddelivery.notification.exception;

public class RateLimitExceededException extends TerminalNotificationException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
