package com.aurelle.identity.internal;

import com.aurelle.authorization.Authorization;
import com.aurelle.common.ApplicationException;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.ErrorCode;
import com.aurelle.common.UserError;
import com.aurelle.organization.LocationSummary;
import com.aurelle.organization.OrganizationDirectory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in viewer: profile, preferences and navigation permissions. */
@Service
public class ViewerService {

    private static final Set<String> AUDIENCES = Set.of("MEN", "WOMEN", "BOYS", "GIRLS", "KIDS_UNISEX", "UNISEX");
    private static final Set<String> THEMES = Set.of("SYSTEM", "DARK", "LIGHT");

    private final JdbcClient jdbc;
    private final Authorization authorization;
    private final OrganizationDirectory organizations;

    ViewerService(JdbcClient jdbc, Authorization authorization, OrganizationDirectory organizations) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.organizations = organizations;
    }

    @Transactional(readOnly = true)
    public Optional<ViewerView> viewer(UUID accountId) {
        return jdbc.sql("""
                select a.id, a.organization_id, a.kind, a.email, a.email_verified_at is not null as email_verified,
                       a.phone_e164, a.phone_verified_at is not null as phone_verified, a.theme_preference,
                       a.preferred_locale, a.marketing_consent_at is not null and a.marketing_consent_withdrawn_at is null
                       as marketing,
                       c.id as c_id, c.display_name as c_name, c.given_name, c.family_name, c.birth_date, c.shop_for,
                       c.version as c_version,
                       s.id as s_id, s.display_name as s_name, s.job_title, s.default_location_id
                  from user_account a
                  left join customer_profile c on c.user_account_id = a.id
                  left join staff_profile s on s.user_account_id = a.id
                 where a.id = :id and a.erased_at is null
                """)
            .param("id", accountId)
            .query((rs, row) -> {
                UUID locationId = rs.getObject("default_location_id", UUID.class);
                LocationSummary location = locationId == null ? null : organizations.location(locationId).orElse(null);
                UUID customerId = rs.getObject("c_id", UUID.class);
                CustomerProfileView customer = customerId == null ? null : new CustomerProfileView(customerId,
                    rs.getString("c_name"), rs.getString("given_name"), rs.getString("family_name"),
                    rs.getObject("birth_date", LocalDate.class),
                    Arrays.asList((String[]) rs.getArray("shop_for").getArray()), rs.getBoolean("marketing"),
                    rs.getLong("c_version"));
                UUID staffId = rs.getObject("s_id", UUID.class);
                StaffProfileView staff = staffId == null ? null
                    : new StaffProfileView(staffId, rs.getString("s_name"), rs.getString("job_title"), location);
                UUID org = rs.getObject("organization_id", UUID.class);
                List<String> permissions = List.copyOf(authorization.effectivePermissions(accountId, org, locationId));
                return new ViewerView(accountId, rs.getString("kind"), rs.getString("email"),
                    rs.getBoolean("email_verified"), rs.getString("phone_e164"), rs.getBoolean("phone_verified"),
                    customer != null ? customer.displayName() : staff != null ? staff.displayName() : "",
                    null, rs.getString("theme_preference"), rs.getString("preferred_locale"), permissions,
                    customer, staff);
            })
            .optional();
    }

    /** Updates the viewer's profile and preferences; CONFLICT when {@code expectedVersion} is stale. */
    @Transactional
    public List<UserError> updateMyProfile(CurrentPrincipal principal, ProfileChanges changes) {
        List<UserError> errors = validate(changes);
        if (!errors.isEmpty()) {
            return errors;
        }
        if (changes.phone() != null && !changes.phone().isBlank() && jdbc.sql("""
                select exists(select 1 from user_account where organization_id = :org and phone_e164 = :phone and id <> :id)
                """).param("org", principal.organizationId()).param("phone", changes.phone()).param("id", principal.accountId())
                .query(Boolean.class).single()) {
            return List.of(UserError.of("PHONE_TAKEN", "Another account already uses this phone number.", "input", "phone"));
        }
        jdbc.sql("""
                update user_account
                   set theme_preference = coalesce(cast(:theme as text), theme_preference),
                       preferred_locale = coalesce(cast(:locale as text), preferred_locale),
                       phone_verified_at = case when cast(:phone as text) is not null and cast(:phone as text) is distinct from phone_e164 then null
                                                else phone_verified_at end,
                       phone_e164 = coalesce(cast(:phone as text), phone_e164),
                       marketing_consent_at = case when cast(:marketing as boolean) = true and (marketing_consent_at is null
                                                   or marketing_consent_withdrawn_at is not null) then now()
                                                   else marketing_consent_at end,
                       marketing_consent_withdrawn_at = case when cast(:marketing as boolean) = false and marketing_consent_at is not null
                                                             then now() when cast(:marketing as boolean) = true then null
                                                             else marketing_consent_withdrawn_at end,
                       updated_at = now(), version = version + 1
                 where id = :id
                """)
            .param("theme", changes.themePreference())
            .param("locale", blankToNull(changes.preferredLocale()))
            .param("phone", blankToNull(changes.phone()))
            .param("marketing", changes.marketingConsent(), java.sql.Types.BOOLEAN)
            .param("id", principal.accountId())
            .update();
        if (principal.isStaff()) {
            if (changes.displayName() != null) {
                jdbc.sql("update staff_profile set display_name = :name, updated_at = now(), version = version + 1 where user_account_id = :id")
                    .param("name", changes.displayName().trim()).param("id", principal.accountId()).update();
            }
            return List.of();
        }
        int updated = jdbc.sql("""
                update customer_profile
                   set display_name = coalesce(cast(:name as text), display_name),
                       given_name = coalesce(cast(:given as text), given_name),
                       family_name = coalesce(cast(:family as text), family_name),
                       birth_date = coalesce(cast(:birth as date), birth_date),
                       shop_for = coalesce(cast(:shopFor as text[]), shop_for),
                       updated_at = now(), version = version + 1
                 where user_account_id = :id and (:expected::bigint is null or version = :expected)
                """)
            .param("name", blankToNull(changes.displayName()))
            .param("given", blankToNull(changes.givenName()))
            .param("family", blankToNull(changes.familyName()))
            .param("birth", changes.birthDate())
            .param("shopFor", changes.shopFor() == null ? null : changes.shopFor().toArray(String[]::new))
            .param("expected", changes.expectedVersion(), java.sql.Types.BIGINT)
            .param("id", principal.accountId())
            .update();
        if (updated == 0) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        return List.of();
    }

    private static List<UserError> validate(ProfileChanges changes) {
        List<UserError> errors = new ArrayList<>();
        if (changes.displayName() != null && (changes.displayName().isBlank() || changes.displayName().length() > 120)) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter your name (up to 120 characters).", "input", "displayName"));
        }
        if (changes.phone() != null && !changes.phone().isBlank() && !Credentials.isPhone(changes.phone())) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter the phone number with country code, e.g. +250788123456.", "input", "phone"));
        }
        if (changes.shopFor() != null && !AUDIENCES.containsAll(changes.shopFor())) {
            errors.add(UserError.of("VALIDATION_FAILED", "Choose from Men, Women, Boys, Girls or Children.", "input", "shopFor"));
        }
        if (changes.themePreference() != null && !THEMES.contains(changes.themePreference())) {
            errors.add(UserError.of("VALIDATION_FAILED", "Choose System, Dark or Light.", "input", "themePreference"));
        }
        if (changes.birthDate() != null && (changes.birthDate().isAfter(LocalDate.now())
                || changes.birthDate().isBefore(LocalDate.of(1900, 1, 2)))) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter a real date of birth.", "input", "birthDate"));
        }
        return errors;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ProfileChanges(String displayName, String givenName, String familyName, String phone,
            LocalDate birthDate, List<String> shopFor, String themePreference, String preferredLocale,
            Boolean marketingConsent, Long expectedVersion) {
    }

    public record ViewerView(UUID id, String kind, String email, boolean emailVerified, String phone,
            boolean phoneVerified, String displayName, String avatarUrl, String themePreference,
            String preferredLocale, List<String> permissions, CustomerProfileView customerProfile,
            StaffProfileView staffProfile) {
    }

    public record CustomerProfileView(UUID id, String displayName, String givenName, String familyName,
            LocalDate birthDate, List<String> shopFor, boolean marketingConsent, long version) {
    }

    public record StaffProfileView(UUID id, String displayName, String jobTitle, LocationSummary defaultLocation) {
    }
}
