package com.aurelle.messaging;

/**
 * Port for sending e-mail (decision D28). The SMTP adapter is used when
 * {@code aurelle.mail.enabled=true}; otherwise messages are dropped and only metadata is logged.
 *
 * <p>Callers send after their transaction commits and never fail a business operation because
 * delivery failed: {@link EmailDeliveryException} is for retry/backoff by the caller.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
