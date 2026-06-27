package com.fooddelivery.notification.exception;

public class RecipientUnreachableException extends TerminalNotificationException {
    public RecipientUnreachableException(String message) {
        super(message);
    }
}
