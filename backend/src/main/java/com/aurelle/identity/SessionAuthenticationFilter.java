package com.aurelle.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests (decision D5):
 * <ul>
 *   <li>Native clients: {@code Authorization: Bearer <accessToken>}.</li>
 *   <li>Browsers: the HttpOnly cookie {@value #ACCESS_COOKIE}. Because browsers attach cookies
 *       automatically, a cookie only counts when the request's {@code Origin} is allowed (CSRF).</li>
 * </ul>
 * An invalid or expired token simply leaves the request anonymous; operations decide what needs a
 * signed-in caller.
 */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String ACCESS_COOKIE = "__Host-aurelle_at";
    public static final String REFRESH_COOKIE = "__Host-aurelle_rt";
    private static final String BEARER = "Bearer ";

    private final SessionAuthenticator authenticator;
    private final List<String> allowedOrigins;

    public SessionAuthenticationFilter(SessionAuthenticator authenticator, List<String> allowedOrigins) {
        this.authenticator = authenticator;
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        accessToken(request)
            .flatMap(authenticator::authenticate)
            .ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
        chain.doFilter(request, response);
    }

    private Optional<String> accessToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER) && header.length() > BEARER.length()) {
            return Optional.of(header.substring(BEARER.length()).trim());
        }
        String cookie = cookie(request, ACCESS_COOKIE);
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (cookie != null && origin != null && allowedOrigins.contains(origin)) {
            return Optional.of(cookie);
        }
        return Optional.empty();
    }

    static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
