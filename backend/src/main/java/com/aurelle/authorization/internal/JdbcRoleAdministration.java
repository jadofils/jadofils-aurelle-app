package com.aurelle.authorization.internal;

import com.aurelle.authorization.RoleAdministration;
import com.aurelle.common.ApplicationException;
import com.aurelle.common.ErrorCode;
import com.aurelle.common.UserError;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcRoleAdministration implements RoleAdministration {

    private static final Pattern ROLE_KEY = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");

    private final JdbcClient jdbc;

    JdbcRoleAdministration(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionView> permissions() {
        return jdbc.sql("select key, description, permission_group from permission order by permission_group, key")
            .query((rs, row) -> new PermissionView(rs.getString("key"), rs.getString("description"),
                rs.getString("permission_group")))
            .list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleView> roles(UUID organizationId) {
        Map<String, PermissionView> catalogue = new HashMap<>();
        permissions().forEach(p -> catalogue.put(p.key(), p));
        record Row(UUID id, String key, String name, String description, String kind, long version) {
        }
        List<Row> rows = jdbc.sql("""
                select id, key, name, description, kind, version from role
                 where organization_id = :org and archived_at is null
                 order by kind desc, name, id
                """)
            .param("org", organizationId)
            .query((rs, row) -> new Row(rs.getObject("id", UUID.class), rs.getString("key"), rs.getString("name"),
                rs.getString("description"), rs.getString("kind"), rs.getLong("version")))
            .list();
        Map<UUID, List<GrantView>> grants = new HashMap<>();
        jdbc.sql("""
                select rp.role_id, rp.permission_key, rp.effect from role_permission rp
                  join role r on r.id = rp.role_id where r.organization_id = :org order by rp.permission_key
                """)
            .param("org", organizationId)
            .query(rs -> {
                grants.computeIfAbsent(rs.getObject("role_id", UUID.class), k -> new ArrayList<>())
                    .add(new GrantView(catalogue.get(rs.getString("permission_key")), rs.getString("effect")));
            });
        Map<UUID, List<UUID>> parents = new HashMap<>();
        jdbc.sql("""
                select ri.child_role_id, ri.parent_role_id from role_inheritance ri
                  join role p on p.id = ri.parent_role_id and p.archived_at is null
                 where p.organization_id = :org order by ri.parent_role_id
                """)
            .param("org", organizationId)
            .query(rs -> {
                parents.computeIfAbsent(rs.getObject("child_role_id", UUID.class), k -> new ArrayList<>())
                    .add(rs.getObject("parent_role_id", UUID.class));
            });
        return rows.stream().map(r -> new RoleView(r.id(), r.key(), r.name(), r.description(), r.kind(),
            grants.getOrDefault(r.id(), List.of()), parents.getOrDefault(r.id(), List.of()),
            effective(r.id(), grants, parents), r.version())).toList();
    }

    /** ALLOW grants of the role and its ancestors, minus any DENY among them. */
    private static List<String> effective(UUID roleId, Map<UUID, List<GrantView>> grants, Map<UUID, List<UUID>> parents) {
        Set<UUID> seen = new HashSet<>();
        Deque<UUID> todo = new ArrayDeque<>(List.of(roleId));
        Set<String> allow = new TreeSet<>();
        Set<String> deny = new HashSet<>();
        while (!todo.isEmpty()) {
            UUID current = todo.pop();
            if (seen.add(current)) {
                for (GrantView grant : grants.getOrDefault(current, List.of())) {
                    ("DENY".equals(grant.effect()) ? deny : allow).add(grant.permission().key());
                }
                todo.addAll(parents.getOrDefault(current, List.of()));
            }
        }
        allow.removeAll(deny);
        return List.copyOf(allow);
    }

    @Override
    @Transactional
    public RoleResult upsertRole(UUID organizationId, RoleInput input) {
        lockRoles(organizationId);
        List<UserError> errors = validate(input);
        if (!errors.isEmpty()) {
            return new RoleResult(null, errors);
        }
        List<UUID> parentIds = input.inheritsFromRoleIds() == null ? List.of() : List.copyOf(input.inheritsFromRoleIds());
        UUID roleId = input.roleId();
        if (roleId != null) {
            Map<String, Object> existing = jdbc.sql("select key, kind, version from role where id = :id and organization_id = :org and archived_at is null")
                .param("id", roleId).param("org", organizationId).query().singleRow();
            if ("SYSTEM".equals(existing.get("kind")) && !existing.get("key").equals(input.key())) {
                return failed("SYSTEM_ROLE_PROTECTED", "System roles keep their key.", "input", "key");
            }
            if (input.expectedVersion() == null || ((Number) existing.get("version")).longValue() != input.expectedVersion()) {
                throw new ApplicationException(ErrorCode.CONFLICT);
            }
        }
        if (keyTaken(organizationId, input.key(), roleId)) {
            return failed("VALIDATION_FAILED", "Another role already uses this key.", "input", "key");
        }
        long knownParents = parentIds.isEmpty() ? 0 : jdbc.sql("""
                select count(*) from role where organization_id = :org and archived_at is null and id in (:ids)
                """).param("org", organizationId).param("ids", parentIds).query(Long.class).single();
        if (knownParents != new HashSet<>(parentIds).size()) {
            return failed("VALIDATION_FAILED", "Choose roles that exist.", "input", "inheritsFromRoleIds");
        }
        if (roleId != null && createsCycle(roleId, parentIds)) {
            return failed("ROLE_CYCLE", "A role cannot inherit from itself, directly or through other roles.",
                "input", "inheritsFromRoleIds");
        }
        if (roleId == null) {
            roleId = jdbc.sql("""
                    insert into role (organization_id, key, name, description, kind)
                    values (:org, :key, :name, :description, 'CUSTOM') returning id
                    """)
                .param("org", organizationId).param("key", input.key()).param("name", input.name().trim())
                .param("description", input.description())
                .query(UUID.class).single();
        } else {
            jdbc.sql("""
                    update role set name = :name, description = :description, updated_at = now(), version = version + 1
                     where id = :id
                    """)
                .param("id", roleId).param("name", input.name().trim()).param("description", input.description())
                .update();
            jdbc.sql("delete from role_permission where role_id = :id").param("id", roleId).update();
            jdbc.sql("delete from role_inheritance where child_role_id = :id").param("id", roleId).update();
        }
        for (GrantInput grant : input.grants()) {
            jdbc.sql("insert into role_permission (role_id, permission_key, effect) values (:role, :key, :effect)")
                .param("role", roleId).param("key", grant.key()).param("effect", grant.effect()).update();
        }
        for (UUID parent : new HashSet<>(parentIds)) {
            jdbc.sql("insert into role_inheritance (child_role_id, parent_role_id) values (:child, :parent)")
                .param("child", roleId).param("parent", parent).update();
        }
        UUID saved = roleId;
        return new RoleResult(roles(organizationId).stream().filter(r -> r.id().equals(saved)).findFirst().orElseThrow(),
            List.of());
    }

    @Override
    @Transactional
    public RoleResult archiveRole(UUID organizationId, UUID roleId, long expectedVersion) {
        lockRoles(organizationId);
        Map<String, Object> role = jdbc.sql("select kind, version from role where id = :id and organization_id = :org and archived_at is null")
            .param("id", roleId).param("org", organizationId).query().listOfRows().stream().findFirst()
            .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND));
        if ("SYSTEM".equals(role.get("kind"))) {
            return failed("SYSTEM_ROLE_PROTECTED", "System roles cannot be archived.", "input", "roleId");
        }
        if (((Number) role.get("version")).longValue() != expectedVersion) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        boolean inUse = jdbc.sql("""
                select exists(select 1 from role_binding where role_id = :id and revoked_at is null
                               and (expires_at is null or expires_at > now()))
                """).param("id", roleId).query(Boolean.class).single();
        if (inUse) {
            return failed("ROLE_IN_USE", "Remove this role from every team member first.", "input", "roleId");
        }
        RoleView before = roles(organizationId).stream().filter(r -> r.id().equals(roleId)).findFirst().orElseThrow();
        jdbc.sql("update role set archived_at = now(), updated_at = now(), version = version + 1 where id = :id")
            .param("id", roleId).update();
        return new RoleResult(before, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BindingView> bindings(UUID accountId) {
        return jdbc.sql("""
                select id, role_id, scope_type, scope_id, granted_at, expires_at from role_binding
                 where user_account_id = :account and revoked_at is null and (expires_at is null or expires_at > now())
                 order by granted_at, id
                """)
            .param("account", accountId)
            .query((rs, row) -> new BindingView(rs.getObject("id", UUID.class), rs.getObject("role_id", UUID.class),
                rs.getString("scope_type"), rs.getObject("scope_id", UUID.class),
                rs.getObject("granted_at", OffsetDateTime.class).toInstant(),
                rs.getObject("expires_at", OffsetDateTime.class) == null ? null
                    : rs.getObject("expires_at", OffsetDateTime.class).toInstant()))
            .list();
    }

    @Override
    @Transactional
    public List<UserError> replaceBindings(UUID organizationId, UUID accountId, List<UUID> roleIds, UUID locationId,
            UUID actorId) {
        Set<UUID> wanted = new HashSet<>(roleIds);
        long valid = wanted.isEmpty() ? 0 : jdbc.sql("""
                select count(*) from role where organization_id = :org and archived_at is null and key <> 'customer'
                   and id in (:ids)
                """).param("org", organizationId).param("ids", wanted).query(Long.class).single();
        if (valid != wanted.size()) {
            return List.of(UserError.of("VALIDATION_FAILED", "Choose staff roles that exist.", "input", "roleIds"));
        }
        String scopeType = locationId == null ? "ORGANIZATION" : "LOCATION";
        UUID scopeId = locationId == null ? organizationId : locationId;
        jdbc.sql("""
                update role_binding set revoked_at = now(), revoked_by = :actor
                 where user_account_id = :account and scope_type = :scopeType and scope_id = :scopeId
                   and revoked_at is null and not (role_id = any(:wanted))
                """)
            .param("actor", actorId).param("account", accountId).param("scopeType", scopeType)
            .param("scopeId", scopeId).param("wanted", wanted.toArray(UUID[]::new), java.sql.Types.ARRAY)
            .update();
        for (UUID roleId : wanted) {
            jdbc.sql("""
                    insert into role_binding (user_account_id, role_id, scope_type, scope_id, granted_by)
                    values (:account, :role, :scopeType, :scopeId, :actor)
                    on conflict (user_account_id, role_id, scope_type, scope_id) where revoked_at is null do nothing
                    """)
                .param("account", accountId).param("role", roleId).param("scopeType", scopeType)
                .param("scopeId", scopeId).param("actor", actorId)
                .update();
        }
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrantView> overrides(UUID accountId) {
        return jdbc.sql("""
                select o.permission_key, o.effect, p.description, p.permission_group
                  from principal_permission_override o join permission p on p.key = o.permission_key
                 where o.user_account_id = :account order by o.permission_key
                """)
            .param("account", accountId)
            .query((rs, row) -> new GrantView(new PermissionView(rs.getString("permission_key"),
                rs.getString("description"), rs.getString("permission_group")), rs.getString("effect")))
            .list();
    }

    @Override
    @Transactional
    public List<UserError> replaceOverrides(UUID organizationId, UUID accountId, List<GrantInput> overrides, UUID actorId) {
        List<UserError> errors = validateGrants(overrides, "overrides");
        if (!errors.isEmpty()) {
            return errors;
        }
        jdbc.sql("delete from principal_permission_override where user_account_id = :account")
            .param("account", accountId).update();
        Map<String, String> unique = new LinkedHashMap<>();
        overrides.forEach(o -> unique.merge(o.key(), o.effect(), (a, b) -> "DENY".equals(a) || "DENY".equals(b) ? "DENY" : a));
        unique.forEach((key, effect) -> jdbc.sql("""
                insert into principal_permission_override (user_account_id, permission_key, effect, scope_type, scope_id, granted_by)
                values (:account, :key, :effect, 'ORGANIZATION', :org, :actor)
                """)
            .param("account", accountId).param("key", key).param("effect", effect).param("org", organizationId)
            .param("actor", actorId).update());
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> accountsWithRole(UUID roleId) {
        return jdbc.sql("""
                select distinct user_account_id from role_binding
                 where role_id = :role and revoked_at is null and (expires_at is null or expires_at > now())
                """).param("role", roleId).query(UUID.class).list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> ownerAccounts(UUID organizationId) {
        return jdbc.sql("""
                select distinct rb.user_account_id from role_binding rb
                  join role r on r.id = rb.role_id and r.key = 'owner' and r.kind = 'SYSTEM'
                 where r.organization_id = :org and rb.scope_type = 'ORGANIZATION' and rb.scope_id = :org
                   and rb.revoked_at is null and (rb.expires_at is null or rb.expires_at > now())
                """)
            .param("org", organizationId).query(UUID.class).list();
    }

    // ------------------------------------------------------------- helpers

    private void lockRoles(UUID organizationId) {
        jdbc.sql("select id from role where organization_id = :org order by id for update")
            .param("org", organizationId).query(UUID.class).list();
    }

    private boolean keyTaken(UUID organizationId, String key, UUID exceptRoleId) {
        return jdbc.sql("""
                select exists(select 1 from role where organization_id = :org and key = :key
                               and (:except::uuid is null or id <> :except))
                """).param("org", organizationId).param("key", key).param("except", exceptRoleId, java.sql.Types.OTHER)
            .query(Boolean.class).single();
    }

    /** True if {@code roleId} is reachable from any new parent through existing inheritance (excluding its own edges). */
    private boolean createsCycle(UUID roleId, List<UUID> parentIds) {
        if (parentIds.contains(roleId)) {
            return true;
        }
        if (parentIds.isEmpty()) {
            return false;
        }
        return jdbc.sql("""
                with recursive ancestors(id) as (
                    select unnest(:parents::uuid[])
                    union
                    select ri.parent_role_id from role_inheritance ri join ancestors a on ri.child_role_id = a.id
                     where ri.child_role_id <> :role
                )
                select exists(select 1 from ancestors where id = :role)
                """)
            .param("parents", parentIds.toArray(UUID[]::new), java.sql.Types.ARRAY)
            .param("role", roleId)
            .query(Boolean.class).single();
    }

    private List<UserError> validate(RoleInput input) {
        List<UserError> errors = new ArrayList<>();
        if (input.key() == null || input.key().length() > 40 || !ROLE_KEY.matcher(input.key()).matches()) {
            errors.add(UserError.of("VALIDATION_FAILED", "Use lowercase letters, digits and dashes, e.g. weekend-lead.", "input", "key"));
        }
        if (input.name() == null || input.name().isBlank() || input.name().length() > 80) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter a role name (up to 80 characters).", "input", "name"));
        }
        errors.addAll(validateGrants(input.grants(), "grants"));
        return errors;
    }

    private List<UserError> validateGrants(List<GrantInput> grants, String field) {
        if (grants.stream().anyMatch(g -> !EFFECTS.contains(g.effect()))) {
            return List.of(UserError.of("VALIDATION_FAILED", "Each permission is ALLOW or DENY.", "input", field));
        }
        Set<String> keys = new HashSet<>();
        grants.forEach(g -> keys.add(g.key()));
        long known = keys.isEmpty() ? 0 : jdbc.sql("select count(*) from permission where key in (:keys)")
            .param("keys", keys).query(Long.class).single();
        if (known != keys.size()) {
            return List.of(UserError.of("UNKNOWN_PERMISSION", "One of the permissions does not exist.", "input", field));
        }
        return List.of();
    }

    private static RoleResult failed(String code, String message, String... field) {
        return new RoleResult(null, List.of(UserError.of(code, message, field)));
    }
}
