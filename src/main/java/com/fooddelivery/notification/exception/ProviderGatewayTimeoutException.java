package com.fooddelivery.notification.exception;

/**
 * Thrown when a provider experiences a network timeout, 504 Gateway Timeout, etc.
 * This is a TRANSIENT error and WILL be retried by the Kafka consumer.
 */
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a 3rd party notification provider (Twilio, SES) times out.
 * This is a transient error and should trigger a failover or retry.
 */
@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class ProviderGatewayTimeoutException extends RuntimeException {
    public ProviderGatewayTimeoutException(String message) {
        super(message);
    }
    
    public ProviderGatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
