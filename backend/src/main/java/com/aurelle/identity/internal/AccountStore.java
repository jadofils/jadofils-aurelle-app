package com.aurelle.identity.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code user_account} and {@code customer_profile} rows. */
@Repository
class AccountStore {

    private static final String SELECT = """
        select id, organization_id, kind, email, email_verified_at is not null as email_verified, phone_e164,
               phone_verified_at is not null as phone_verified, password_hash, status, failed_sign_in_count,
               locked_until, theme_preference, preferred_locale
          from user_account
        """;

    private final JdbcClient jdbc;

    AccountStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<AccountRecord> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id and erased_at is null").param("id", id)
            .query(AccountStore::map).optional();
    }

    /** By e-mail (case-insensitive) when the identifier contains '@', otherwise by E.164 phone. */
    Optional<AccountRecord> findByIdentifier(UUID organizationId, String identifier) {
        String column = identifier.contains("@") ? "email" : "phone_e164";
        return jdbc.sql(SELECT + " where organization_id = :org and " + column + " = :identifier and erased_at is null")
            .param("org", organizationId)
            .param("identifier", identifier.trim())
            .query(AccountStore::map).optional();
    }

    boolean emailTaken(UUID organizationId, String email) {
        return jdbc.sql("select exists(select 1 from user_account where organization_id = :org and email = :email)")
            .param("org", organizationId).param("email", email).query(Boolean.class).single();
    }

    boolean phoneTaken(UUID organizationId, String phone) {
        return jdbc.sql("select exists(select 1 from user_account where organization_id = :org and phone_e164 = :phone)")
            .param("org", organizationId).param("phone", phone).query(Boolean.class).single();
    }

    UUID insertCustomer(NewCustomer customer) {
        UUID accountId = jdbc.sql("""
                insert into user_account (organization_id, kind, email, phone_e164, password_hash, password_changed_at,
                                          status, preferred_locale, marketing_consent_at, terms_accepted_at, terms_version)
                values (:org, 'CUSTOMER', :email, :phone, :hash, now(), 'ACTIVE', :locale,
                        case when :marketing then now() end, now(), :terms)
                returning id
                """)
            .param("org", customer.organizationId())
            .param("email", customer.email())
            .param("phone", customer.phone())
            .param("hash", customer.passwordHash())
            .param("locale", customer.preferredLocale())
            .param("marketing", customer.marketingConsent())
            .param("terms", customer.termsVersion())
            .query(UUID.class).single();
        jdbc.sql("""
                insert into customer_profile (organization_id, user_account_id, display_name, shop_for)
                values (:org, :account, :name, :shopFor)
                """)
            .param("org", customer.organizationId())
            .param("account", accountId)
            .param("name", customer.displayName())
            .param("shopFor", customer.shopFor())
            .update();
        return accountId;
    }

    /** Counts a failed sign-in; locks the account temporarily at the limit. */
    void recordFailedSignIn(UUID id, int maxFailures, Duration lockDuration) {
        jdbc.sql("""
                update user_account
                   set failed_sign_in_count = failed_sign_in_count + 1,
                       status = case when failed_sign_in_count + 1 >= :max and status = 'ACTIVE' then 'LOCKED' else status end,
                       locked_until = case when failed_sign_in_count + 1 >= :max then now() + make_interval(secs => :lock)
                                           else locked_until end,
                       updated_at = now()
                 where id = :id
                """)
            .param("id", id).param("max", maxFailures).param("lock", lockDuration.toSeconds())
            .update();
    }

    void recordSuccessfulSignIn(UUID id) {
        jdbc.sql("""
                update user_account
                   set failed_sign_in_count = 0, locked_until = null, last_sign_in_at = now(),
                       status = case when status = 'LOCKED' then 'ACTIVE' else status end, updated_at = now()
                 where id = :id
                """).param("id", id).update();
    }

    void setPassword(UUID id, String passwordHash) {
        jdbc.sql("""
                update user_account
                   set password_hash = :hash, password_changed_at = now(), failed_sign_in_count = 0, locked_until = null,
                       status = case when status = 'LOCKED' then 'ACTIVE' else status end,
                       updated_at = now(), version = version + 1
                 where id = :id
                """).param("id", id).param("hash", passwordHash).update();
    }

    void markEmailVerified(UUID id) {
        jdbc.sql("""
                update user_account set email_verified_at = coalesce(email_verified_at, now()), updated_at = now()
                 where id = :id
                """).param("id", id).update();
    }

    /** Display name from the customer or staff profile. */
    Optional<String> displayName(UUID accountId) {
        return jdbc.sql("""
                select coalesce((select display_name from customer_profile where user_account_id = :id),
                                (select display_name from staff_profile where user_account_id = :id))
                """).param("id", accountId).query(String.class).optional();
    }

    private static AccountRecord map(ResultSet rs, int row) throws SQLException {
        OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
        return new AccountRecord(
            rs.getObject("id", UUID.class),
            rs.getObject("organization_id", UUID.class),
            rs.getString("kind"),
            rs.getString("email"),
            rs.getBoolean("email_verified"),
            rs.getString("phone_e164"),
            rs.getBoolean("phone_verified"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getInt("failed_sign_in_count"),
            lockedUntil == null ? null : lockedUntil.toInstant(),
            rs.getString("theme_preference"),
            rs.getString("preferred_locale"));
    }

    record AccountRecord(UUID id, UUID organizationId, String kind, String email, boolean emailVerified,
            String phone, boolean phoneVerified, String passwordHash, String status, int failedSignInCount,
            Instant lockedUntil, String themePreference, String preferredLocale) {

        boolean lockedAt(Instant now) {
            return "LOCKED".equals(status) && lockedUntil != null && lockedUntil.isAfter(now);
        }
    }

    record NewCustomer(UUID organizationId, String displayName, String email, String phone, String passwordHash,
            boolean marketingConsent, String preferredLocale, String[] shopFor, String termsVersion) {
    }
}
