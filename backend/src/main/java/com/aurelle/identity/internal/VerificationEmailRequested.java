package com.aurelle.identity.internal;

import com.aurelle.messaging.VerificationEmails.Purpose;
import java.time.Duration;

/**
 * Published inside a transaction; the e-mail is sent only after it commits. Holds the plain code and
 * link in memory only: never logged or persisted.
 */
record VerificationEmailRequested(Purpose purpose, String to, String name, String code, String link,
        Duration validFor) {

    @Override
    public String toString() {
        return "VerificationEmailRequested[" + purpose + "]";
    }
}
