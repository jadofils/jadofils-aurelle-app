package com.aurelle.messaging;

/** The mail server rejected or could not receive a message. Safe to retry with backoff. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
