package com.aurelle.messaging.internal;

import com.aurelle.messaging.EmailMessage;
import com.aurelle.messaging.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when {@code aurelle.mail.enabled=false}: drops the message and logs no codes, links or bodies. */
class DisabledEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(DisabledEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.atInfo().addKeyValue("to", message.maskedRecipient())
            .log("E-mail not sent: aurelle.mail.enabled=false");
    }
}
