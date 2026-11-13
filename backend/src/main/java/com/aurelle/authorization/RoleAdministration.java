package com.aurelle.authorization;

import com.aurelle.common.UserError;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Role, binding and override management (team screens). Callers authorize first; changes are audited by them. */
public interface RoleAdministration {

    List<PermissionView> permissions();

    /** Non-archived roles of the organization, system roles first, then by name. */
    List<RoleView> roles(UUID organizationId);

    RoleResult upsertRole(UUID organizationId, RoleInput input);

    RoleResult archiveRole(UUID organizationId, UUID roleId, long expectedVersion);

    /** Active bindings of each account (revoked/expired excluded). */
    List<BindingView> bindings(UUID accountId);

    /** Replaces the account's bindings at one scope (location, or organization when {@code locationId} is null). */
    List<UserError> replaceBindings(UUID organizationId, UUID accountId, List<UUID> roleIds, UUID locationId, UUID actorId);

    List<GrantView> overrides(UUID accountId);

    List<UserError> replaceOverrides(UUID organizationId, UUID accountId, List<GrantInput> overrides, UUID actorId);

    /** Accounts with an active binding to {@code roleId} at any scope. */
    List<UUID> accountsWithRole(UUID roleId);

    /** Accounts with an active binding to the system {@code owner} role at organization scope. */
    List<UUID> ownerAccounts(UUID organizationId);

    record PermissionView(String key, String description, String group) {
    }

    record GrantView(PermissionView permission, String effect) {
    }

    record GrantInput(String key, String effect) {
    }

    record RoleView(UUID id, String key, String name, String description, String kind, List<GrantView> grants,
            List<UUID> inheritsFromIds, List<String> effectivePermissions, long version) {
    }

    record RoleInput(UUID roleId, String key, String name, String description, List<GrantInput> grants,
            List<UUID> inheritsFromRoleIds, Long expectedVersion) {
    }

    record RoleResult(RoleView role, List<UserError> errors) {
    }

    record BindingView(UUID id, UUID roleId, String scopeType, UUID scopeId, Instant grantedAt, Instant expiresAt) {
    }
}
