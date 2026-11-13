package com.aurelle.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Append-only business audit trail ({@code audit_event}). Never pass passwords, tokens or codes in details. */
public interface AuditLog {

    void record(Entry entry);

    /**
     * @param organizationId tenant scope
     * @param actorId        who acted, or null when anonymous/system
     * @param actorKind      {@code STAFF}, {@code CUSTOMER}, {@code SYSTEM} or {@code ANONYMOUS}
     * @param action         e.g. {@code auth.sign_in}, {@code team.member.invite}
     * @param resourceType   e.g. {@code user_account}, {@code role}
     * @param resourceId     target id/key, or null
     * @param outcome        {@code SUCCESS}, {@code DENIED} or {@code FAILED}
     * @param details        small redacted map, or empty
     */
    record Entry(UUID organizationId, UUID actorId, String actorKind, String action, String resourceType,
            String resourceId, String outcome, Map<String, Object> details) {

        public Entry {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(actorKind, "actorKind");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(outcome, "outcome");
            details = details == null ? Map.of() : Map.copyOf(details);
        }
    }
}
