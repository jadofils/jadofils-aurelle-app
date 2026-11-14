package com.aurelle.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.IntegrationTest;
import com.aurelle.audit.AuditLog;
import com.aurelle.common.ApplicationException;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthorizationIT extends IntegrationTest {

    private static final UUID ACCRA = UUID.fromString("01970000-0000-7000-8001-000000001002");

    @Autowired
    Authorization authorization;
    @Autowired
    AuditLog audit;
    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void guestsAreUnauthenticated() {
        assertThatThrownBy(() -> authorization.require("orders.order.read"))
            .isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void missingPermissionIsForbiddenAndGrantedPermissionPasses() {
        CurrentPrincipal packer = signInAs("packer@example.test");

        assertThatThrownBy(() -> authorization.require("orders.order.read"))
            .as("packer's binding is at the Accra warehouse, not organization-wide")
            .isInstanceOfSatisfying(ApplicationException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(authorization.require("orders.order.read", ACCRA)).isEqualTo(packer);
        assertThat(authorization.require("catalog.product.read")).as("organization-wide override").isEqualTo(packer);
    }

    @Test
    void missingSystemRoleFailsFast() {
        UUID owner = accountId("owner@example.test");
        UUID org = organizationId();

        assertThatThrownBy(() -> authorization.bindSystemRole(owner, org, "no-such-role", null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void auditUsesTheRequestCorrelationIdWhenPresent() {
        MDC.put("correlationId", "corr-123");
        audit.record(new AuditLog.Entry(organizationId(), null, "SYSTEM", "test.audit.correlation", "test", null,
            "SUCCESS", Map.of("k", "v")));

        assertThat(jdbc.sql("select correlation_id from audit_event where action = 'test.audit.correlation'")
            .query(String.class).single()).isEqualTo("corr-123");
    }

    @Test
    void auditEventsCannotBeChangedOrDeleted() {
        assertThatThrownBy(() -> jdbc.sql("update audit_event set outcome = 'SUCCESS'").update())
            .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from audit_event").update())
            .hasMessageContaining("append-only");
    }

    @Test
    void auditEntryNormalizesDetails() {
        assertThat(new AuditLog.Entry(UUID.randomUUID(), null, "SYSTEM", "a.b", "t", null, "SUCCESS", null).details())
            .isEmpty();
    }

    private CurrentPrincipal signInAs(String email) {
        CurrentPrincipal principal = new CurrentPrincipal(accountId(email), organizationId(), CurrentPrincipal.STAFF,
            UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        return principal;
    }

    private UUID accountId(String email) {
        return jdbc.sql("select id from user_account where email = ?").param(email).query(UUID.class).single();
    }

    private UUID organizationId() {
        return jdbc.sql("select id from organization where slug = 'aurelle'").query(UUID.class).single();
    }
}
