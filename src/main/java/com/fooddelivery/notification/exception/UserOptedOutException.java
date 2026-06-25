package com.fooddelivery.notification.exception;

public class UserOptedOutException extends RuntimeException {
    public UserOptedOutException(String message) {
        super(message);
    }
}
