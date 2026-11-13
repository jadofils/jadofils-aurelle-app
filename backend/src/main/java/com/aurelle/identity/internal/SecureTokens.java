package com.aurelle.identity.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opaque tokens (256-bit, URL-safe) and 6-digit codes. Only hashes are stored: SHA-256 for tokens,
 * HMAC-SHA-256 with a server key for codes (a 6-digit space is too small for a plain hash).
 */
class SecureTokens {

    private static final Logger log = LoggerFactory.getLogger(SecureTokens.class);

    private final SecureRandom random;
    private final byte[] codeKey;

    SecureTokens(String codeHmacKey, SecureRandom random) {
        this.random = random;
        if (codeHmacKey == null || codeHmacKey.isBlank()) {
            log.warn("AURELLE_CODE_HMAC_KEY is not set: using a random key; codes stop working after a restart");
            this.codeKey = new byte[32];
            random.nextBytes(this.codeKey);
        } else {
            this.codeKey = codeHmacKey.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** A new random token and its SHA-256 hash. */
    Issued newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Issued(token, sha256(token));
    }

    /** A new 6-digit code. */
    String newCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** HMAC of the code bound to its token (by the token's hash), so a code only works for that token. */
    byte[] codeHash(byte[] tokenHash, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeKey, "HmacSHA256"));
            mac.update(tokenHash);
            return mac.doFinal((":" + code).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable", ex);
        }
    }

    /** Constant-time comparison. */
    static boolean same(byte[] a, byte[] b) {
        return a != null && b != null && MessageDigest.isEqual(a, b);
    }

    record Issued(String token, byte[] hash) {
    }
}
