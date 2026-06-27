package com.fooddelivery.notification.exception;

public class UserOptedOutException extends TerminalNotificationException {
    public UserOptedOutException(String message) {
        super(message);
    }
}
