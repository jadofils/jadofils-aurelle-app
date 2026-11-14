package com.aurelle.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.common.CurrentPrincipal;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionAuthenticationFilterTest {

    private static final CurrentPrincipal PRINCIPAL =
        new CurrentPrincipal(UUID.randomUUID(), UUID.randomUUID(), CurrentPrincipal.CUSTOMER, UUID.randomUUID());

    private final List<String> presented = new ArrayList<>();
    private final SessionAuthenticationFilter filter = new SessionAuthenticationFilter(token -> {
        presented.add(token);
        return "good".equals(token) ? Optional.of(PRINCIPAL) : Optional.empty();
    }, List.of("https://shop.example.test"));

    @AfterEach
    void clear() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void bearerTokenAuthenticates() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good");

        assertThat(run(request)).contains(PRINCIPAL);
    }

    @Test
    void unknownTokenLeavesTheRequestAnonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad");

        assertThat(run(request)).isEmpty();
        assertThat(presented).containsExactly("bad");
    }

    @Test
    void malformedAuthorizationHeadersAreIgnored() throws Exception {
        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic abc");
        MockHttpServletRequest empty = new MockHttpServletRequest();
        empty.addHeader("Authorization", "Bearer ");

        assertThat(run(basic)).isEmpty();
        assertThat(run(empty)).isEmpty();
        assertThat(presented).isEmpty();
    }

    @Test
    void cookieAuthenticatesOnlyFromAnAllowedOrigin() throws Exception {
        MockHttpServletRequest allowed = cookieRequest("good");
        allowed.addHeader("Origin", "https://shop.example.test");
        MockHttpServletRequest foreign = cookieRequest("good");
        foreign.addHeader("Origin", "https://evil.example.test");
        MockHttpServletRequest noOrigin = cookieRequest("good");

        assertThat(run(allowed)).contains(PRINCIPAL);
        assertThat(run(foreign)).as("CSRF: foreign origin").isEmpty();
        assertThat(run(noOrigin)).isEmpty();
    }

    @Test
    void otherOrBlankCookiesAreIgnored() throws Exception {
        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setCookies(new Cookie("theme", "dark"));
        other.addHeader("Origin", "https://shop.example.test");
        MockHttpServletRequest blank = cookieRequest(" ");
        blank.addHeader("Origin", "https://shop.example.test");
        MockHttpServletRequest none = new MockHttpServletRequest();
        none.addHeader("Origin", "https://shop.example.test");

        assertThat(run(other)).isEmpty();
        assertThat(run(blank)).isEmpty();
        assertThat(run(none)).isEmpty();
        assertThat(presented).isEmpty();
    }

    private static MockHttpServletRequest cookieRequest(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SessionAuthenticationFilter.ACCESS_COOKIE, value));
        return request;
    }

    private Optional<CurrentPrincipal> run(MockHttpServletRequest request) throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("chain continues").isNotNull();
        return CurrentPrincipal.current();
    }
}
