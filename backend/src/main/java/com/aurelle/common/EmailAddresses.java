package com.aurelle.common;

/**
 * Structural e-mail check without regular expressions (linear time, no backtracking on hostile
 * input). Deliberately lenient: one {@code @}, a non-empty local part, a domain with an inner dot,
 * no whitespace, at most 254 characters. Real deliverability is proven by the verification e-mail.
 */
public final class EmailAddresses {

    public static final int MAX_LENGTH = 254;

    private EmailAddresses() {
    }

    public static boolean isValid(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return false;
        }
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        int dot = value.lastIndexOf('.');
        return dot > at + 1 && dot < value.length() - 1;
    }
}
