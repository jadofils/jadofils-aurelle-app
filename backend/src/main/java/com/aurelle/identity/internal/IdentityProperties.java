package com.aurelle.identity.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code aurelle.identity.*} (decision D5).
 *
 * @param accessTokenTtl     access token lifetime (15 min)
 * @param refreshTokenTtl    refresh token lifetime (30 days)
 * @param emailVerifyTtl     e-mail verification code/link lifetime (24 h)
 * @param passwordResetTtl   password reset code/link lifetime (30 min)
 * @param staffInviteTtl     staff invite link lifetime (72 h)
 * @param maxFailedSignIns   failed sign-ins before a temporary lock
 * @param lockDuration       how long the lock lasts
 * @param codeHmacKey        server key for hashing 6-digit codes (AURELLE_CODE_HMAC_KEY; random if blank)
 * @param termsVersion       current terms version recorded at sign-up
 * @param allowedOrigins     browser origins whose cookies are accepted (CSRF protection)
 * @param secureCookies      Secure flag on cookies (true except plain-http local setups)
 */
@Validated
@ConfigurationProperties("aurelle.identity")
record IdentityProperties(
    @NotNull Duration accessTokenTtl,
    @NotNull Duration refreshTokenTtl,
    @NotNull Duration emailVerifyTtl,
    @NotNull Duration passwordResetTtl,
    @NotNull Duration staffInviteTtl,
    @Min(1) int maxFailedSignIns,
    @NotNull Duration lockDuration,
    String codeHmacKey,
    @NotBlank String termsVersion,
    @NotNull List<String> allowedOrigins,
    boolean secureCookies) {
}
