package com.aurelle.identity.graphql;

import com.aurelle.common.ApplicationException;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.ErrorCode;
import com.aurelle.common.UserError;
import com.aurelle.identity.internal.AuthService;
import com.aurelle.identity.internal.AuthService.AuthResult;
import com.aurelle.identity.internal.AuthService.Client;
import com.aurelle.identity.internal.AuthService.SignUp;
import com.aurelle.identity.internal.ViewerService;
import com.aurelle.identity.internal.ViewerService.ProfileChanges;
import com.aurelle.identity.internal.ViewerService.ViewerView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Self-service account operations of contracts/graphql/01-identity.graphqls. Resolvers only map. */
@Controller
class AccountController {

    private final AuthService auth;
    private final ViewerService viewers;

    AccountController(AuthService auth, ViewerService viewers) {
        this.auth = auth;
        this.viewers = viewers;
    }

    // ------------------------------------------------------------- queries

    @QueryMapping
    ViewerView viewer() {
        return CurrentPrincipal.current().flatMap(p -> viewers.viewer(p.accountId())).orElse(null);
    }

    @QueryMapping
    List<DeviceSession> mySessions() {
        CurrentPrincipal principal = signedIn();
        return auth.sessions(principal).stream()
            .map(s -> new DeviceSession(s.id(), s.deviceLabel(), s.platform(), s.createdAt(), s.lastSeenAt(),
                s.id().equals(principal.sessionId())))
            .toList();
    }

    // ------------------------------------------------------------- sign-up / sign-in / sessions

    @MutationMapping
    AuthPayload signUp(@Argument Map<String, Object> input, @ContextValue(ClientContext.KEY) ClientContext client) {
        SignUp command = new SignUp(str(input, "displayName"), str(input, "email"), str(input, "phone"),
            str(input, "password"), Boolean.TRUE.equals(input.get("acceptTerms")),
            Boolean.TRUE.equals(input.get("marketingConsent")), strings(input, "shopFor"), str(input, "preferredLocale"));
        return authPayload(auth.signUp(command, clientOf(client, input)), client);
    }

    @MutationMapping
    AuthPayload signIn(@Argument Map<String, Object> input, @ContextValue(ClientContext.KEY) ClientContext client) {
        return authPayload(auth.signIn(str(input, "identifier"), str(input, "password"), clientOf(client, input)), client);
    }

    @MutationMapping
    AuthPayload refreshSession(@Argument Map<String, Object> input, @ContextValue(ClientContext.KEY) ClientContext client) {
        String token = client.browser() ? client.refreshCookie() : str(input, "refreshToken");
        return authPayload(auth.refresh(token), client);
    }

    @MutationMapping
    AckPayload signOut(@ContextValue(ClientContext.KEY) ClientContext client) {
        client.forgetSession();
        CurrentPrincipal.current().ifPresent(auth::signOut);
        return AckPayload.success();
    }

    @MutationMapping
    AckPayload revokeSession(@Argument Map<String, Object> input) {
        auth.revokeSession(signedIn(), UUID.fromString(str(input, "sessionId")));
        return AckPayload.success();
    }

    // ------------------------------------------------------------- verification and passwords

    @MutationMapping
    AckPayload verifyEmail(@Argument Map<String, Object> input) {
        return AckPayload.of(auth.verifyEmail(str(input, "token"), str(input, "code"), CurrentPrincipal.current()));
    }

    @MutationMapping
    AckPayload resendEmailVerification(@ContextValue(ClientContext.KEY) ClientContext client) {
        auth.resendEmailVerification(signedIn(), new Client(client.platform(), null, client.rateKey()));
        return AckPayload.success();
    }

    @MutationMapping
    AckPayload requestPasswordReset(@Argument Map<String, Object> input) {
        auth.requestPasswordReset(str(input, "identifier"));
        return AckPayload.success();
    }

    @MutationMapping
    AckPayload resetPassword(@Argument Map<String, Object> input) {
        return AckPayload.of(auth.resetPassword(str(input, "token"), str(input, "identifier"), str(input, "code"),
            str(input, "newPassword")));
    }

    @MutationMapping
    AckPayload changePassword(@Argument Map<String, Object> input) {
        return AckPayload.of(auth.changePassword(signedIn(), str(input, "currentPassword"), str(input, "newPassword")));
    }

    @MutationMapping
    ViewerPayload updateMyProfile(@Argument Map<String, Object> input) {
        CurrentPrincipal principal = signedIn();
        Object birth = input.get("birthDate");
        Object version = input.get("expectedVersion");
        List<UserError> errors = viewers.updateMyProfile(principal, new ProfileChanges(str(input, "displayName"),
            str(input, "givenName"), str(input, "familyName"), str(input, "phone"), (LocalDate) birth,
            input.containsKey("shopFor") ? strings(input, "shopFor") : null, str(input, "themePreference"),
            str(input, "preferredLocale"), (Boolean) input.get("marketingConsent"),
            version == null ? null : ((Number) version).longValue()));
        return new ViewerPayload(viewers.viewer(principal.accountId()).orElse(null), errors);
    }

    // ------------------------------------------------------------- mapping helpers

    AuthPayload authPayload(AuthResult result, ClientContext client) {
        if (result.session() == null) {
            if (client.browser()) {
                client.forgetSession();
            }
            return new AuthPayload(null, null, null, null, null, result.errors());
        }
        var session = result.session();
        ViewerView viewer = viewers.viewer(session.accountId()).orElse(null);
        if (client.browser()) {
            client.rememberSession(session);
            return new AuthPayload(viewer, null, null, null, null, List.of());
        }
        return new AuthPayload(viewer, session.accessToken(), session.accessExpiresAt(), session.refreshToken(),
            session.refreshExpiresAt(), List.of());
    }

    static CurrentPrincipal signedIn() {
        return CurrentPrincipal.current().orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static Client clientOf(ClientContext client, Map<String, Object> input) {
        String label = str(input, "deviceLabel");
        return new Client(client.platform(), label == null ? null : label.substring(0, Math.min(80, label.length())),
            client.rateKey());
    }

    static String str(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    static List<String> strings(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? List.of() : ((List<Object>) value).stream().map(Object::toString).toList();
    }

    // ------------------------------------------------------------- GraphQL types

    record AuthPayload(ViewerView viewer, String accessToken, Instant accessTokenExpiresAt, String refreshToken,
            Instant refreshTokenExpiresAt, List<UserError> errors) {
    }

    record AckPayload(boolean ok, List<UserError> errors) {

        static AckPayload success() {
            return new AckPayload(true, List.of());
        }

        static AckPayload of(List<UserError> errors) {
            return new AckPayload(errors.isEmpty(), errors);
        }
    }

    record ViewerPayload(ViewerView viewer, List<UserError> errors) {
    }

    record DeviceSession(UUID id, String deviceLabel, String platform, Instant createdAt, Instant lastSeenAt,
            boolean current) {
    }
}
