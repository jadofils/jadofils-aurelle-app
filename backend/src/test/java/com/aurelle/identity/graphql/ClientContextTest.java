package com.aurelle.identity.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.identity.internal.AuthService.IssuedSession;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientContextTest {

    private static final IssuedSession SESSION = new IssuedSession(UUID.randomUUID(), "access", Instant.now().plusSeconds(900),
        "refresh", Instant.now().plusSeconds(3600));

    @Test
    void nativeClientsNeverGetCookies() {
        ClientContext client = new ClientContext(false, "k", null, true);

        client.rememberSession(SESSION);
        client.forgetSession();

        assertThat(client.cookiesToSet()).isEmpty();
        assertThat(client.platform()).isEqualTo("ANDROID");
    }

    @Test
    void browsersGetStrictHttpOnlyCookies() {
        ClientContext client = new ClientContext(true, "k", null, false);

        client.rememberSession(SESSION);

        assertThat(client.platform()).isEqualTo("WEB");
        assertThat(client.cookiesToSet()).hasSize(2).allSatisfy(cookie -> {
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.isSecure()).as("secure-cookies=false for plain-http local setups").isFalse();
            assertThat(cookie.getSameSite()).isEqualTo("Strict");
        });
    }

    @Test
    void alreadyExpiredSessionsGetZeroMaxAge() {
        ClientContext client = new ClientContext(true, "k", null, true);

        client.rememberSession(new IssuedSession(UUID.randomUUID(), "a", Instant.now().minusSeconds(5), "r",
            Instant.now().minusSeconds(5)));

        assertThat(client.cookiesToSet()).allSatisfy(c -> assertThat(c.getMaxAge().isZero()).isTrue());
    }

    @Test
    void rateKeyIsAHashAndCopesWithUnknownAddresses() {
        String known = ClientInterceptor.rateKey(new InetSocketAddress("203.0.113.7", 443));

        assertThat(known).hasSize(24).doesNotContain("203.0.113.7");
        assertThat(ClientInterceptor.rateKey(null)).hasSize(24).isNotEqualTo(known);
        assertThat(ClientInterceptor.rateKey(InetSocketAddress.createUnresolved("unresolved.example.test", 1)))
            .isEqualTo(ClientInterceptor.rateKey(null));
    }
}
