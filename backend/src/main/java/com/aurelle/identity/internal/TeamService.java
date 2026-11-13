package com.aurelle.identity.internal;

import com.aurelle.audit.AuditLog;
import com.aurelle.authorization.Authorization;
import com.aurelle.authorization.RoleAdministration;
import com.aurelle.authorization.RoleAdministration.GrantInput;
import com.aurelle.common.ApplicationException;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.ErrorCode;
import com.aurelle.common.UserError;
import com.aurelle.identity.internal.AuthService.AuthResult;
import com.aurelle.identity.internal.AuthService.Client;
import com.aurelle.messaging.VerificationEmails.Purpose;
import com.aurelle.organization.OrganizationDirectory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Team screens: staff list, invites, status, roles and per-member overrides. Every change is audited. */
@Service
public class TeamService {

    public static final int MAX_PAGE = 100;
    private static final Set<String> EDITABLE_STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final JdbcClient jdbc;
    private final Authorization authorization;
    private final RoleAdministration roles;
    private final OrganizationDirectory organizations;
    private final AuditLog audit;
    private final SecureTokens secure;
    private final TokenStore tokens;
    private final SessionStore sessions;
    private final AccountStore accounts;
    private final PasswordHasher hasher;
    private final AuthService auth;
    private final ApplicationEventPublisher events;
    private final IdentityProperties properties;
    private final Clock clock;
    private final String publicWebUrl;

    TeamService(JdbcClient jdbc, Authorization authorization, RoleAdministration roles,
            OrganizationDirectory organizations, AuditLog audit, SecureTokens secure, TokenStore tokens,
            SessionStore sessions, AccountStore accounts, PasswordHasher hasher, AuthService auth,
            ApplicationEventPublisher events, IdentityProperties properties, Clock clock,
            @Value("${aurelle.web.public-url}") String publicWebUrl) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.roles = roles;
        this.organizations = organizations;
        this.audit = audit;
        this.secure = secure;
        this.tokens = tokens;
        this.sessions = sessions;
        this.accounts = accounts;
        this.hasher = hasher;
        this.auth = auth;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.publicWebUrl = publicWebUrl;
    }

    // ------------------------------------------------------------- queries

    /** Staff ordered by display name then id; cursor = opaque (name, id). */
    @Transactional(readOnly = true)
    public StaffPage staffMembers(int first, String after, String search, List<String> statuses, UUID roleId) {
        CurrentPrincipal principal = authorization.require("team.member.read");
        int limit = Math.max(1, Math.min(MAX_PAGE, first));
        String[] cursor = decodeCursor(after);
        String like = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase().replace("%", "\\%") + "%";
        List<StaffMemberView> rows = jdbc.sql("""
                select a.id from user_account a join staff_profile s on s.user_account_id = a.id
                 where a.organization_id = :org and a.kind = 'STAFF' and a.erased_at is null
                   and (cast(:like as text) is null or lower(s.display_name) like :like or lower(a.email::text) like :like)
                   and (cast(:statuses as text[]) is null or a.status = any(:statuses))
                   and (cast(:withRole as uuid[]) is null or a.id = any(:withRole))
                   and (cast(:afterName as text) is null or (s.display_name, a.id) > (:afterName, cast(:afterId as uuid)))
                 order by s.display_name, a.id
                 limit :limit
                """)
            .param("org", principal.organizationId())
            .param("like", like)
            .param("statuses", statuses == null || statuses.isEmpty() ? null : statuses.toArray(String[]::new))
            .param("afterName", cursor == null ? null : cursor[0])
            .param("afterId", cursor == null ? null : cursor[1])
            .param("withRole", roleId == null ? null : roles.accountsWithRole(roleId).toArray(UUID[]::new))
            .param("limit", limit + 1)
            .query(UUID.class).list().stream()
            .map(id -> staffMemberView(principal.organizationId(), id).orElseThrow())
            .toList();
        boolean hasNext = rows.size() > limit;
        List<StaffMemberView> page = hasNext ? rows.subList(0, limit) : rows;
        long total = jdbc.sql("select count(*) from user_account where organization_id = :org and kind = 'STAFF' and erased_at is null")
            .param("org", principal.organizationId()).query(Long.class).single();
        return new StaffPage(page, hasNext, after != null, total);
    }

    @Transactional(readOnly = true)
    public Optional<StaffMemberView> staffMember(UUID accountId) {
        CurrentPrincipal principal = authorization.require("team.member.read");
        return staffMemberView(principal.organizationId(), accountId);
    }

    @Transactional(readOnly = true)
    public List<com.aurelle.organization.LocationSummary> locations() {
        CurrentPrincipal principal = authorization.require("team.member.read");
        return organizations.locations(principal.organizationId());
    }

    // ------------------------------------------------------------- invites

    @Transactional
    public StaffResult invite(String displayName, String email, String jobTitle, List<UUID> roleIds, UUID locationId) {
        CurrentPrincipal actor = authorization.require("team.member.manage");
        UUID org = actor.organizationId();
        String normalized = Credentials.normalizeEmail(email);
        List<UserError> errors = new ArrayList<>();
        if (displayName == null || displayName.isBlank() || displayName.length() > 120) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter the person's name.", "input", "displayName"));
        }
        if (!Credentials.isEmail(normalized)) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter a valid e-mail address.", "input", "email"));
        }
        if (roleIds == null || roleIds.isEmpty()) {
            errors.add(UserError.of("VALIDATION_FAILED", "Choose at least one role.", "input", "roleIds"));
        }
        errors.addAll(checkLocation(locationId, org));
        if (errors.isEmpty() && accounts.emailTaken(org, normalized)) {
            errors.add(UserError.of("EMAIL_TAKEN", "Someone with this e-mail already has an account.", "input", "email"));
        }
        if (!errors.isEmpty()) {
            return StaffResult.failed(errors);
        }
        UUID accountId = jdbc.sql("""
                insert into user_account (organization_id, kind, email, status) values (:org, 'STAFF', :email, 'INVITED')
                returning id
                """).param("org", org).param("email", normalized).query(UUID.class).single();
        jdbc.sql("""
                insert into staff_profile (user_account_id, display_name, job_title, default_location_id)
                values (:account, :name, :title, :location)
                """)
            .param("account", accountId).param("name", displayName.trim()).param("title", blankToNull(jobTitle))
            .param("location", locationId, java.sql.Types.OTHER).update();
        List<UserError> bindingErrors = roles.replaceBindings(org, accountId, roleIds, locationId, actor.accountId());
        if (!bindingErrors.isEmpty()) {
            throw new RollbackWithErrors(bindingErrors);
        }
        SecureTokens.Issued token = secure.newToken();
        tokens.issue(accountId, "STAFF_INVITE", token.hash(), null, clock.instant().plus(properties.staffInviteTtl()));
        events.publishEvent(new VerificationEmailRequested(Purpose.STAFF_INVITE, normalized, displayName.trim(),
            secure.newCode(), publicWebUrl + "/accept-invite?token=" + token.token(), properties.staffInviteTtl()));
        audit(actor, "team.member.invite", accountId, Map.of("roles", roleIds.size()));
        return StaffResult.ok(staffMemberView(org, accountId).orElseThrow());
    }

    /** Public: the invited person sets a password with the link token and is signed in. */
    @Transactional
    public AuthResult acceptInvite(String token, String password, String displayName, Client client) {
        Optional<UserError> weak = Credentials.checkPassword(password, List.of("input", "password"));
        if (weak.isPresent()) {
            return new AuthResult(null, List.of(weak.get()));
        }
        Optional<TokenStore.OpenToken> open = token == null ? Optional.empty()
            : tokens.findOpenByTokenHash("STAFF_INVITE", SecureTokens.sha256(token));
        if (open.isEmpty() || !tokens.consume(open.get().id())) {
            return new AuthResult(null, List.of(UserError.of("TOKEN_INVALID_OR_EXPIRED",
                "This invite is not valid any more. Ask the shop owner for a new one.")));
        }
        UUID accountId = open.get().accountId();
        jdbc.sql("""
                update user_account set password_hash = :hash, password_changed_at = now(), status = 'ACTIVE',
                       email_verified_at = coalesce(email_verified_at, now()), updated_at = now(), version = version + 1
                 where id = :id and status = 'INVITED'
                """).param("hash", hasher.hash(password)).param("id", accountId).update();
        if (displayName != null && !displayName.isBlank()) {
            jdbc.sql("update staff_profile set display_name = :name, updated_at = now() where user_account_id = :id")
                .param("name", displayName.trim()).param("id", accountId).update();
        }
        AccountStore.AccountRecord account = accounts.findById(accountId).orElseThrow();
        audit.record(new AuditLog.Entry(account.organizationId(), accountId, "STAFF", "team.member.accept_invite",
            "user_account", accountId.toString(), "SUCCESS", Map.of()));
        return auth.signIn(account.email(), password, client);
    }

    // ------------------------------------------------------------- updates

    @Transactional
    public StaffResult update(UUID accountId, String displayName, String jobTitle, String status, UUID defaultLocationId,
            long expectedVersion) {
        CurrentPrincipal actor = authorization.require("team.member.manage");
        UUID org = actor.organizationId();
        StaffMemberView current = staffMemberView(org, accountId).orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND));
        if (current.version() != expectedVersion) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        List<UserError> errors = new ArrayList<>(checkLocation(defaultLocationId, org));
        if (status != null && !EDITABLE_STATUSES.contains(status)) {
            errors.add(UserError.of("VALIDATION_FAILED", "Set the account to Active or Inactive.", "input", "status"));
        }
        if (displayName != null && (displayName.isBlank() || displayName.length() > 120)) {
            errors.add(UserError.of("VALIDATION_FAILED", "Enter the person's name.", "input", "displayName"));
        }
        if (errors.isEmpty() && "INACTIVE".equals(status) && leavesNoActiveOwner(org, accountId)) {
            errors.add(UserError.of("LAST_OWNER_REQUIRED", "The shop needs at least one active owner.", "input", "status"));
        }
        if (!errors.isEmpty()) {
            return StaffResult.failed(errors);
        }
        jdbc.sql("""
                update user_account set status = case when cast(:status as text) is not null and status <> 'INVITED'
                                                      then cast(:status as text) else status end,
                       updated_at = now(), version = version + 1
                 where id = :id
                """).param("status", status).param("id", accountId).update();
        jdbc.sql("""
                update staff_profile set display_name = coalesce(cast(:name as text), display_name),
                       job_title = coalesce(cast(:title as text), job_title),
                       default_location_id = coalesce(cast(:location as uuid), default_location_id), updated_at = now()
                 where user_account_id = :id
                """)
            .param("name", blankToNull(displayName)).param("title", blankToNull(jobTitle))
            .param("location", defaultLocationId, java.sql.Types.OTHER).param("id", accountId).update();
        if ("INACTIVE".equals(status)) {
            sessions.revokeAllExcept(accountId, null, "DEACTIVATED");
        }
        audit(actor, "team.member.update", accountId, status == null ? Map.of() : Map.of("status", status));
        return StaffResult.ok(staffMemberView(org, accountId).orElseThrow());
    }

    @Transactional
    public StaffResult setRoles(UUID accountId, List<UUID> roleIds, UUID locationId, long expectedVersion) {
        CurrentPrincipal actor = authorization.require("team.member.manage");
        UUID org = actor.organizationId();
        StaffMemberView current = staffMemberView(org, accountId).orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND));
        if (current.version() != expectedVersion) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        List<UserError> errors = checkLocation(locationId, org);
        if (!errors.isEmpty()) {
            return StaffResult.failed(errors);
        }
        errors = roles.replaceBindings(org, accountId, roleIds, locationId, actor.accountId());
        if (!errors.isEmpty()) {
            return StaffResult.failed(errors);
        }
        if (activeOwners(org).isEmpty()) {
            throw new RollbackWithErrors(List.of(UserError.of("LAST_OWNER_REQUIRED",
                "The shop needs at least one active owner.", "input", "roleIds")));
        }
        bumpVersion(accountId);
        audit(actor, "team.member.set_roles", accountId, Map.of("roles", roleIds.size()));
        return StaffResult.ok(staffMemberView(org, accountId).orElseThrow());
    }

    @Transactional
    public StaffResult setOverrides(UUID accountId, List<GrantInput> overrides, long expectedVersion) {
        CurrentPrincipal actor = authorization.require("team.member.manage");
        UUID org = actor.organizationId();
        StaffMemberView current = staffMemberView(org, accountId).orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND));
        if (current.version() != expectedVersion) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        List<UserError> errors = roles.replaceOverrides(org, accountId, overrides, actor.accountId());
        if (!errors.isEmpty()) {
            return StaffResult.failed(errors);
        }
        bumpVersion(accountId);
        audit(actor, "team.member.set_overrides", accountId, Map.of("overrides", overrides.size()));
        return StaffResult.ok(staffMemberView(org, accountId).orElseThrow());
    }

    // ------------------------------------------------------------- helpers

    Optional<StaffMemberView> staffMemberView(UUID organizationId, UUID accountId) {
        return jdbc.sql("""
                select a.id, a.email, a.status, a.last_sign_in_at, a.version, s.display_name, s.job_title,
                       s.default_location_id
                  from user_account a join staff_profile s on s.user_account_id = a.id
                 where a.id = :id and a.organization_id = :org and a.kind = 'STAFF' and a.erased_at is null
                """)
            .param("id", accountId).param("org", organizationId)
            .query((rs, row) -> {
                UUID location = rs.getObject("default_location_id", UUID.class);
                OffsetDateTime lastSignIn = rs.getObject("last_sign_in_at", OffsetDateTime.class);
                return new StaffMemberView(accountId, rs.getString("display_name"), rs.getString("email"),
                    rs.getString("job_title"), rs.getString("status"), roles.bindings(accountId),
                    roles.overrides(accountId),
                    List.copyOf(authorization.effectivePermissions(accountId, organizationId, location)),
                    location == null ? null : organizations.location(location).orElse(null),
                    lastSignIn == null ? null : lastSignIn.toInstant(), rs.getLong("version"));
            })
            .optional();
    }

    private List<UUID> activeOwners(UUID org) {
        List<UUID> owners = roles.ownerAccounts(org);
        if (owners.isEmpty()) {
            return owners;
        }
        return jdbc.sql("select id from user_account where id in (:ids) and status = 'ACTIVE' and erased_at is null")
            .param("ids", owners).query(UUID.class).list();
    }

    private boolean leavesNoActiveOwner(UUID org, UUID deactivating) {
        return activeOwners(org).stream().allMatch(deactivating::equals);
    }

    private List<UserError> checkLocation(UUID locationId, UUID org) {
        if (locationId == null) {
            return List.of();
        }
        boolean ok = organizations.locations(org).stream().anyMatch(l -> l.id().equals(locationId));
        return ok ? List.of() : List.of(UserError.of("VALIDATION_FAILED", "Choose a location of this shop.", "input", "locationId"));
    }

    private void bumpVersion(UUID accountId) {
        jdbc.sql("update user_account set version = version + 1, updated_at = now() where id = :id")
            .param("id", accountId).update();
    }

    private void audit(CurrentPrincipal actor, String action, UUID target, Map<String, Object> details) {
        audit.record(new AuditLog.Entry(actor.organizationId(), actor.accountId(), "STAFF", action, "user_account",
            target.toString(), "SUCCESS", details));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String encodeCursor(StaffMemberView member) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((member.displayName() + "\u0000" + member.id()).getBytes(StandardCharsets.UTF_8));
    }

    private static String[] decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\u0000", 2);
            UUID.fromString(parts[1]);
            return parts;
        } catch (RuntimeException ex) {
            throw new ApplicationException(ErrorCode.BAD_REQUEST, "The page cursor is not valid.");
        }
    }

    /** Thrown inside a transaction to roll back and report business errors (converted by callers). */
    public static class RollbackWithErrors extends RuntimeException {

        private final transient List<UserError> errors;

        RollbackWithErrors(List<UserError> errors) {
            super("rolled back", null, false, false);
            this.errors = List.copyOf(errors);
        }

        public List<UserError> errors() {
            return errors;
        }
    }

    public record StaffMemberView(UUID id, String displayName, String email, String jobTitle, String status,
            List<RoleAdministration.BindingView> roles, List<RoleAdministration.GrantView> permissionOverrides,
            List<String> effectivePermissions, com.aurelle.organization.LocationSummary defaultLocation,
            Instant lastSignInAt, long version) {

        public String cursor() {
            return encodeCursor(this);
        }
    }

    public record StaffPage(List<StaffMemberView> members, boolean hasNextPage, boolean hasPreviousPage, long totalCount) {
    }

    public record StaffResult(StaffMemberView staffMember, List<UserError> errors) {

        static StaffResult ok(StaffMemberView member) {
            return new StaffResult(member, List.of());
        }

        static StaffResult failed(List<UserError> errors) {
            return new StaffResult(null, List.copyOf(errors));
        }
    }
}
