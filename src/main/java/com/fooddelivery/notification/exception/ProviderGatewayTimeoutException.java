package com.fooddelivery.notification.exception;

/**
 * Thrown when a provider experiences a network timeout, 504 Gateway Timeout, etc.
 * This is a TRANSIENT error and WILL be retried by the Kafka consumer.
 */
public class ProviderGatewayTimeoutException extends RuntimeException {
    public ProviderGatewayTimeoutException(String message) {
        super(message);
    }
    
    public ProviderGatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
