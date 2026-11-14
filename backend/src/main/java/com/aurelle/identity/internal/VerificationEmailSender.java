package com.aurelle.identity.internal;

import com.aurelle.messaging.EmailDeliveryException;
import com.aurelle.messaging.EmailSender;
import com.aurelle.messaging.VerificationEmails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends verification/reset/invite e-mails after the transaction commits, on the bounded mail
 * executor. A delivery failure never rolls back or fails the user's request; the user can ask again.
 */
@Component
class VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailSender.class);

    private final EmailSender emailSender;
    private final String shopName;

    VerificationEmailSender(EmailSender emailSender,
            @org.springframework.beans.factory.annotation.Value("${aurelle.mail.from-name}") String shopName) {
        this.emailSender = emailSender;
        this.shopName = shopName;
    }

    @Async(IdentityConfiguration.MAIL_EXECUTOR)
    @TransactionalEventListener
    void onVerificationEmailRequested(VerificationEmailRequested event) {
        send(event);
    }

    void send(VerificationEmailRequested event) {
        try {
            emailSender.send(VerificationEmails.compose(event.purpose(), shopName, event.to(), event.name(),
                event.code(), event.link(), event.validFor()));
        } catch (EmailDeliveryException ex) {
            log.atWarn().addKeyValue("purpose", event.purpose()).log("Verification e-mail not delivered");
        }
    }
}
