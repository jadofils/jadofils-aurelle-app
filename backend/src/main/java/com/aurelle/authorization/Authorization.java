package com.aurelle.authorization;

import com.aurelle.common.CurrentPrincipal;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the authorization module. Roles, grants and bindings are data; DENY wins.
 * Effective permissions = ALLOW grants of roles bound at organization scope (and at the given
 * location), plus the roles they inherit from, plus ALLOW overrides, minus every DENY.
 */
public interface Authorization {

    /** Effective permission keys of an account at organization scope plus {@code locationId} (may be null). */
    Set<String> effectivePermissions(UUID accountId, UUID organizationId, UUID locationId);

    /**
     * Throws {@code ApplicationException} UNAUTHENTICATED for guests and FORBIDDEN when the caller lacks
     * {@code permission}. Use at every use case/resolver boundary.
     */
    CurrentPrincipal require(String permission);

    /** Like {@link #require(String)} for an operation on a resource at {@code locationId}. */
    CurrentPrincipal require(String permission, UUID locationId);

    /** Binds a system role (e.g. {@code customer}) at organization scope. */
    void bindSystemRole(UUID accountId, UUID organizationId, String roleKey, UUID grantedBy);
}
