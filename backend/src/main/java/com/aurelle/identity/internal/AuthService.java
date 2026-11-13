package com.aurelle.identity.internal;

import com.aurelle.audit.AuditLog;
import com.aurelle.authorization.Authorization;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.UserError;
import com.aurelle.identity.SessionAuthenticator;
import com.aurelle.identity.internal.AccountStore.AccountRecord;
import com.aurelle.identity.internal.AccountStore.NewCustomer;
import com.aurelle.identity.internal.SessionStore.SessionView;
import com.aurelle.messaging.VerificationEmails.Purpose;
import com.aurelle.organization.OrganizationDirectory;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-up, sign-in, sessions, e-mail verification and passwords (decision D5). */
@Service
public class AuthService implements SessionAuthenticator {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final AccountStore accounts;
    private final SessionStore sessions;
    private final TokenStore tokens;
    private final SecureTokens secure;
    private final PasswordHasher hasher;
    private final OrganizationDirectory organizations;
    private final Authorization authorization;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final IdentityProperties properties;
    private final RateLimiter signInLimiter;
    private final RateLimiter sensitiveLimiter;
    private final Clock clock;
    private final String publicWebUrl;

    AuthService(AccountStore accounts, SessionStore sessions, TokenStore tokens, SecureTokens secure,
            PasswordHasher hasher, OrganizationDirectory organizations, Authorization authorization, AuditLog audit,
            ApplicationEventPublisher events, IdentityProperties properties,
            @Qualifier("signInLimiter") RateLimiter signInLimiter,
            @Qualifier("sensitiveLimiter") RateLimiter sensitiveLimiter, Clock clock,
            @Value("${aurelle.web.public-url}") String publicWebUrl) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.tokens = tokens;
        this.secure = secure;
        this.hasher = hasher;
        this.organizations = organizations;
        this.authorization = authorization;
        this.audit = audit;
        this.events = events;
        this.properties = properties;
        this.signInLimiter = signInLimiter;
        this.sensitiveLimiter = sensitiveLimiter;
        this.clock = clock;
        this.publicWebUrl = publicWebUrl;
    }

    // ------------------------------------------------------------------ sessions

    @Override
    @Transactional
    public Optional<CurrentPrincipal> authenticate(String accessToken) {
        if (accessToken == null || accessToken.isBlank() || accessToken.length() > 100) {
            return Optional.empty();
        }
        return sessions.principalForAccessToken(SecureTokens.sha256(accessToken));
    }

    @Transactional
    public AuthResult signUp(SignUp command, Client client) {
        sensitiveLimiter.acquire("sign-up:" + client.rateKey());
        UUID org = organizations.defaultOrganization().id();
        String email = Credentials.normalizeEmail(blankToNull(command.email()));
        String phone = blankToNull(command.phone());
        List<UserError> errors = new ArrayList<>();
        if (command.displayName() == null || command.displayName().isBlank() || command.displayName().length() > 120) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter your name (up to 120 characters).", "input", "displayName"));
        }
        if (email == null && phone == null) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter an e-mail address or a phone number.", "input", "email"));
        }
        if (email != null && !Credentials.isEmail(email)) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter a valid e-mail address, e.g. name@example.com.", "input", "email"));
        }
        if (phone != null && !Credentials.isPhone(phone)) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter the phone number with country code, e.g. +250788123456.", "input", "phone"));
        }
        Credentials.checkPassword(command.password(), List.of("input", "password")).ifPresent(errors::add);
        if (!command.acceptTerms()) {
            errors.add(UserError.of("TERMS_NOT_ACCEPTED", "Accept the terms to create an account.", "input", "acceptTerms"));
        }
        if (errors.isEmpty() && email != null && accounts.emailTaken(org, email)) {
            errors.add(UserError.of("EMAIL_TAKEN", "An account with this e-mail already exists. Sign in instead.", "input", "email"));
        }
        if (errors.isEmpty() && phone != null && accounts.phoneTaken(org, phone)) {
            errors.add(UserError.of("PHONE_TAKEN", "An account with this phone number already exists. Sign in instead.", "input", "phone"));
        }
        if (!errors.isEmpty()) {
            return AuthResult.failed(errors);
        }
        UUID accountId = accounts.insertCustomer(new NewCustomer(org, command.displayName().trim(), email, phone,
            hasher.hash(command.password()), command.marketingConsent(), blankToNull(command.preferredLocale()),
            command.shopFor().toArray(String[]::new), properties.termsVersion()));
        authorization.bindSystemRole(accountId, org, "customer", null);
        if (email != null) {
            issueEmailVerification(accountId, email, command.displayName().trim());
        }
        audit.record(new AuditLog.Entry(org, accountId, CurrentPrincipal.CUSTOMER, "auth.sign_up", "user_account",
            accountId.toString(), SUCCESS, Map.of("platform", client.platform())));
        return AuthResult.ok(openSession(accountId, client));
    }

    @Transactional
    public AuthResult signIn(String identifier, String password, Client client) {
        String key = Credentials.normalizeIdentifier(identifier);
        signInLimiter.acquire("sign-in:id:" + key);
        signInLimiter.acquire("sign-in:client:" + client.rateKey());
        UUID org = organizations.defaultOrganization().id();
        Optional<AccountRecord> found = accounts.findByIdentifier(org, key);
        Instant now = clock.instant();
        if (found.isEmpty() || found.get().lockedAt(now)) {
            hasher.matches(password, found.map(AccountRecord::passwordHash).orElse(null));
            audit.record(new AuditLog.Entry(org, null, "ANONYMOUS", "auth.sign_in", "user_account",
                found.map(a -> a.id().toString()).orElse(null), FAILED, Map.of("reason", "INVALID_CREDENTIALS")));
            return invalidCredentials();
        }
        AccountRecord account = found.get();
        if (!hasher.matches(password, account.passwordHash())) {
            accounts.recordFailedSignIn(account.id(), properties.maxFailedSignIns(), properties.lockDuration());
            audit.record(new AuditLog.Entry(org, null, "ANONYMOUS", "auth.sign_in", "user_account",
                account.id().toString(), FAILED, Map.of("reason", "INVALID_CREDENTIALS")));
            return invalidCredentials();
        }
        if ("INACTIVE".equals(account.status())) {
            return AuthResult.failed(List.of(UserError.of("ACCOUNT_INACTIVE",
                "This account has been deactivated. Contact the shop for help.")));
        }
        accounts.recordSuccessfulSignIn(account.id());
        audit.record(new AuditLog.Entry(org, account.id(), account.kind(), "auth.sign_in", "user_account",
            account.id().toString(), SUCCESS, Map.of("platform", client.platform())));
        return AuthResult.ok(openSession(account.id(), client));
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || refreshToken.length() > 100) {
            return tokenInvalid();
        }
        SecureTokens.Issued access = secure.newToken();
        SecureTokens.Issued refresh = secure.newToken();
        Instant now = clock.instant();
        Instant accessExpires = now.plus(properties.accessTokenTtl());
        Instant refreshExpires = now.plus(properties.refreshTokenTtl());
        return sessions.rotate(SecureTokens.sha256(refreshToken), access.hash(), accessExpires, refresh.hash(), refreshExpires)
            .map(accountId -> AuthResult.ok(new IssuedSession(accountId, access.token(), accessExpires,
                refresh.token(), refreshExpires)))
            .orElseGet(AuthService::tokenInvalid);
    }

    @Transactional
    public boolean signOut(CurrentPrincipal principal) {
        return sessions.revoke(principal.sessionId(), principal.accountId(), "SIGN_OUT");
    }

    @Transactional
    public boolean revokeSession(CurrentPrincipal principal, UUID sessionId) {
        return sessions.revoke(sessionId, principal.accountId(), "REVOKED_BY_USER");
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(CurrentPrincipal principal) {
        return sessions.listActive(principal.accountId());
    }

    // ------------------------------------------------------------------ e-mail verification

    @Transactional
    public void resendEmailVerification(CurrentPrincipal principal, Client client) {
        sensitiveLimiter.acquire("verify:" + principal.accountId());
        accounts.findById(principal.accountId())
            .filter(account -> account.email() != null && !account.emailVerified())
            .ifPresent(account -> issueEmailVerification(account.id(), account.email(), displayNameOf(account)));
    }

    /** Verifies with the link token (anyone) or the 6-digit code (signed-in viewer only). */
    @Transactional
    public List<UserError> verifyEmail(String token, String code, Optional<CurrentPrincipal> viewer) {
        Optional<TokenStore.OpenToken> open;
        if (hasText(token) == hasText(code)) {
            return List.of(UserError.of("VALIDATION_FAILED", "Enter the 6-digit code or open the link from the e-mail.", "input"));
        }
        if (hasText(token)) {
            open = tokens.findOpenByTokenHash("EMAIL_VERIFY", SecureTokens.sha256(token));
        } else {
            if (viewer.isEmpty()) {
                return List.of(UserError.of("VALIDATION_FAILED", "Sign in first, then enter the code.", "input", "code"));
            }
            open = tokens.findLatestOpen(viewer.get().accountId(), "EMAIL_VERIFY");
            if (open.isPresent() && !SecureTokens.same(open.get().codeHash(), secure.codeHash(open.get().tokenHash(), code))) {
                tokens.recordWrongCode(open.get().id());
                return tokenInvalidErrors();
            }
        }
        if (open.isEmpty() || !tokens.consume(open.get().id())) {
            return tokenInvalidErrors();
        }
        accounts.markEmailVerified(open.get().accountId());
        return List.of();
    }

    // ------------------------------------------------------------------ passwords

    /** Enumeration-safe: same response whether or not the account exists. */
    @Transactional
    public void requestPasswordReset(String identifier) {
        String key = Credentials.normalizeIdentifier(identifier);
        sensitiveLimiter.acquire("reset:" + key);
        UUID org = organizations.defaultOrganization().id();
        accounts.findByIdentifier(org, key)
            .filter(account -> account.email() != null && !"INVITED".equals(account.status()))
            .ifPresent(account -> {
                SecureTokens.Issued token = secure.newToken();
                String code = secure.newCode();
                tokens.issue(account.id(), "PASSWORD_RESET", token.hash(), secure.codeHash(token.hash(), code),
                    clock.instant().plus(properties.passwordResetTtl()));
                events.publishEvent(new VerificationEmailRequested(Purpose.PASSWORD_RESET, account.email(),
                    displayNameOf(account), code, publicWebUrl + "/reset-password?token=" + token.token(),
                    properties.passwordResetTtl()));
            });
    }

    /** Resets with the link token, or identifier + code. Signs out every session of the account. */
    @Transactional
    public List<UserError> resetPassword(String token, String identifier, String code, String newPassword) {
        boolean byLink = hasText(token);
        boolean byCode = hasText(identifier) && hasText(code);
        if (byLink == byCode) {
            return List.of(UserError.of("VALIDATION_FAILED", "Use the link from the e-mail, or your e-mail/phone with the code.", "input"));
        }
        Optional<UserError> weak = Credentials.checkPassword(newPassword, List.of("input", "newPassword"));
        if (weak.isPresent()) {
            return List.of(weak.get());
        }
        Optional<TokenStore.OpenToken> open;
        if (byLink) {
            open = tokens.findOpenByTokenHash("PASSWORD_RESET", SecureTokens.sha256(token));
        } else {
            sensitiveLimiter.acquire("reset-code:" + Credentials.normalizeIdentifier(identifier));
            UUID org = organizations.defaultOrganization().id();
            open = accounts.findByIdentifier(org, Credentials.normalizeIdentifier(identifier))
                .flatMap(account -> tokens.findLatestOpen(account.id(), "PASSWORD_RESET"));
            if (open.isPresent() && !SecureTokens.same(open.get().codeHash(), secure.codeHash(open.get().tokenHash(), code))) {
                tokens.recordWrongCode(open.get().id());
                return tokenInvalidErrors();
            }
        }
        if (open.isEmpty() || !tokens.consume(open.get().id())) {
            return tokenInvalidErrors();
        }
        UUID accountId = open.get().accountId();
        accounts.setPassword(accountId, hasher.hash(newPassword));
        sessions.revokeAllExcept(accountId, null, "PASSWORD_CHANGED");
        accounts.findById(accountId).ifPresent(account -> audit.record(new AuditLog.Entry(account.organizationId(),
            accountId, account.kind(), "auth.password_reset", "user_account", accountId.toString(), SUCCESS, Map.of())));
        return List.of();
    }

    /** Changes the viewer's password and signs out every other session. */
    @Transactional
    public List<UserError> changePassword(CurrentPrincipal principal, String currentPassword, String newPassword) {
        sensitiveLimiter.acquire("change-password:" + principal.accountId());
        AccountRecord account = accounts.findById(principal.accountId()).orElseThrow();
        if (!hasher.matches(currentPassword, account.passwordHash())) {
            return List.of(UserError.of("INVALID_CREDENTIALS", "Your current password is not correct.", "input", "currentPassword"));
        }
        Optional<UserError> weak = Credentials.checkPassword(newPassword, List.of("input", "newPassword"));
        if (weak.isPresent()) {
            return List.of(weak.get());
        }
        accounts.setPassword(account.id(), hasher.hash(newPassword));
        sessions.revokeAllExcept(account.id(), principal.sessionId(), "PASSWORD_CHANGED");
        audit.record(new AuditLog.Entry(account.organizationId(), account.id(), account.kind(),
            "auth.password_change", "user_account", account.id().toString(), SUCCESS, Map.of()));
        return List.of();
    }

    // ------------------------------------------------------------------ helpers

    private void issueEmailVerification(UUID accountId, String email, String name) {
        SecureTokens.Issued token = secure.newToken();
        String code = secure.newCode();
        tokens.issue(accountId, "EMAIL_VERIFY", token.hash(), secure.codeHash(token.hash(), code),
            clock.instant().plus(properties.emailVerifyTtl()));
        events.publishEvent(new VerificationEmailRequested(Purpose.EMAIL_VERIFY, email, name, code,
            publicWebUrl + "/verify-email?token=" + token.token(), properties.emailVerifyTtl()));
    }

    private IssuedSession openSession(UUID accountId, Client client) {
        SecureTokens.Issued access = secure.newToken();
        SecureTokens.Issued refresh = secure.newToken();
        Instant now = clock.instant();
        Instant accessExpires = now.plus(properties.accessTokenTtl());
        Instant refreshExpires = now.plus(properties.refreshTokenTtl());
        sessions.create(accountId, client.platform(), client.deviceLabel(), access.hash(), accessExpires,
            refresh.hash(), refreshExpires);
        return new IssuedSession(accountId, access.token(), accessExpires, refresh.token(), refreshExpires);
    }

    private String displayNameOf(AccountRecord account) {
        return accounts.displayName(account.id()).orElse("there");
    }

    private static AuthResult invalidCredentials() {
        return AuthResult.failed(List.of(UserError.of("INVALID_CREDENTIALS",
            "That e-mail/phone and password don't match. Try again or reset your password.")));
    }

    private static AuthResult tokenInvalid() {
        return AuthResult.failed(tokenInvalidErrors());
    }

    private static List<UserError> tokenInvalidErrors() {
        return List.of(UserError.of("TOKEN_INVALID_OR_EXPIRED",
            "This code or link is not valid any more. Ask for a new one."));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    // ------------------------------------------------------------------ types

    /**
     * @param platform    {@code WEB} for browsers (request had an Origin header), else {@code ANDROID}
     * @param deviceLabel optional label shown in "my devices"
     * @param rateKey     client key for rate limiting (e.g. hashed remote address)
     */
    public record Client(String platform, String deviceLabel, String rateKey) {
    }

    public record SignUp(String displayName, String email, String phone, String password, boolean acceptTerms,
            boolean marketingConsent, List<String> shopFor, String preferredLocale) {

        public SignUp {
            shopFor = shopFor == null ? List.of() : List.copyOf(shopFor);
        }
    }

    public record IssuedSession(UUID accountId, String accessToken, Instant accessExpiresAt, String refreshToken,
            Instant refreshExpiresAt) {
    }

    public record AuthResult(IssuedSession session, List<UserError> errors) {

        static AuthResult ok(IssuedSession session) {
            return new AuthResult(session, List.of());
        }

        static AuthResult failed(List<UserError> errors) {
            return new AuthResult(null, List.copyOf(errors));
        }
    }
}
