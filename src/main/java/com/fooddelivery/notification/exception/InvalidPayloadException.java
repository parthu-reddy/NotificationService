package com.fooddelivery.notification.exception;

public class InvalidPayloadException extends TerminalNotificationException {
    public InvalidPayloadException(String message) {
        super(message);
    }
}
