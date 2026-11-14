package com.aurelle.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentPrincipalTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptyForGuestsAndForeignPrincipals() {
        assertThat(CurrentPrincipal.current()).isEmpty();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of()));
        assertThat(CurrentPrincipal.current()).isEmpty();
    }

    @Test
    void returnsTheSignedInPrincipal() {
        var staff = new CurrentPrincipal(UUID.randomUUID(), UUID.randomUUID(), CurrentPrincipal.STAFF, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(staff, null, List.of()));

        assertThat(CurrentPrincipal.current()).contains(staff);
        assertThat(staff.isStaff()).isTrue();
        assertThat(new CurrentPrincipal(UUID.randomUUID(), UUID.randomUUID(), "CUSTOMER", UUID.randomUUID()).isStaff())
            .isFalse();
    }

    @Test
    void requiresAllFields() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new CurrentPrincipal(null, id, "STAFF", id)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CurrentPrincipal(id, null, "STAFF", id)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CurrentPrincipal(id, id, null, id)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CurrentPrincipal(id, id, "STAFF", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void userErrorCopiesThePath() {
        assertThat(new UserError("X", "m", null).field()).isEmpty();
        assertThat(UserError.of("X", "m").field()).isEmpty();
        assertThat(UserError.of("X", "m", "input", "email").field()).containsExactly("input", "email");
        assertThat(Map.of("k", UserError.of("X", "m"))).isNotEmpty();
    }
}
