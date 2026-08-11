package com.fooddelivery.notification.exception;

/**
 * Base exception for all permanent (terminal) failures.
 * Any exception extending this class will not be retried by the Kafka consumer
 * and will be routed directly to the Dead Letter Topic.
 */
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a notification fails due to an unrecoverable error 
 * (e.g. invalid phone number, user opted out). These should not be retried.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TerminalNotificationException extends RuntimeException {
    public TerminalNotificationException(String message) {
        super(message);
    }
    
    public TerminalNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
