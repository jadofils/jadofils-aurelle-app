package com.aurelle.identity.internal;

import com.aurelle.common.EmailAddresses;
import com.aurelle.common.UserError;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Input rules for identifiers and passwords (contracts/errors.md, Phase 1). */
final class Credentials {

    static final int MIN_PASSWORD = 10;
    static final int MAX_PASSWORD = 128;
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{6,14}$");
    /**
     * Small offline deny-list of the most common breached passwords. A full breached-password check
     * (e.g. k-anonymity range API) is a follow-up; see docs/decisions.md D5.
     */
    private static final Set<String> COMMON = Set.of("password123", "1234567890", "qwertyuiop", "password1234",
        "iloveyou123", "aurelle123", "aurelle2026", "welcome123", "abcdefghij", "0123456789", "qwerty12345",
        "1q2w3e4r5t", "letmein123", "football123", "monkey12345");

    private Credentials() {
    }

    static boolean isEmail(String value) {
        return EmailAddresses.isValid(value);
    }

    static boolean isPhone(String value) {
        return value != null && E164.matcher(value).matches();
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Identifier key for rate limiting and lookups: lower-cased e-mail or trimmed phone. */
    static String normalizeIdentifier(String identifier) {
        String trimmed = identifier == null ? "" : identifier.trim();
        return trimmed.contains("@") ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }

    static Optional<UserError> checkPassword(String password, List<String> field) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD
                || COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            return Optional.of(new UserError("PASSWORD_TOO_WEAK",
                "Use at least " + MIN_PASSWORD + " characters and avoid common passwords.", field));
        }
        return Optional.empty();
    }
}
