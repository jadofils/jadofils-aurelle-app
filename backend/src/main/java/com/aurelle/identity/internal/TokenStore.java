package com.aurelle.identity.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code auth_token}: single-use links and 6-digit codes. */
@Repository
class TokenStore {

    static final int MAX_CODE_ATTEMPTS = 5;

    private final JdbcClient jdbc;

    TokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Issues a new token; older unused tokens of the same purpose stop working. */
    void issue(UUID accountId, String purpose, byte[] tokenHash, byte[] codeHash, Instant expiresAt) {
        jdbc.sql("""
                update auth_token set used_at = now()
                 where user_account_id = :account and purpose = :purpose and used_at is null
                """).param("account", accountId).param("purpose", purpose).update();
        jdbc.sql("""
                insert into auth_token (user_account_id, purpose, token_hash, code_hash, expires_at)
                values (:account, :purpose, :token, :code, :expires)
                """)
            .param("account", accountId)
            .param("purpose", purpose)
            .param("token", tokenHash)
            .param("code", codeHash)
            .param("expires", expiresAt.atOffset(ZoneOffset.UTC))
            .update();
    }

    Optional<OpenToken> findOpenByTokenHash(String purpose, byte[] tokenHash) {
        return jdbc.sql("""
                select id, user_account_id, token_hash, code_hash from auth_token
                 where purpose = :purpose and token_hash = :hash and used_at is null and expires_at > now()
                """)
            .param("purpose", purpose).param("hash", tokenHash)
            .query((rs, row) -> new OpenToken(rs.getObject("id", UUID.class), rs.getObject("user_account_id", UUID.class),
                rs.getBytes("token_hash"), rs.getBytes("code_hash")))
            .optional();
    }

    Optional<OpenToken> findLatestOpen(UUID accountId, String purpose) {
        return jdbc.sql("""
                select id, user_account_id, token_hash, code_hash from auth_token
                 where user_account_id = :account and purpose = :purpose and used_at is null and expires_at > now()
                 order by created_at desc, id desc limit 1
                """)
            .param("account", accountId).param("purpose", purpose)
            .query((rs, row) -> new OpenToken(rs.getObject("id", UUID.class), rs.getObject("user_account_id", UUID.class),
                rs.getBytes("token_hash"), rs.getBytes("code_hash")))
            .optional();
    }

    /** Counts a wrong code; the last allowed attempt burns the token. */
    void recordWrongCode(UUID tokenId) {
        jdbc.sql("""
                update auth_token
                   set failed_attempts = least(failed_attempts + 1, :max),
                       used_at = case when failed_attempts + 1 >= :max then now() else used_at end
                 where id = :id and used_at is null
                """).param("id", tokenId).param("max", MAX_CODE_ATTEMPTS).update();
    }

    /** Marks the token used; false if someone else used it first (concurrent requests). */
    boolean consume(UUID tokenId) {
        return jdbc.sql("update auth_token set used_at = now() where id = :id and used_at is null and expires_at > now()")
            .param("id", tokenId).update() == 1;
    }

    record OpenToken(UUID id, UUID accountId, byte[] tokenHash, byte[] codeHash) {
    }
}
