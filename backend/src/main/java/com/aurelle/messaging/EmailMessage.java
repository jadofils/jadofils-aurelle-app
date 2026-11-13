package com.aurelle.messaging;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An outgoing e-mail. Bodies may contain one-time codes or links, so instances are never logged.
 *
 * @param to       recipient address (PII)
 * @param subject  subject line (must not contain secrets)
 * @param textBody plain-text body (always sent, for clients without HTML)
 * @param htmlBody HTML body, or null for text only
 */
public record EmailMessage(String to, String subject, String textBody, String htmlBody) {

    private static final Pattern ADDRESS = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
        if (!ADDRESS.matcher(to).matches()) {
            throw new IllegalArgumentException("to must be an e-mail address");
        }
        if (subject.isBlank() || subject.contains("\n") || subject.contains("\r")) {
            throw new IllegalArgumentException("subject must be a single non-blank line");
        }
    }

    /** Recipient with the local part masked, safe for logs: {@code j***@gmail.com}. */
    public String maskedRecipient() {
        int at = to.indexOf('@');
        return to.charAt(0) + "***" + to.substring(at);
    }

    @Override
    public String toString() {
        return "EmailMessage[to=" + maskedRecipient() + ", subject=" + subject + "]";
    }
}
