package com.aurelle.messaging;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the "code + link" e-mails used for e-mail verification, password reset and staff invites.
 * The customer can either type the 6-digit code into the app or tap the link.
 */
public final class VerificationEmails {

    /** What the code/link is for. Each purpose has its own wording. */
    public enum Purpose {
        EMAIL_VERIFY("Confirm your e-mail", "Use this code to confirm your e-mail address", "Confirm e-mail"),
        PASSWORD_RESET("Reset your password", "Use this code to choose a new password", "Reset password"),
        STAFF_INVITE("You're invited to the Aurelle team", "Use this code to set up your staff account",
            "Accept invite");

        private final String subject;
        private final String lead;
        private final String button;

        Purpose(String subject, String lead, String button) {
            this.subject = subject;
            this.lead = lead;
            this.button = button;
        }
    }

    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");

    private VerificationEmails() {
    }

    /**
     * @param shopName  shown in the subject and footer
     * @param to        recipient address
     * @param name      recipient display name (escaped in HTML)
     * @param code      6-digit one-time code (never put in the subject)
     * @param link      https/http link containing the one-time token
     * @param validFor  how long the code and link work
     */
    public static EmailMessage compose(Purpose purpose, String shopName, String to, String name, String code,
            String link, Duration validFor) {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(shopName, "shopName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(validFor, "validFor");
        if (code == null || !SIX_DIGITS.matcher(code).matches()) {
            throw new IllegalArgumentException("code must be 6 digits");
        }
        if (link == null || !(link.startsWith("https://") || link.startsWith("http://"))) {
            throw new IllegalArgumentException("link must be an http(s) URL");
        }
        String expiry = describe(validFor);
        String subject = purpose.subject + " · " + shopName;
        String text = """
            Hi %s,

            %s: %s

            Or open this link: %s

            The code and link work once and expire in %s.
            If you didn't ask for this, you can ignore this e-mail.

            %s
            """.formatted(name, purpose.lead, code, link, expiry, shopName);
        String html = """
            <!doctype html>
            <html><body style="margin:0;background:#f2ece3;font-family:Manrope,Arial,sans-serif;color:#1a130b">
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px">
            <table role="presentation" width="100%%" style="max-width:480px;background:#ffffff" cellpadding="0" cellspacing="0">
            <tr><td style="padding:32px 24px">
            <p style="margin:0 0 16px;font-family:Fraunces,Georgia,serif;font-size:26px;font-weight:700">%s</p>
            <p style="margin:0 0 16px;font-size:15px">Hi %s,</p>
            <p style="margin:0 0 8px;font-size:15px">%s:</p>
            <p style="margin:0 0 24px;font-size:32px;font-weight:700;letter-spacing:8px">%s</p>
            <p style="margin:0 0 24px"><a href="%s" style="display:inline-block;padding:12px 24px;background:#ff7200;color:#000000;font-weight:700;text-decoration:none">%s</a></p>
            <p style="margin:0 0 8px;font-size:13px;color:#6b6156">The code and link work once and expire in %s.</p>
            <p style="margin:0;font-size:13px;color:#6b6156">If you didn't ask for this, you can ignore this e-mail.</p>
            </td></tr></table></td></tr></table></body></html>
            """.formatted(HtmlUtils.htmlEscape(shopName), HtmlUtils.htmlEscape(name), purpose.lead, code,
                HtmlUtils.htmlEscape(link), purpose.button, expiry);
        return new EmailMessage(to, subject, text, html);
    }

    static String describe(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes < 1) {
            throw new IllegalArgumentException("validFor must be at least one minute");
        }
        if (minutes % (24 * 60) == 0) {
            long days = minutes / (24 * 60);
            return days == 1 ? "24 hours" : days + " days";
        }
        if (minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }
}
