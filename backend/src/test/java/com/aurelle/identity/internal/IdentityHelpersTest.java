package com.aurelle.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.common.RateLimitedException;
import com.aurelle.identity.internal.AccountStore.AccountRecord;
import com.aurelle.messaging.EmailDeliveryException;
import com.aurelle.messaging.VerificationEmails.Purpose;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for the identity module's small building blocks. */
class IdentityHelpersTest {

    // ------------------------------------------------------------- RateLimiter

    private final AtomicLong millis = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
    };

    @Test
    void rateLimiterAllowsUpToTheLimitThenReportsTheWait() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofSeconds(60), clock);
        limiter.acquire("k");
        limiter.acquire("k");
        millis.addAndGet(10_000);

        assertThatThrownBy(() -> limiter.acquire("k"))
            .isInstanceOfSatisfying(RateLimitedException.class, ex -> assertThat(ex.retryAfterSeconds()).isEqualTo(50));
        assertThatCode(() -> limiter.acquire("other")).doesNotThrowAnyException();
    }

    @Test
    void rateLimiterStartsANewWindowAfterExpiry() {
        RateLimiter limiter = new RateLimiter(1, Duration.ofSeconds(1), clock);
        limiter.acquire("k");
        millis.addAndGet(1_000);

        assertThatCode(() -> limiter.acquire("k")).doesNotThrowAnyException();
    }

    @Test
    void rateLimiterStaysBoundedInMemory() {
        RateLimiter expiring = new RateLimiter(1, Duration.ofMillis(1), clock);
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) {
            expiring.acquire("key-" + i);
        }
        millis.addAndGet(5);
        assertThatCode(() -> expiring.acquire("new-key")).as("expired windows are evicted").doesNotThrowAnyException();

        RateLimiter live = new RateLimiter(1, Duration.ofHours(1), clock);
        for (int i = 0; i < RateLimiter.MAX_KEYS; i++) {
            live.acquire("key-" + i);
        }
        assertThatCode(() -> live.acquire("new-key")).as("full of live windows: cleared").doesNotThrowAnyException();
    }

    @Test
    void rateLimitedExceptionWaitsAtLeastOneSecond() {
        assertThat(new RateLimitedException(0).retryAfterSeconds()).isEqualTo(1);
    }

    // ------------------------------------------------------------- Credentials

    @ParameterizedTest
    @ValueSource(strings = {"amina@example.test", "a.b+c@shop.example.test"})
    void acceptsEmails(String email) {
        assertThat(Credentials.isEmail(email)).isTrue();
    }

    @Test
    void rejectsInvalidEmails() {
        assertThat(Credentials.isEmail(null)).isFalse();
        assertThat(Credentials.isEmail("no-at-sign")).isFalse();
        assertThat(Credentials.isEmail("a".repeat(250) + "@example.test")).isFalse();
    }

    @Test
    void phonesMustBeE164() {
        assertThat(Credentials.isPhone("+250788123456")).isTrue();
        assertThat(Credentials.isPhone("0788123456")).isFalse();
        assertThat(Credentials.isPhone(null)).isFalse();
    }

    @Test
    void normalizesIdentifiers() {
        assertThat(Credentials.normalizeIdentifier("  Amina@Example.TEST ")).isEqualTo("amina@example.test");
        assertThat(Credentials.normalizeIdentifier(" +250788123456 ")).isEqualTo("+250788123456");
        assertThat(Credentials.normalizeIdentifier(null)).isEmpty();
        assertThat(Credentials.normalizeEmail(null)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "PASSWORD123", "1234567890"})
    void rejectsWeakPasswords(String password) {
        assertThat(Credentials.checkPassword(password, List.of("input", "password"))).get()
            .satisfies(error -> assertThat(error.code()).isEqualTo("PASSWORD_TOO_WEAK"));
    }

    @Test
    void rejectsOverlongPasswordsAndAcceptsGoodOnes() {
        assertThat(Credentials.checkPassword("x".repeat(129), List.of())).isPresent();
        assertThat(Credentials.checkPassword("Correct-Horse-Battery-9", List.of())).isEmpty();
    }

    // ------------------------------------------------------------- SecureTokens

    @Test
    void tokensAreRandomUrlSafeAndHashed() {
        SecureTokens tokens = new SecureTokens("server-key", new SecureRandom());
        SecureTokens.Issued a = tokens.newToken();
        SecureTokens.Issued b = tokens.newToken();

        assertThat(a.token()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(b.token());
        assertThat(a.hash()).isEqualTo(SecureTokens.sha256(a.token())).hasSize(32);
        assertThat(tokens.newCode()).matches("\\d{6}");
    }

    @Test
    void codeHashesDependOnKeyTokenAndCode() {
        byte[] tokenHash = SecureTokens.sha256("t");
        SecureTokens one = new SecureTokens("key-one", new SecureRandom());
        SecureTokens two = new SecureTokens("key-two", new SecureRandom());

        assertThat(SecureTokens.same(one.codeHash(tokenHash, "123456"), one.codeHash(tokenHash, "123456"))).isTrue();
        assertThat(SecureTokens.same(one.codeHash(tokenHash, "123456"), two.codeHash(tokenHash, "123456"))).isFalse();
        assertThat(SecureTokens.same(one.codeHash(tokenHash, "123456"), one.codeHash(SecureTokens.sha256("u"), "123456"))).isFalse();
        assertThat(SecureTokens.same(null, tokenHash)).isFalse();
        assertThat(SecureTokens.same(tokenHash, null)).isFalse();
    }

    @Test
    void missingKeyFallsBackToARandomOne() {
        byte[] tokenHash = SecureTokens.sha256("t");
        SecureTokens blank = new SecureTokens(" ", new SecureRandom());
        SecureTokens absent = new SecureTokens(null, new SecureRandom());

        assertThat(SecureTokens.same(blank.codeHash(tokenHash, "1"), absent.codeHash(tokenHash, "1"))).isFalse();
    }

    // ------------------------------------------------------------- records and small adapters

    @Test
    void lockIsActiveOnlyWhileLockedUntilIsInTheFuture() {
        Instant now = Instant.parse("2026-11-13T10:00:00Z");
        assertThat(account("LOCKED", now.plusSeconds(60)).lockedAt(now)).isTrue();
        assertThat(account("LOCKED", now.minusSeconds(60)).lockedAt(now)).isFalse();
        assertThat(account("LOCKED", null).lockedAt(now)).isFalse();
        assertThat(account("ACTIVE", now.plusSeconds(60)).lockedAt(now)).isFalse();
    }

    @Test
    void verificationEventNeverPrintsTheCodeOrLink() {
        var event = new VerificationEmailRequested(Purpose.EMAIL_VERIFY, "a@example.test", "A", "123456",
            "https://x.example.test/?token=secret", Duration.ofHours(1));

        assertThat(event.toString()).doesNotContain("123456", "secret", "a@example.test");
    }

    @Test
    void verificationEmailSenderSwallowsDeliveryFailures() {
        var sender = new VerificationEmailSender(message -> {
            throw new EmailDeliveryException("down", null);
        }, "Aurelle");
        var event = new VerificationEmailRequested(Purpose.PASSWORD_RESET, "a@example.test", "A", "123456",
            "https://x.example.test/?token=t", Duration.ofMinutes(30));

        assertThatCode(() -> sender.onVerificationEmailRequested(event)).doesNotThrowAnyException();
    }

    @Test
    void seedPlaceholderUsesTheConfiguredPasswordOrARandomOne() {
        PasswordHasher hasher = new PasswordHasher();
        assertThat(new SeedPasswordPlaceholder(hasher, "Configured-Seed-1").passwordOrRandom()).isEqualTo("Configured-Seed-1");
        String random = new SeedPasswordPlaceholder(hasher, " ").passwordOrRandom();
        assertThat(random).hasSizeGreaterThan(30).isNotEqualTo(new SeedPasswordPlaceholder(hasher, null).passwordOrRandom());
    }

    @Test
    void passwordHasherNeverMatchesAMissingHash() {
        PasswordHasher hasher = new PasswordHasher();
        String hash = hasher.hash("Correct-Horse-Battery-9");

        assertThat(hash).startsWith("$argon2id$");
        assertThat(hasher.matches("Correct-Horse-Battery-9", hash)).isTrue();
        assertThat(hasher.matches("Correct-Horse-Battery-9", null)).isFalse();
    }

    private static AccountRecord account(String status, Instant lockedUntil) {
        return new AccountRecord(UUID.randomUUID(), UUID.randomUUID(), "CUSTOMER", "a@example.test", false, null,
            false, null, status, 0, lockedUntil, "SYSTEM", null);
    }
}
