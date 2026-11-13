package com.aurelle.identity.internal;

import com.aurelle.common.CurrentPrincipal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code auth_session} rows. Every state change is a single conditional statement (safe under races). */
@Repository
public class SessionStore {

    private final JdbcClient jdbc;

    SessionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    UUID create(UUID accountId, String platform, String deviceLabel, byte[] accessHash, Instant accessExpires,
            byte[] refreshHash, Instant refreshExpires) {
        return jdbc.sql("""
                insert into auth_session (user_account_id, platform, device_label, access_token_hash, access_expires_at,
                                          refresh_token_hash, refresh_expires_at)
                values (:account, :platform, :label, :access, :accessExp, :refresh, :refreshExp)
                returning id
                """)
            .param("account", accountId)
            .param("platform", platform)
            .param("label", deviceLabel)
            .param("access", accessHash)
            .param("accessExp", utc(accessExpires))
            .param("refresh", refreshHash)
            .param("refreshExp", utc(refreshExpires))
            .query(UUID.class).single();
    }

    /** The principal for a live access token of an account that may still use the shop. */
    Optional<CurrentPrincipal> principalForAccessToken(byte[] accessHash) {
        return jdbc.sql("""
                update auth_session s
                   set last_seen_at = now()
                  from user_account a
                 where s.access_token_hash = :hash
                   and s.revoked_at is null
                   and s.access_expires_at > now()
                   and a.id = s.user_account_id
                   and a.status in ('ACTIVE', 'LOCKED')
                   and a.erased_at is null
                returning a.id as account_id, a.organization_id, a.kind, s.id as session_id
                """)
            .param("hash", accessHash)
            .query((rs, row) -> new CurrentPrincipal(
                rs.getObject("account_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("kind"),
                rs.getObject("session_id", UUID.class)))
            .optional();
    }

    /**
     * Rotates the tokens of the session holding {@code refreshHash}. Empty when the refresh token is
     * unknown, expired or revoked; if it is a previously rotated token, the session is revoked (reuse).
     */
    Optional<UUID> rotate(byte[] refreshHash, byte[] newAccess, Instant accessExpires, byte[] newRefresh,
            Instant refreshExpires) {
        Optional<UUID> rotated = jdbc.sql("""
                update auth_session s
                   set previous_refresh_token_hash = s.refresh_token_hash,
                       refresh_token_hash = :newRefresh, refresh_expires_at = :refreshExp,
                       access_token_hash = :newAccess, access_expires_at = :accessExp, last_seen_at = now()
                  from user_account a
                 where s.refresh_token_hash = :old and s.revoked_at is null and s.refresh_expires_at > now()
                   and a.id = s.user_account_id and a.status in ('ACTIVE', 'LOCKED') and a.erased_at is null
                returning s.user_account_id
                """)
            .param("old", refreshHash)
            .param("newRefresh", newRefresh)
            .param("refreshExp", utc(refreshExpires))
            .param("newAccess", newAccess)
            .param("accessExp", utc(accessExpires))
            .query(UUID.class).optional();
        if (rotated.isEmpty()) {
            jdbc.sql("""
                    update auth_session set revoked_at = now(), revoked_reason = 'REFRESH_REUSE'
                     where previous_refresh_token_hash = :old and revoked_at is null
                    """).param("old", refreshHash).update();
        }
        return rotated;
    }

    boolean revoke(UUID sessionId, UUID accountId, String reason) {
        return jdbc.sql("""
                update auth_session set revoked_at = now(), revoked_reason = :reason
                 where id = :id and user_account_id = :account and revoked_at is null
                """)
            .param("id", sessionId).param("account", accountId).param("reason", reason)
            .update() == 1;
    }

    int revokeAllExcept(UUID accountId, UUID keepSessionId, String reason) {
        return jdbc.sql("""
                update auth_session set revoked_at = now(), revoked_reason = :reason
                 where user_account_id = :account and revoked_at is null and (:keep::uuid is null or id <> :keep)
                """)
            .param("account", accountId).param("keep", keepSessionId).param("reason", reason)
            .update();
    }

    List<SessionView> listActive(UUID accountId) {
        return jdbc.sql("""
                select id, device_label, platform, created_at, last_seen_at
                  from auth_session
                 where user_account_id = :account and revoked_at is null and refresh_expires_at > now()
                 order by last_seen_at desc, id desc
                 limit 50
                """)
            .param("account", accountId)
            .query((rs, row) -> new SessionView(
                rs.getObject("id", UUID.class),
                rs.getString("device_label"),
                rs.getString("platform"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("last_seen_at", OffsetDateTime.class).toInstant()))
            .list();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public record SessionView(UUID id, String deviceLabel, String platform, Instant createdAt, Instant lastSeenAt) {
    }
}
