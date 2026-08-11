package com.fooddelivery.notification.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class NotificationDispatchException extends RuntimeException {
    public NotificationDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
