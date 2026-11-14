package com.aurelle.authorization.internal;

import com.aurelle.authorization.Authorization;
import com.aurelle.common.ApplicationException;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.ErrorCode;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Evaluates permissions in one recursive SQL query (no N+1, no cache to go stale after a change). */
@Component
class JdbcAuthorization implements Authorization {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuthorization.class);

    private static final String EFFECTIVE = """
        with recursive bound(role_id) as (
            select rb.role_id
              from role_binding rb
              join role r on r.id = rb.role_id and r.archived_at is null
             where rb.user_account_id = :account
               and rb.revoked_at is null
               and (rb.expires_at is null or rb.expires_at > now())
               and ((rb.scope_type = 'ORGANIZATION' and rb.scope_id = :org)
                 or (rb.scope_type = 'LOCATION' and rb.scope_id = :location))
        ), roles(role_id) as (
            select role_id from bound
            union
            select ri.parent_role_id
              from role_inheritance ri
              join roles on ri.child_role_id = roles.role_id
              join role p on p.id = ri.parent_role_id and p.archived_at is null
        ), grants(permission_key, effect) as (
            select rp.permission_key, rp.effect from role_permission rp join roles on roles.role_id = rp.role_id
            union all
            select o.permission_key, o.effect
              from principal_permission_override o
             where o.user_account_id = :account
               and ((o.scope_type = 'ORGANIZATION' and o.scope_id = :org)
                 or (o.scope_type = 'LOCATION' and o.scope_id = :location))
        )
        select permission_key
          from grants
         group by permission_key
        having bool_or(effect = 'ALLOW') and not bool_or(effect = 'DENY')
         order by permission_key
        """;

    private final JdbcClient jdbc;

    JdbcAuthorization(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> effectivePermissions(UUID accountId, UUID organizationId, UUID locationId) {
        return new TreeSet<>(jdbc.sql(EFFECTIVE)
            .param("account", accountId)
            .param("org", organizationId)
            .param("location", locationId, java.sql.Types.OTHER)
            .query(String.class).list());
    }

    @Override
    public CurrentPrincipal require(String permission) {
        return require(permission, null);
    }

    @Override
    public CurrentPrincipal require(String permission, UUID locationId) {
        CurrentPrincipal principal = CurrentPrincipal.current()
            .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        if (!effectivePermissions(principal.accountId(), principal.organizationId(), locationId).contains(permission)) {
            log.atInfo().addKeyValue("permission", permission).addKeyValue("account", principal.accountId())
                .log("Permission denied");
            throw new ApplicationException(ErrorCode.FORBIDDEN);
        }
        return principal;
    }

    @Override
    public void bindSystemRole(UUID accountId, UUID organizationId, String roleKey, UUID grantedBy) {
        int bound = jdbc.sql("""
                insert into role_binding (user_account_id, role_id, scope_type, scope_id, granted_by)
                select :account, r.id, 'ORGANIZATION', :org, :grantedBy
                  from role r where r.organization_id = :org and r.key = :key and r.kind = 'SYSTEM'
                """)
            .param("account", accountId).param("org", organizationId).param("key", roleKey)
            .param("grantedBy", grantedBy, java.sql.Types.OTHER)
            .update();
        if (bound != 1) {
            throw new IllegalStateException("System role '" + roleKey + "' is missing for organization " + organizationId);
        }
    }
}
