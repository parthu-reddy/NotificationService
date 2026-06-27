package com.fooddelivery.notification.exception;

/**
 * Base exception for all permanent (terminal) failures.
 * Any exception extending this class will not be retried by the Kafka consumer
 * and will be routed directly to the Dead Letter Topic.
 */
public class TerminalNotificationException extends RuntimeException {
    public TerminalNotificationException(String message) {
        super(message);
    }
    
    public TerminalNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
