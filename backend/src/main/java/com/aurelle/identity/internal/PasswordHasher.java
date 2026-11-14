package com.aurelle.identity.internal;

import java.util.Objects;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id password hashing (decision D5). Hashes start with {@code $argon2id$}, which the
 * {@code user_account.password_hash} check constraint enforces.
 */
@Component
public class PasswordHasher {

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    /** Compared against when an account is unknown, so response time does not reveal whether it exists. */
    private final String decoyHash = encoder.encode("decoy-password-for-timing-equalisation");

    public String hash(CharSequence password) {
        Objects.requireNonNull(password, "password");
        return encoder.encode(password);
    }

    /** True if the password matches; a null hash (no password set) never matches but costs the same time. */
    public boolean matches(CharSequence password, String hash) {
        Objects.requireNonNull(password, "password");
        if (hash == null) {
            encoder.matches(password, decoyHash);
            return false;
        }
        return encoder.matches(password, hash);
    }
}
