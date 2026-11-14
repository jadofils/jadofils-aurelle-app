package com.aurelle.identity.graphql;

import com.aurelle.identity.SessionAuthenticationFilter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Adds {@link ClientContext} to every GraphQL request and writes session cookies chosen by the
 * resolvers onto the HTTP response (browsers only; decision D5).
 */
@Component
class ClientInterceptor implements WebGraphQlInterceptor {

    private final boolean secureCookies;

    ClientInterceptor(@Value("${aurelle.identity.secure-cookies}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        boolean browser = request.getHeaders().getOrigin() != null;
        HttpCookie refresh = request.getCookies().getFirst(SessionAuthenticationFilter.REFRESH_COOKIE);
        ClientContext client = new ClientContext(browser, rateKey(request.getRemoteAddress()),
            refresh == null ? null : refresh.getValue(), secureCookies);
        request.configureExecutionInput((input, builder) ->
            builder.graphQLContext(context -> context.put(ClientContext.KEY, client)).build());
        return chain.next(request).doOnNext(response -> client.cookiesToSet().forEach(cookie ->
            response.getResponseHeaders().add(HttpHeaders.SET_COOKIE, cookie.toString())));
    }

    static String rateKey(InetSocketAddress address) {
        String host = address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(host.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
