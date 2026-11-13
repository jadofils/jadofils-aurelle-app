package com.aurelle.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.IntegrationTest;
import com.aurelle.authorization.Authorization;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.RateLimitedException;
import com.aurelle.common.UserError;
import com.aurelle.identity.internal.AuthService.AuthResult;
import com.aurelle.identity.internal.AuthService.Client;
import com.aurelle.identity.internal.AuthService.SignUp;
import com.aurelle.messaging.VerificationEmails.Purpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class AuthServiceIT extends IntegrationTest {

    /** Generated per run: no credential literal in the code. */
    private static final String PASSWORD = "Pw-" + UUID.randomUUID();

    @Autowired
    AuthService auth;
    @Autowired
    Authorization authorization;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ApplicationEvents events;

    // ------------------------------------------------------------- sign-up

    @Test
    void signUpCreatesACustomerWithProfileRoleSessionAuditAndVerificationEmail() {
        String email = uniqueEmail();

        AuthResult result = auth.signUp(signUp(email, null), client());

        assertThat(result.errors()).isEmpty();
        UUID accountId = result.session().accountId();
        assertThat(result.session().accessToken()).hasSizeGreaterThan(40);
        assertThat(count("select count(*) from customer_profile where user_account_id = ?", accountId)).isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action = 'auth.sign_up' and actor_id = ?", accountId)).isEqualTo(1);
        assertThat(jdbc.sql("select password_hash from user_account where id = ?").param(accountId).query(String.class).single())
            .startsWith("$argon2id$").doesNotContain(PASSWORD);
        assertThat(permissionsOf(accountId)).containsExactly("shop.order.place", "shop.review.write", "shop.wishlist.manage");
        assertThat(verificationEmailTo(email, Purpose.EMAIL_VERIFY)).isPresent();
        assertThat(auth.authenticate(result.session().accessToken())).get()
            .extracting(CurrentPrincipal::accountId).isEqualTo(accountId);
    }

    @Test
    void signUpWithPhoneOnlySendsNoEmail() {
        AuthResult result = auth.signUp(signUp(null, uniquePhone()), client());

        assertThat(result.errors()).isEmpty();
        assertThat(events.stream(VerificationEmailRequested.class)).isEmpty();
    }

    @Test
    void signUpReportsEveryInvalidField() {
        AuthResult result = auth.signUp(new SignUp(" ", "not-an-email", "0788", "short", false, false, null, null), client());

        assertThat(result.session()).isNull();
        assertThat(result.errors()).extracting(UserError::code)
            .contains("VALIDATION_FAILED", "PASSWORD_TOO_WEAK", "TERMS_NOT_ACCEPTED");
        assertThat(result.errors()).extracting(e -> String.join(".", e.field()))
            .contains("input.displayName", "input.email", "input.phone", "input.password", "input.acceptTerms");
    }

    @Test
    void signUpNeedsEmailOrPhoneAndRejectsCommonPasswords() {
        AuthResult result = auth.signUp(new SignUp("Ama", null, null, "password123", true, false, null, null), client());

        assertThat(result.errors()).extracting(UserError::code).containsExactly("VALIDATION_FAILED", "PASSWORD_TOO_WEAK");
    }

    @Test
    void signUpRejectsTakenEmailAndPhoneCaseInsensitively() {
        assertThat(auth.signUp(signUp("AMINA@example.test", null), client()).errors())
            .extracting(UserError::code).containsExactly("EMAIL_TAKEN");
        assertThat(auth.signUp(signUp(null, "+447700900022"), client()).errors())
            .extracting(UserError::code).containsExactly("PHONE_TAKEN");
    }

    @Test
    void signUpIsRateLimitedPerClient() {
        Client client = client();
        for (int i = 0; i < 5; i++) {
            auth.signUp(signUp(uniqueEmail(), null), client);
        }
        assertThatThrownBy(() -> auth.signUp(signUp(uniqueEmail(), null), client))
            .isInstanceOf(RateLimitedException.class);
    }

    // ------------------------------------------------------------- sign-in

    @Test
    void seededAccountsSignInWithTheSeedPassword() {
        AuthResult result = auth.signIn("Amina@Example.test", SEED_PASSWORD, client());

        assertThat(result.errors()).isEmpty();
        assertThat(count("select count(*) from user_account where email = 'amina@example.test' and last_sign_in_at > now() - interval '1 minute'")).isEqualTo(1);
    }

    @Test
    void wrongPasswordUnknownAccountAndLockedAccountLookTheSame() {
        String email = uniqueEmail();
        auth.signUp(signUp(email, null), client());

        List<UserError> wrong = auth.signIn(email, "Wrong-Password-123", client()).errors();
        List<UserError> unknown = auth.signIn(uniqueEmail(), PASSWORD, client()).errors();
        List<UserError> locked = auth.signIn("nia@example.test", SEED_PASSWORD, client()).errors();

        assertThat(wrong).isEqualTo(unknown).isEqualTo(locked);
        assertThat(wrong).extracting(UserError::code).containsExactly("INVALID_CREDENTIALS");
    }

    @Test
    void fiveWrongPasswordsLockTheAccount() {
        String email = uniqueEmail();
        UUID accountId = auth.signUp(signUp(email, null), client()).session().accountId();

        for (int i = 0; i < 5; i++) {
            auth.signIn(email, "Wrong-Password-" + i, client());
        }

        assertThat(jdbc.sql("select status from user_account where id = ?").param(accountId).query(String.class).single())
            .isEqualTo("LOCKED");
        assertThat(auth.signIn(email, PASSWORD, client()).errors()).extracting(UserError::code)
            .containsExactly("INVALID_CREDENTIALS");
    }

    @Test
    void inactiveAccountIsOnlyRevealedAfterTheCorrectPassword() {
        assertThat(auth.signIn("kofi@example.test", "Wrong-Password-123", client()).errors())
            .extracting(UserError::code).containsExactly("INVALID_CREDENTIALS");
        assertThat(auth.signIn("kofi@example.test", SEED_PASSWORD, client()).errors())
            .extracting(UserError::code).containsExactly("ACCOUNT_INACTIVE");
    }

    @Test
    void invitedStaffCannotSignInBeforeAcceptingTheInvite() {
        assertThat(auth.signIn("invited@example.test", SEED_PASSWORD, client()).errors())
            .extracting(UserError::code).containsExactly("INVALID_CREDENTIALS");
    }

    @Test
    void signInIsRateLimitedPerIdentifier() {
        String email = uniqueEmail();
        for (int i = 0; i < 10; i++) {
            auth.signIn(email, "Wrong-Password-123", client());
        }
        assertThatThrownBy(() -> auth.signIn(email, "Wrong-Password-123", client()))
            .isInstanceOf(RateLimitedException.class);
    }

    // ------------------------------------------------------------- sessions

    @Test
    void refreshRotatesTokensAndReuseRevokesTheSession() {
        AuthResult signedUp = auth.signUp(signUp(uniqueEmail(), null), client());
        String firstRefresh = signedUp.session().refreshToken();

        AuthResult refreshed = auth.refresh(firstRefresh);
        assertThat(refreshed.errors()).isEmpty();
        assertThat(auth.authenticate(signedUp.session().accessToken())).isEmpty();
        assertThat(auth.authenticate(refreshed.session().accessToken())).isPresent();

        assertThat(auth.refresh(firstRefresh).errors()).extracting(UserError::code)
            .containsExactly("TOKEN_INVALID_OR_EXPIRED");
        assertThat(auth.authenticate(refreshed.session().accessToken())).as("reuse revokes the session").isEmpty();
        assertThat(auth.refresh(refreshed.session().refreshToken()).errors()).isNotEmpty();
    }

    @Test
    void refreshRejectsGarbage() {
        assertThat(auth.refresh(null).errors()).isNotEmpty();
        assertThat(auth.refresh("x".repeat(200)).errors()).isNotEmpty();
        assertThat(auth.refresh("unknown-token").errors()).extracting(UserError::code)
            .containsExactly("TOKEN_INVALID_OR_EXPIRED");
        assertThat(auth.authenticate(null)).isEmpty();
        assertThat(auth.authenticate(" ")).isEmpty();
        assertThat(auth.authenticate("x".repeat(200))).isEmpty();
    }

    @Test
    void signOutAndRevokeEndSessions() {
        String email = uniqueEmail();
        AuthResult first = auth.signUp(signUp(email, null), client());
        AuthResult second = auth.signIn(email, PASSWORD, client());
        CurrentPrincipal viaFirst = auth.authenticate(first.session().accessToken()).orElseThrow();
        CurrentPrincipal viaSecond = auth.authenticate(second.session().accessToken()).orElseThrow();

        assertThat(auth.sessions(viaFirst)).hasSize(2);
        assertThat(auth.revokeSession(viaFirst, viaSecond.sessionId())).isTrue();
        assertThat(auth.authenticate(second.session().accessToken())).isEmpty();
        assertThat(auth.revokeSession(viaFirst, viaSecond.sessionId())).as("already revoked").isFalse();
        assertThat(auth.signOut(viaFirst)).isTrue();
        assertThat(auth.authenticate(first.session().accessToken())).isEmpty();
    }

    @Test
    void customersCannotRevokeOtherPeoplesSessions() {
        CurrentPrincipal mallory = principalOf(auth.signUp(signUp(uniqueEmail(), null), client()));
        CurrentPrincipal victim = principalOf(auth.signUp(signUp(uniqueEmail(), null), client()));

        assertThat(auth.revokeSession(mallory, victim.sessionId())).isFalse();
    }

    // ------------------------------------------------------------- e-mail verification

    @Test
    void verifyEmailWithTheLinkToken() {
        String email = uniqueEmail();
        UUID accountId = auth.signUp(signUp(email, null), client()).session().accountId();
        String link = verificationEmailTo(email, Purpose.EMAIL_VERIFY).orElseThrow().link();

        assertThat(auth.verifyEmail(tokenOf(link), null, Optional.empty())).isEmpty();
        assertThat(emailVerified(accountId)).isTrue();
        assertThat(auth.verifyEmail(tokenOf(link), null, Optional.empty())).as("single use")
            .extracting(UserError::code).containsExactly("TOKEN_INVALID_OR_EXPIRED");
    }

    @Test
    void verifyEmailWithTheCodeForTheSignedInViewer() {
        String email = uniqueEmail();
        CurrentPrincipal viewer = principalOf(auth.signUp(signUp(email, null), client()));
        String code = verificationEmailTo(email, Purpose.EMAIL_VERIFY).orElseThrow().code();

        assertThat(auth.verifyEmail(null, code, Optional.of(viewer))).isEmpty();
        assertThat(emailVerified(viewer.accountId())).isTrue();
    }

    @Test
    void fiveWrongCodesBurnTheCodeAndTheLink() {
        String email = uniqueEmail();
        CurrentPrincipal viewer = principalOf(auth.signUp(signUp(email, null), client()));
        VerificationEmailRequested mail = verificationEmailTo(email, Purpose.EMAIL_VERIFY).orElseThrow();
        String wrong = mail.code().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(auth.verifyEmail(null, wrong, Optional.of(viewer))).extracting(UserError::code)
                .containsExactly("TOKEN_INVALID_OR_EXPIRED");
        }
        assertThat(auth.verifyEmail(null, mail.code(), Optional.of(viewer))).isNotEmpty();
        assertThat(auth.verifyEmail(tokenOf(mail.link()), null, Optional.empty())).isNotEmpty();
        assertThat(emailVerified(viewer.accountId())).isFalse();
    }

    @Test
    void verifyEmailNeedsExactlyOneOfTokenOrCodeAndCodeNeedsSignIn() {
        assertThat(auth.verifyEmail(null, null, Optional.empty())).extracting(UserError::code)
            .containsExactly("VALIDATION_FAILED");
        assertThat(auth.verifyEmail("t", "123456", Optional.empty())).extracting(UserError::code)
            .containsExactly("VALIDATION_FAILED");
        assertThat(auth.verifyEmail(null, "123456", Optional.empty())).extracting(UserError::code)
            .containsExactly("VALIDATION_FAILED");
    }

    @Test
    void resendIssuesANewCodeAndOldOnesStopWorking() {
        String email = uniqueEmail();
        CurrentPrincipal viewer = principalOf(auth.signUp(signUp(email, null), client()));
        String oldLink = verificationEmailTo(email, Purpose.EMAIL_VERIFY).orElseThrow().link();

        auth.resendEmailVerification(viewer, client());

        assertThat(events.stream(VerificationEmailRequested.class).filter(e -> e.to().equals(email))).hasSize(2);
        assertThat(auth.verifyEmail(tokenOf(oldLink), null, Optional.empty())).isNotEmpty();
    }

    @Test
    void resendDoesNothingForVerifiedAddresses() {
        CurrentPrincipal amina = principalOf(auth.signIn("amina@example.test", SEED_PASSWORD, client()));

        auth.resendEmailVerification(amina, client());

        assertThat(events.stream(VerificationEmailRequested.class)).isEmpty();
    }

    // ------------------------------------------------------------- passwords

    @Test
    void passwordResetWithLinkSignsOutEverywhere() {
        String email = uniqueEmail();
        AuthResult signedUp = auth.signUp(signUp(email, null), client());

        auth.requestPasswordReset(email.toUpperCase());
        String link = verificationEmailTo(email, Purpose.PASSWORD_RESET).orElseThrow().link();

        assertThat(auth.resetPassword(tokenOf(link), null, null, "Brand-New-Password-7")).isEmpty();
        assertThat(auth.authenticate(signedUp.session().accessToken())).isEmpty();
        assertThat(auth.signIn(email, PASSWORD, client()).errors()).isNotEmpty();
        assertThat(auth.signIn(email, "Brand-New-Password-7", client()).errors()).isEmpty();
    }

    @Test
    void passwordResetWithIdentifierAndCode() {
        String email = uniqueEmail();
        auth.signUp(signUp(email, null), client());
        auth.requestPasswordReset(email);
        String code = verificationEmailTo(email, Purpose.PASSWORD_RESET).orElseThrow().code();
        String wrong = code.equals("000000") ? "111111" : "000000";

        assertThat(auth.resetPassword(null, email, wrong, "Brand-New-Password-7")).extracting(UserError::code)
            .containsExactly("TOKEN_INVALID_OR_EXPIRED");
        assertThat(auth.resetPassword(null, email, code, "Brand-New-Password-7")).isEmpty();
        assertThat(auth.signIn(email, "Brand-New-Password-7", client()).errors()).isEmpty();
    }

    @Test
    void passwordResetRequestIsEnumerationSafe() {
        auth.requestPasswordReset(uniqueEmail());
        auth.requestPasswordReset("invited@example.test");

        assertThat(events.stream(VerificationEmailRequested.class)).isEmpty();
    }

    @Test
    void passwordResetValidatesInput() {
        assertThat(auth.resetPassword(null, null, null, "Brand-New-Password-7")).extracting(UserError::code)
            .containsExactly("VALIDATION_FAILED");
        assertThat(auth.resetPassword("token", "a@example.test", "123456", "Brand-New-Password-7"))
            .extracting(UserError::code).containsExactly("VALIDATION_FAILED");
        assertThat(auth.resetPassword("token", null, null, "short")).extracting(UserError::code)
            .containsExactly("PASSWORD_TOO_WEAK");
        assertThat(auth.resetPassword("unknown", null, null, "Brand-New-Password-7")).extracting(UserError::code)
            .containsExactly("TOKEN_INVALID_OR_EXPIRED");
        assertThat(auth.resetPassword(null, uniqueEmail(), "123456", "Brand-New-Password-7")).extracting(UserError::code)
            .containsExactly("TOKEN_INVALID_OR_EXPIRED");
    }

    @Test
    void changePasswordKeepsThisSessionAndEndsTheOthers() {
        String email = uniqueEmail();
        AuthResult first = auth.signUp(signUp(email, null), client());
        AuthResult second = auth.signIn(email, PASSWORD, client());
        CurrentPrincipal viewer = auth.authenticate(first.session().accessToken()).orElseThrow();

        assertThat(auth.changePassword(viewer, "Wrong-Password-123", "Another-Password-8")).extracting(UserError::code)
            .containsExactly("INVALID_CREDENTIALS");
        assertThat(auth.changePassword(viewer, PASSWORD, "short")).extracting(UserError::code)
            .containsExactly("PASSWORD_TOO_WEAK");
        assertThat(auth.changePassword(viewer, PASSWORD, "Another-Password-8")).isEmpty();
        assertThat(auth.authenticate(first.session().accessToken())).isPresent();
        assertThat(auth.authenticate(second.session().accessToken())).isEmpty();
    }

    // ------------------------------------------------------------- authorization (seeded roles)

    @Test
    void seededRolesHaveTheExpectedPermissions() {
        UUID org = jdbc.sql("select id from organization where slug = 'aurelle'").query(UUID.class).single();
        UUID accra = UUID.fromString("01970000-0000-7000-8001-000000001002");
        UUID kigali = UUID.fromString("01970000-0000-7000-8001-000000001001");

        assertThat(authorization.effectivePermissions(id("owner@example.test"), org, null))
            .contains("team.role.manage", "settings.shop.write", "finance.expense.write").hasSize(28);
        assertThat(authorization.effectivePermissions(id("manager@example.test"), org, null))
            .contains("orders.order.refund").doesNotContain("finance.expense.write", "team.member.manage");
        assertThat(authorization.effectivePermissions(id("packer@example.test"), org, null))
            .containsExactly("catalog.product.read");
        assertThat(authorization.effectivePermissions(id("packer@example.test"), org, accra))
            .containsExactlyInAnyOrder("catalog.product.read", "inventory.stock.adjust", "inventory.stock.read",
                "orders.order.read", "orders.order.update_status");
        assertThat(authorization.effectivePermissions(id("invited@example.test"), org, kigali))
            .contains("notifications.inbox.read", "orders.order.read").doesNotContain("orders.order.refund");
    }

    // ------------------------------------------------------------- helpers

    private Optional<VerificationEmailRequested> verificationEmailTo(String email, Purpose purpose) {
        return events.stream(VerificationEmailRequested.class)
            .filter(e -> e.to().equals(email) && e.purpose() == purpose)
            .reduce((first, second) -> second);
    }

    private CurrentPrincipal principalOf(AuthResult result) {
        return auth.authenticate(result.session().accessToken()).orElseThrow();
    }

    private java.util.Set<String> permissionsOf(UUID accountId) {
        UUID org = jdbc.sql("select organization_id from user_account where id = ?").param(accountId).query(UUID.class).single();
        return authorization.effectivePermissions(accountId, org, null);
    }

    private UUID id(String email) {
        return jdbc.sql("select id from user_account where email = ?").param(email).query(UUID.class).single();
    }

    private boolean emailVerified(UUID accountId) {
        return jdbc.sql("select email_verified_at is not null from user_account where id = ?").param(accountId)
            .query(Boolean.class).single();
    }

    private long count(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object param : params) {
            spec = spec.param(param);
        }
        return spec.query(Long.class).single();
    }

    private static String tokenOf(String link) {
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private static SignUp signUp(String email, String phone) {
        return new SignUp("Test Shopper", email, phone, PASSWORD, true, true, List.of("WOMEN"), "en-RW");
    }

    private static Client client() {
        return new Client("WEB", "Test browser", UUID.randomUUID().toString());
    }

    private static String uniqueEmail() {
        return "shopper-" + UUID.randomUUID() + "@example.test";
    }

    private static String uniquePhone() {
        return "+4477009" + String.format("%05d", (int) (Math.random() * 100_000));
    }
}
