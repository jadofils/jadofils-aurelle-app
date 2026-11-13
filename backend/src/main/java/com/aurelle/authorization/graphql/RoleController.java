package com.aurelle.authorization.graphql;

import com.aurelle.audit.AuditLog;
import com.aurelle.authorization.Authorization;
import com.aurelle.authorization.RoleAdministration;
import com.aurelle.authorization.RoleAdministration.BindingView;
import com.aurelle.authorization.RoleAdministration.GrantInput;
import com.aurelle.authorization.RoleAdministration.PermissionView;
import com.aurelle.authorization.RoleAdministration.RoleInput;
import com.aurelle.authorization.RoleAdministration.RoleResult;
import com.aurelle.authorization.RoleAdministration.RoleView;
import com.aurelle.common.CurrentPrincipal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Roles and permissions (contracts/graphql/01-identity.graphqls). Authorizes, maps, audits. */
@Controller
class RoleController {

    private final Authorization authorization;
    private final RoleAdministration roles;
    private final AuditLog audit;

    RoleController(Authorization authorization, RoleAdministration roles, AuditLog audit) {
        this.authorization = authorization;
        this.roles = roles;
        this.audit = audit;
    }

    @QueryMapping
    List<RoleView> roles() {
        return roles.roles(authorization.require("team.role.read").organizationId());
    }

    @QueryMapping
    List<PermissionView> permissions() {
        authorization.require("team.role.read");
        return roles.permissions();
    }

    @MutationMapping
    RoleResult upsertRole(@Argument Map<String, Object> input) {
        CurrentPrincipal actor = authorization.require("team.role.manage");
        Object version = input.get("expectedVersion");
        Object roleId = input.get("roleId");
        RoleResult result = roles.upsertRole(actor.organizationId(), new RoleInput(
            roleId == null ? null : UUID.fromString(roleId.toString()), (String) input.get("key"),
            (String) input.get("name"), (String) input.get("description"), grants(input.get("grants")),
            ids(input.get("inheritsFromRoleIds")), version == null ? null : ((Number) version).longValue()));
        auditRole(actor, "team.role.update", result);
        return result;
    }

    @MutationMapping
    RoleResult archiveRole(@Argument Map<String, Object> input) {
        CurrentPrincipal actor = authorization.require("team.role.manage");
        RoleResult result = roles.archiveRole(actor.organizationId(), UUID.fromString(input.get("roleId").toString()),
            ((Number) input.get("expectedVersion")).longValue());
        auditRole(actor, "team.role.archive", result);
        return result;
    }

    /** Role.inheritsFrom for all roles in one go (no N+1). */
    @BatchMapping(typeName = "Role", field = "inheritsFrom")
    Map<RoleView, List<RoleView>> inheritsFrom(List<RoleView> views) {
        Map<UUID, RoleView> byId = rolesById();
        return views.stream().collect(Collectors.toMap(Function.identity(),
            view -> view.inheritsFromIds().stream().map(byId::get).filter(java.util.Objects::nonNull).toList(),
            (a, b) -> a, LinkedHashMap::new));
    }

    /** RoleBinding.role for all bindings in one go (no N+1). */
    @BatchMapping(typeName = "RoleBinding", field = "role")
    Map<BindingView, RoleView> role(List<BindingView> bindings) {
        Map<UUID, RoleView> byId = rolesById();
        return bindings.stream().filter(b -> byId.containsKey(b.roleId()))
            .collect(Collectors.toMap(Function.identity(), b -> byId.get(b.roleId()), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<UUID, RoleView> rolesById() {
        UUID org = CurrentPrincipal.current().orElseThrow().organizationId();
        return roles.roles(org).stream().collect(Collectors.toMap(RoleView::id, Function.identity()));
    }

    private void auditRole(CurrentPrincipal actor, String action, RoleResult result) {
        if (result.role() != null) {
            audit.record(new AuditLog.Entry(actor.organizationId(), actor.accountId(), "STAFF", action, "role",
                result.role().key(), "SUCCESS", Map.of()));
        }
    }

    @SuppressWarnings("unchecked")
    static List<GrantInput> grants(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((Collection<Map<String, Object>>) value).stream()
            .map(g -> new GrantInput((String) g.get("key"), g.get("effect").toString())).toList();
    }

    @SuppressWarnings("unchecked")
    static List<UUID> ids(Object value) {
        return value == null ? List.of()
            : ((Collection<Object>) value).stream().map(v -> UUID.fromString(v.toString())).toList();
    }
}
