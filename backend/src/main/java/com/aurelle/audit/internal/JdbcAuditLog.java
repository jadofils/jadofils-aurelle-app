package com.aurelle.audit.internal;

import com.aurelle.audit.AuditLog;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Inserts into {@code audit_event} within the caller's transaction (UPDATE/DELETE are blocked by trigger). */
@Component
class JdbcAuditLog implements AuditLog {

    static final String CORRELATION_ID = "correlationId";

    private final JdbcClient jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    JdbcAuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Entry entry) {
        String correlationId = MDC.get(CORRELATION_ID);
        jdbc.sql("""
                insert into audit_event (organization_id, actor_id, actor_kind, action, resource_type, resource_id,
                                         scope_type, scope_id, outcome, correlation_id, details)
                values (:org, :actor, :actorKind, :action, :type, :resource, 'ORGANIZATION', :org, :outcome,
                        :correlation, cast(:details as jsonb))
                """)
            .param("org", entry.organizationId())
            .param("actor", entry.actorId())
            .param("actorKind", entry.actorKind())
            .param("action", entry.action())
            .param("type", entry.resourceType())
            .param("resource", entry.resourceId())
            .param("outcome", entry.outcome())
            .param("correlation", correlationId == null ? UUID.randomUUID().toString() : correlationId)
            .param("details", entry.details().isEmpty() ? null : json.writeValueAsString(entry.details()))
            .update();
    }
}
