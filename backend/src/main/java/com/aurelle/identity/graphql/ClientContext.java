package com.aurelle.identity.graphql;

import com.aurelle.identity.internal.AuthService.IssuedSession;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseCookie;

/**
 * Per-request facts about the caller, put into the GraphQL context by {@link ClientInterceptor}.
 *
 * @param browser       request had an Origin header: tokens go only into HttpOnly cookies
 * @param rateKey       hashed client address for rate limiting (never the raw IP)
 * @param refreshCookie refresh token from the browser cookie, if any
 * @param secureCookies Secure flag for cookies
 */
record ClientContext(boolean browser, String rateKey, String refreshCookie, boolean secureCookies,
        List<ResponseCookie> cookiesToSet) {

    static final String KEY = "aurelle.client";

    ClientContext(boolean browser, String rateKey, String refreshCookie, boolean secureCookies) {
        this(browser, rateKey, refreshCookie, secureCookies, new ArrayList<>());
    }

    String platform() {
        return browser ? "WEB" : "ANDROID";
    }

    /** Browsers: set both tokens as HttpOnly cookies. Native clients: nothing (tokens go in the body). */
    void rememberSession(IssuedSession session) {
        if (!browser) {
            return;
        }
        cookiesToSet.add(cookie(com.aurelle.identity.SessionAuthenticationFilter.ACCESS_COOKIE, session.accessToken(),
            java.time.Duration.between(java.time.Instant.now(), session.accessExpiresAt())));
        cookiesToSet.add(cookie(com.aurelle.identity.SessionAuthenticationFilter.REFRESH_COOKIE, session.refreshToken(),
            java.time.Duration.between(java.time.Instant.now(), session.refreshExpiresAt())));
    }

    /** Browsers: expire both cookies. */
    void forgetSession() {
        if (browser) {
            cookiesToSet.add(cookie(com.aurelle.identity.SessionAuthenticationFilter.ACCESS_COOKIE, "", java.time.Duration.ZERO));
            cookiesToSet.add(cookie(com.aurelle.identity.SessionAuthenticationFilter.REFRESH_COOKIE, "", java.time.Duration.ZERO));
        }
    }

    private ResponseCookie cookie(String name, String value, java.time.Duration maxAge) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secureCookies)
            .sameSite("Strict")
            .path("/")
            .maxAge(maxAge.isNegative() ? java.time.Duration.ZERO : maxAge)
            .build();
    }
}
