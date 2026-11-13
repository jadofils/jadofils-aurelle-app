package com.aurelle.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurelle.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Phase 1 contract over real HTTP: Android (bearer) and browser (cookies) clients, team and roles. */
class IdentityGraphQlIT extends IntegrationTest {

    private static final String ORIGIN = "http://localhost:8081";
    private static final String PASSWORD = "Correct-Horse-Battery-9";
    private static final String VIEWER = "{ viewer { id kind email emailVerified phone phoneVerified displayName "
        + "themePreference preferredLocale permissions customerProfile { id displayName shopFor marketingConsent version } "
        + "staffProfile { id displayName jobTitle defaultLocation { id name kind active } } } }";

    // ------------------------------------------------------------- clients and sessions

    @Test
    void androidSignUpReturnsTokensAndBearerWorks() {
        Map<String, Object> payload = mutation(null, null, "signUp",
            "signUp(input: {displayName: \"Ama Mensah\", email: \"" + uniqueEmail() + "\", password: \"" + PASSWORD
                + "\", acceptTerms: true, shopFor: [WOMEN], deviceLabel: \"Pixel 8\"}) "
                + "{ accessToken accessTokenExpiresAt refreshToken refreshTokenExpiresAt errors { code } viewer { kind displayName permissions } }");

        assertThat((List<?>) payload.get("errors")).isEmpty();
        String access = (String) payload.get("accessToken");
        assertThat(access).isNotBlank();
        assertThat(payload.get("accessTokenExpiresAt").toString()).endsWith("Z");
        Map<String, Object> viewer = data(post(access, null, null, VIEWER), "viewer");
        assertThat(viewer).containsEntry("kind", "CUSTOMER").containsEntry("displayName", "Ama Mensah");
        assertThat(((Map<?, ?>) viewer.get("customerProfile")).get("shopFor")).isEqualTo(List.of("WOMEN"));
        assertThat(dataList(post(access, null, null, "{ mySessions { deviceLabel platform current } }"), "mySessions"))
            .isNotNull();
    }

    @Test
    void browserSignInSetsHttpOnlyCookiesAndNeverReturnsTokens() {
        ResponseEntity<Map<String, Object>> response = post(null, ORIGIN, null,
            "mutation { signIn(input: {identifier: \"amina@example.test\", password: \"" + SEED_PASSWORD + "\"}) "
                + "{ accessToken refreshToken errors { code } viewer { email permissions } } }");

        Map<String, Object> payload = data(response, "signIn");
        assertThat(payload.get("accessToken")).isNull();
        assertThat(payload.get("refreshToken")).isNull();
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2).allSatisfy(c -> assertThat(c).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/"));
        String access = cookieValue(cookies, SessionAuthenticationFilter.ACCESS_COOKIE);
        String refresh = cookieValue(cookies, SessionAuthenticationFilter.REFRESH_COOKIE);

        assertThat(data(post(null, ORIGIN, cookieHeader(access, refresh), "{ viewer { email } }"), "viewer"))
            .containsEntry("email", "amina@example.test");
        assertThat(post(null, "https://evil.example.test", cookieHeader(access, refresh), "{ viewer { email } }").getBody())
            .as("cookies from a foreign origin are ignored").extractingByKey("data").extracting("viewer").isNull();

        ResponseEntity<Map<String, Object>> refreshed = post(null, ORIGIN, cookieHeader(access, refresh),
            "mutation { refreshSession(input: {}) { accessToken errors { code } } }");
        assertThat(((List<?>) data(refreshed, "refreshSession").get("errors"))).isEmpty();
        assertThat(refreshed.getHeaders().get(HttpHeaders.SET_COOKIE)).hasSize(2);

        ResponseEntity<Map<String, Object>> signedOut = post(null, ORIGIN,
            cookieHeader(cookieValue(refreshed.getHeaders().get(HttpHeaders.SET_COOKIE), SessionAuthenticationFilter.ACCESS_COOKIE), null),
            "mutation { signOut { ok } }");
        assertThat(signedOut.getHeaders().get(HttpHeaders.SET_COOKIE)).allSatisfy(c -> assertThat(c).contains("Max-Age=0"));
    }

    @Test
    void failedBrowserSignInClearsCookiesAndReportsTheError() {
        ResponseEntity<Map<String, Object>> response = post(null, ORIGIN, null,
            "mutation { signIn(input: {identifier: \"amina@example.test\", password: \"Wrong-Password-1\"}) { viewer { id } errors { code message } } }");

        assertThat((List<Map<String, Object>>) data(response, "signIn").get("errors"))
            .extracting(e -> e.get("code")).containsExactly("INVALID_CREDENTIALS");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).allSatisfy(c -> assertThat(c).contains("Max-Age=0"));
    }

    @Test
    void guestsSeeNullViewerAndUnauthenticatedForPrivateOperations() {
        assertThat(post(null, null, null, "{ viewer { id } }").getBody()).extractingByKey("data").extracting("viewer").isNull();
        assertThat(errorCode(post(null, null, null, "{ mySessions { id } }"))).isEqualTo("UNAUTHENTICATED");
        assertThat(errorCode(post(null, null, null, "{ roles { id } }"))).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void enumerationSafeMutationsAlwaysAnswerOk() {
        assertThat(mutation(null, null, "requestPasswordReset",
            "requestPasswordReset(input: {identifier: \"" + uniqueEmail() + "\"}) { ok errors { code } }"))
            .containsEntry("ok", true);
        assertThat(mutation(null, null, "verifyEmail", "verifyEmail(input: {token: \"nope\"}) { ok errors { code } }"))
            .containsEntry("ok", false);
        assertThat(mutation(null, null, "resetPassword",
            "resetPassword(input: {token: \"nope\", newPassword: \"Brand-New-Password-7\"}) { ok errors { code } }"))
            .containsEntry("ok", false);
    }

    @Test
    void profileUpdatesUseOptimisticVersions() {
        String access = signUpAndroid();
        Map<String, Object> viewer = data(post(access, null, null, VIEWER), "viewer");
        long version = ((Number) ((Map<?, ?>) viewer.get("customerProfile")).get("version")).longValue();

        Map<String, Object> updated = mutation(access, null, "updateMyProfile",
            "updateMyProfile(input: {displayName: \"Ama K. Mensah\", givenName: \"Ama\", birthDate: \"1995-04-01\", "
                + "shopFor: [WOMEN, KIDS_UNISEX], themePreference: DARK, preferredLocale: \"en-GH\", marketingConsent: false, "
                + "phone: \"+447700900555\", expectedVersion: " + version + "}) { errors { code } viewer { displayName themePreference phone } }");
        assertThat((List<?>) updated.get("errors")).isEmpty();
        assertThat((Map<String, Object>) updated.get("viewer")).containsEntry("displayName", "Ama K. Mensah")
            .containsEntry("themePreference", "DARK");

        assertThat(errorCode(post(access, null, null, "mutation { updateMyProfile(input: {displayName: \"Stale\", expectedVersion: "
            + version + "}) { errors { code } } }"))).isEqualTo("CONFLICT");
        Map<String, Object> invalid = mutation(access, null, "updateMyProfile",
            "updateMyProfile(input: {displayName: \" \", phone: \"07\", birthDate: \"2999-01-01\", shopFor: [MEN]}) { errors { code field } }");
        assertThat((List<?>) invalid.get("errors")).hasSize(3);
        Map<String, Object> taken = mutation(access, null, "updateMyProfile",
            "updateMyProfile(input: {phone: \"+447700900021\"}) { errors { code } }");
        assertThat((List<Map<String, Object>>) taken.get("errors")).extracting(e -> e.get("code")).containsExactly("PHONE_TAKEN");
    }

    @Test
    void passwordChangeAndDeviceRevocationOverGraphql() {
        String email = uniqueEmail();
        String first = signUpAndroid(email);
        String second = (String) mutation(null, null, "signIn", "signIn(input: {identifier: \"" + email + "\", password: \""
            + PASSWORD + "\"}) { accessToken }").get("accessToken");
        List<Map<String, Object>> sessions = dataList(post(first, null, null, "{ mySessions { id current } }"), "mySessions");
        Object other = sessions.stream().filter(s -> !Boolean.TRUE.equals(s.get("current"))).findFirst().orElseThrow().get("id");

        assertThat(mutation(first, null, "revokeSession", "revokeSession(input: {sessionId: \"" + other + "\"}) { ok }"))
            .containsEntry("ok", true);
        assertThat(post(second, null, null, "{ viewer { id } }").getBody()).extractingByKey("data").extracting("viewer").isNull();
        assertThat(mutation(first, null, "changePassword",
            "changePassword(input: {currentPassword: \"" + PASSWORD + "\", newPassword: \"Another-Password-8\"}) { ok errors { code } }"))
            .containsEntry("ok", true);
        assertThat(mutation(first, null, "resendEmailVerification", "resendEmailVerification { ok }")).containsEntry("ok", true);
        assertThat(mutation(first, null, "signOut", "signOut { ok }")).containsEntry("ok", true);
        assertThat(mutation(null, null, "refreshSession", "refreshSession(input: {refreshToken: \"nope\"}) { errors { code } }"))
            .extracting("errors").asList().hasSize(1);
    }

    // ------------------------------------------------------------- authorization and team

    @Test
    void customersAreForbiddenFromTeamOperations() {
        String customer = signUpAndroid();

        assertThat(errorCode(post(customer, null, null, "{ staffMembers { totalCount } }"))).isEqualTo("FORBIDDEN");
        assertThat(errorCode(post(customer, null, null, "{ permissions { key } }"))).isEqualTo("FORBIDDEN");
        assertThat(errorCode(post(customer, null, null,
            "mutation { upsertRole(input: {key: \"x\", name: \"X\", grants: []}) { errors { code } } }"))).isEqualTo("FORBIDDEN");
    }

    @Test
    void ownerSeesTheTeamPagedWithRolesAndLocations() {
        String owner = signIn("owner@example.test");

        Map<String, Object> page = data(post(owner, null, null, "{ staffMembers(first: 2) { totalCount "
            + "pageInfo { hasNextPage endCursor } edges { cursor node { id displayName email status version "
            + "roles { scopeType role { key inheritsFrom { key } effectivePermissions grants { effect permission { key group } } } } "
            + "permissionOverrides { effect permission { key } } effectivePermissions defaultLocation { name } lastSignInAt } } } }"),
            "staffMembers");
        assertThat(((Number) page.get("totalCount")).intValue()).isGreaterThanOrEqualTo(4);
        Map<String, Object> pageInfo = (Map<String, Object>) page.get("pageInfo");
        assertThat(pageInfo).containsEntry("hasNextPage", true);
        Map<String, Object> next = data(post(owner, null, null, "{ staffMembers(first: 100, after: \"" + pageInfo.get("endCursor")
            + "\", filter: {search: \"linda\", status: [ACTIVE]}) { edges { node { displayName roles { role { key } } } } } }"),
            "staffMembers");
        assertThat((List<?>) next.get("edges")).hasSize(1);
        assertThat(errorCode(post(owner, null, null, "{ staffMembers(after: \"%%%\") { totalCount } }"))).isEqualTo("BAD_REQUEST");

        assertThat(dataList(post(owner, null, null, "{ locations { id name kind active } }"), "locations")).hasSizeGreaterThanOrEqualTo(4);
        assertThat(dataList(post(owner, null, null, "{ permissions { key description group } }"), "permissions")).hasSize(31);
        String lindaId = id("packer@example.test");
        Map<String, Object> linda = data(post(owner, null, null, "{ staffMember(id: \"" + lindaId + "\") { displayName } }"), "staffMember");
        assertThat(linda).containsEntry("displayName", "Linda Cole");
        assertThat(data(post(owner, null, null, "{ staffMember(id: \"" + UUID.randomUUID() + "\") { displayName } }"), "staffMember"))
            .isNull();
    }

    @Test
    void inviteAcceptAndManageAStaffMember() {
        String owner = signIn("owner@example.test");
        String packerRole = roleId(owner, "packer");
        String email = uniqueEmail();

        Map<String, Object> invited = mutation(owner, null, "inviteStaffMember", "inviteStaffMember(input: {displayName: \"Esi Owusu\", "
            + "email: \"" + email + "\", jobTitle: \"Packer\", roleIds: [\"" + packerRole + "\"], locationId: \"01970000-0000-7000-8001-000000001002\"}) "
            + "{ errors { code } staffMember { id status version effectivePermissions } }");
        assertThat((List<?>) invited.get("errors")).isEmpty();
        Map<String, Object> member = (Map<String, Object>) invited.get("staffMember");
        assertThat(member).containsEntry("status", "INVITED");
        String memberId = (String) member.get("id");

        assertThat((List<Map<String, Object>>) mutation(owner, null, "inviteStaffMember", "inviteStaffMember(input: {displayName: \"Dup\", "
            + "email: \"" + email + "\", roleIds: [\"" + packerRole + "\"]}) { errors { code } }").get("errors"))
            .extracting(e -> e.get("code")).containsExactly("EMAIL_TAKEN");
        assertThat((List<?>) mutation(owner, null, "inviteStaffMember", "inviteStaffMember(input: {displayName: \" \", "
            + "email: \"bad\", roleIds: [], locationId: \"" + UUID.randomUUID() + "\"}) { errors { code } }").get("errors")).hasSize(4);

        String token = inviteToken(memberId);
        Map<String, Object> accepted = mutation(null, null, "acceptStaffInvite", "acceptStaffInvite(input: {token: \"" + token
            + "\", password: \"" + PASSWORD + "\", displayName: \"Esi O.\"}) { accessToken errors { code } viewer { kind staffProfile { displayName } } }");
        assertThat((List<?>) accepted.get("errors")).isEmpty();
        assertThat(accepted.get("accessToken")).isNotNull();
        assertThat((List<Map<String, Object>>) mutation(null, null, "acceptStaffInvite", "acceptStaffInvite(input: {token: \"" + token
            + "\", password: \"" + PASSWORD + "\"}) { errors { code } }").get("errors")).extracting(e -> e.get("code"))
            .containsExactly("TOKEN_INVALID_OR_EXPIRED");
        assertThat((List<Map<String, Object>>) mutation(null, null, "acceptStaffInvite", "acceptStaffInvite(input: {token: \"x\", "
            + "password: \"short\"}) { errors { code } }").get("errors")).extracting(e -> e.get("code")).containsExactly("PASSWORD_TOO_WEAK");

        long version = version(owner, memberId);
        Map<String, Object> overrides = mutation(owner, null, "setStaffPermissionOverrides", "setStaffPermissionOverrides(input: {staffMemberId: \""
            + memberId + "\", overrides: [{key: \"reports.dashboard.read\", effect: ALLOW}, {key: \"reports.dashboard.read\", effect: DENY}], "
            + "expectedVersion: " + version + "}) { errors { code } staffMember { permissionOverrides { effect } } }");
        assertThat((List<?>) overrides.get("errors")).isEmpty();
        assertThat((List<Map<String, Object>>) mutation(owner, null, "setStaffPermissionOverrides", "setStaffPermissionOverrides(input: {staffMemberId: \""
            + memberId + "\", overrides: [{key: \"no.such.key\", effect: ALLOW}], expectedVersion: " + version(owner, memberId)
            + "}) { errors { code } }").get("errors")).extracting(e -> e.get("code")).containsExactly("UNKNOWN_PERMISSION");

        Map<String, Object> roles = mutation(owner, null, "setStaffRoles", "setStaffRoles(input: {staffMemberId: \"" + memberId
            + "\", roleIds: [\"" + roleId(owner, "manager") + "\"], expectedVersion: " + version(owner, memberId) + "}) { errors { code } }");
        assertThat((List<?>) roles.get("errors")).isEmpty();
        assertThat((List<Map<String, Object>>) mutation(owner, null, "setStaffRoles", "setStaffRoles(input: {staffMemberId: \"" + memberId
            + "\", roleIds: [\"" + roleId(owner, "manager") + "\"], locationId: \"" + UUID.randomUUID() + "\", expectedVersion: "
            + version(owner, memberId) + "}) { errors { code } }").get("errors")).extracting(e -> e.get("code")).containsExactly("VALIDATION_FAILED");

        Map<String, Object> deactivated = mutation(owner, null, "updateStaffMember", "updateStaffMember(input: {staffMemberId: \"" + memberId
            + "\", status: INACTIVE, jobTitle: \"Seasonal\", displayName: \"Esi Owusu\", expectedVersion: " + version(owner, memberId)
            + "}) { errors { code } staffMember { status jobTitle } }");
        assertThat((Map<String, Object>) deactivated.get("staffMember")).containsEntry("status", "INACTIVE");
        assertThat(post((String) accepted.get("accessToken"), null, null, "{ viewer { id } }").getBody())
            .as("deactivation signs the member out").extractingByKey("data").extracting("viewer").isNull();
        assertThat(errorCode(post(owner, null, null, "mutation { updateStaffMember(input: {staffMemberId: \"" + memberId
            + "\", status: ACTIVE, expectedVersion: 0}) { errors { code } } }"))).isEqualTo("CONFLICT");
        assertThat((List<?>) mutation(owner, null, "updateStaffMember", "updateStaffMember(input: {staffMemberId: \"" + memberId
            + "\", status: LOCKED, displayName: \" \", expectedVersion: " + version(owner, memberId) + "}) { errors { code } }").get("errors"))
            .hasSize(2);
        assertThat(errorCode(post(owner, null, null, "mutation { updateStaffMember(input: {staffMemberId: \"" + UUID.randomUUID()
            + "\", expectedVersion: 0}) { errors { code } } }"))).isEqualTo("NOT_FOUND");
    }

    @Test
    void theLastOwnerCannotBeDeactivatedOrDemoted() {
        String owner = signIn("owner@example.test");
        String zara = id("owner@example.test");

        assertThat((List<Map<String, Object>>) mutation(owner, null, "updateStaffMember", "updateStaffMember(input: {staffMemberId: \""
            + zara + "\", status: INACTIVE, expectedVersion: " + version(owner, zara) + "}) { errors { code } }").get("errors"))
            .extracting(e -> e.get("code")).containsExactly("LAST_OWNER_REQUIRED");
        assertThat((List<Map<String, Object>>) mutation(owner, null, "setStaffRoles", "setStaffRoles(input: {staffMemberId: \""
            + zara + "\", roleIds: [\"" + roleId(owner, "manager") + "\"], expectedVersion: " + version(owner, zara) + "}) { errors { code } }")
            .get("errors")).extracting(e -> e.get("code")).containsExactly("LAST_OWNER_REQUIRED");
        assertThat(data(post(owner, null, null, "{ viewer { permissions } }"), "viewer").get("permissions").toString())
            .as("rolled back").contains("team.role.manage");
    }

    @Test
    void rolesRejectCyclesProtectSystemRolesAndArchiveOnlyUnusedRoles() {
        String owner = signIn("owner@example.test");
        String packer = roleId(owner, "packer");

        Map<String, Object> created = mutation(owner, null, "upsertRole", "upsertRole(input: {key: \"" + uniqueKey() + "\", name: \"Night shift\", "
            + "grants: [{key: \"orders.order.read\", effect: ALLOW}], inheritsFromRoleIds: [\"" + packer + "\"]}) "
            + "{ errors { code } role { id key kind version effectivePermissions inheritsFrom { key } } }");
        assertThat((List<?>) created.get("errors")).isEmpty();
        Map<String, Object> role = (Map<String, Object>) created.get("role");
        assertThat(role).containsEntry("kind", "CUSTOM");
        String roleId = (String) role.get("id");
        long version = ((Number) role.get("version")).longValue();

        assertThat((List<Map<String, Object>>) mutation(owner, null, "upsertRole", "upsertRole(input: {roleId: \"" + packer + "\", key: \"packer\", "
            + "name: \"Packer\", grants: [], inheritsFromRoleIds: [\"" + roleId + "\"], expectedVersion: " + roleVersion(owner, "packer")
            + "}) { errors { code } }").get("errors")).extracting(e -> e.get("code")).containsExactly("ROLE_CYCLE");
        assertThat((List<Map<String, Object>>) mutation(owner, null, "upsertRole", "upsertRole(input: {roleId: \"" + roleId + "\", key: \""
            + role.get("key") + "\", name: \"Self\", grants: [], inheritsFromRoleIds: [\"" + roleId + "\"], expectedVersion: " + version
            + "}) { errors { code } }").get("errors")).extracting(e -> e.get("code")).containsExactly("ROLE_CYCLE");
        assertThat((List<Map<String, Object>>) mutation(owner, null, "upsertRole", "upsertRole(input: {roleId: \"" + packer
            + "\", key: \"renamed\", name: \"Packer\", grants: [], expectedVersion: " + roleVersion(owner, "packer") + "}) { errors { code } }")
            .get("errors")).extracting(e -> e.get("code")).containsExactly("SYSTEM_ROLE_PROTECTED");
        assertThat((List<Map<String, Object>>) mutation(owner, null, "upsertRole", "upsertRole(input: {key: \"packer\", name: \"Dup\", grants: []}) "
            + "{ errors { code } }").get("errors")).extracting(e -> e.get("code")).containsExactly("VALIDATION_FAILED");
        assertThat((List<?>) mutation(owner, null, "upsertRole", "upsertRole(input: {key: \"Bad Key\", name: \" \", "
            + "grants: [{key: \"no.such.key\", effect: ALLOW}], inheritsFromRoleIds: [\"" + UUID.randomUUID() + "\"]}) { errors { code } }")
            .get("errors")).hasSize(3);
        assertThat((List<Map<String, Object>>) mutation(owner, null, "upsertRole", "upsertRole(input: {key: \"" + uniqueKey()
            + "\", name: \"Ghost parent\", grants: [], inheritsFromRoleIds: [\"" + UUID.randomUUID() + "\"]}) { errors { code } }")
            .get("errors")).extracting(e -> e.get("code")).containsExactly("VALIDATION_FAILED");
        assertThat(errorCode(post(owner, null, null, "mutation { upsertRole(input: {roleId: \"" + roleId + "\", key: \"" + role.get("key")
            + "\", name: \"Stale\", grants: [], expectedVersion: 999}) { errors { code } } }"))).isEqualTo("CONFLICT");

        Map<String, Object> updated = mutation(owner, null, "upsertRole", "upsertRole(input: {roleId: \"" + roleId + "\", key: \""
            + role.get("key") + "\", name: \"Night shift lead\", description: \"Evenings\", grants: [{key: \"orders.order.refund\", effect: DENY}], "
            + "inheritsFromRoleIds: [\"" + packer + "\"], expectedVersion: " + version + "}) { errors { code } role { name version } }");
        assertThat((List<?>) updated.get("errors")).isEmpty();
        long newVersion = ((Number) ((Map<?, ?>) updated.get("role")).get("version")).longValue();

        assertThat((List<Map<String, Object>>) mutation(owner, null, "archiveRole", "archiveRole(input: {roleId: \"" + packer
            + "\", expectedVersion: " + roleVersion(owner, "packer") + "}) { errors { code } }").get("errors"))
            .extracting(e -> e.get("code")).containsExactly("SYSTEM_ROLE_PROTECTED");
        assertThat((List<Map<String, Object>>) mutation(owner, null, "archiveRole", "archiveRole(input: {roleId: \"" + roleId(owner, "weekend-lead")
            + "\", expectedVersion: " + roleVersion(owner, "weekend-lead") + "}) { errors { code } }").get("errors"))
            .extracting(e -> e.get("code")).containsExactly("ROLE_IN_USE");
        assertThat(errorCode(post(owner, null, null, "mutation { archiveRole(input: {roleId: \"" + roleId
            + "\", expectedVersion: 999}) { errors { code } } }"))).isEqualTo("CONFLICT");
        assertThat(errorCode(post(owner, null, null, "mutation { archiveRole(input: {roleId: \"" + UUID.randomUUID()
            + "\", expectedVersion: 0}) { errors { code } } }"))).isEqualTo("NOT_FOUND");
        assertThat((List<?>) mutation(owner, null, "archiveRole", "archiveRole(input: {roleId: \"" + roleId
            + "\", expectedVersion: " + newVersion + "}) { errors { code } role { key } }").get("errors")).isEmpty();
    }

    // ------------------------------------------------------------- helpers

    private String signUpAndroid() {
        return signUpAndroid(uniqueEmail());
    }

    private String signUpAndroid(String email) {
        return (String) mutation(null, null, "signUp", "signUp(input: {displayName: \"Test Shopper\", email: \"" + email
            + "\", password: \"" + PASSWORD + "\", acceptTerms: true}) { accessToken }").get("accessToken");
    }

    private String signIn(String email) {
        return (String) mutation(null, null, "signIn", "signIn(input: {identifier: \"" + email + "\", password: \""
            + SEED_PASSWORD + "\"}) { accessToken }").get("accessToken");
    }

    private String roleId(String owner, String key) {
        List<Map<String, Object>> roles = dataList(post(owner, null, null, "{ roles { id key version } }"), "roles");
        return (String) roles.stream().filter(r -> key.equals(r.get("key"))).findFirst().orElseThrow().get("id");
    }

    private long roleVersion(String owner, String key) {
        List<Map<String, Object>> roles = dataList(post(owner, null, null, "{ roles { key version } }"), "roles");
        return ((Number) roles.stream().filter(r -> key.equals(r.get("key"))).findFirst().orElseThrow().get("version")).longValue();
    }

    private long version(String owner, String memberId) {
        return ((Number) data(post(owner, null, null, "{ staffMember(id: \"" + memberId + "\") { version } }"), "staffMember")
            .get("version")).longValue();
    }

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbc;

    private String id(String email) {
        return jdbc.sql("select id::text from user_account where email = ?").param(email).query(String.class).single();
    }

    /** Re-issues a known invite token for the member (the real one is only in the e-mail). */
    private String inviteToken(String memberId) {
        String token = "test-invite-" + UUID.randomUUID();
        jdbc.sql("update auth_token set token_hash = sha256(convert_to(?, 'UTF8')) where user_account_id = ?::uuid "
            + "and purpose = 'STAFF_INVITE' and used_at is null").param(token).param(memberId).update();
        return token;
    }

    private Map<String, Object> mutation(String bearer, String origin, String field, String selection) {
        return data(post(bearer, origin, null, "mutation { " + selection + " }"), field);
    }

    private ResponseEntity<Map<String, Object>> post(String bearer, String origin, String cookie, String query) {
        return RestClient.create("http://localhost:" + port).post().uri("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> {
                if (bearer != null) {
                    h.setBearerAuth(bearer);
                }
                if (origin != null) {
                    h.setOrigin(origin);
                }
                if (cookie != null) {
                    h.add(HttpHeaders.COOKIE, cookie);
                }
            })
            .body(Map.of("query", query))
            .retrieve()
            .onStatus(status -> true, (request, response) -> { })
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response, String field) {
        return (Map<String, Object>) field(response, field);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> dataList(ResponseEntity<Map<String, Object>> response, String field) {
        return (List<Map<String, Object>>) field(response, field);
    }

    @SuppressWarnings("unchecked")
    private static Object field(ResponseEntity<Map<String, Object>> response, String field) {
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertThat(data).as("data in %s", response.getBody()).isNotNull();
        return data.get(field);
    }

    @SuppressWarnings("unchecked")
    private static String errorCode(ResponseEntity<Map<String, Object>> response) {
        List<Map<String, Object>> errors = (List<Map<String, Object>>) response.getBody().get("errors");
        assertThat(errors).as("errors in %s", response.getBody()).isNotEmpty();
        return (String) ((Map<String, Object>) errors.getFirst().get("extensions")).get("code");
    }

    private static String cookieValue(List<String> setCookies, String name) {
        return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow()
            .substring(name.length() + 1).split(";")[0];
    }

    private static String cookieHeader(String access, String refresh) {
        StringBuilder header = new StringBuilder(SessionAuthenticationFilter.ACCESS_COOKIE + "=" + access);
        if (refresh != null) {
            header.append("; ").append(SessionAuthenticationFilter.REFRESH_COOKIE).append('=').append(refresh);
        }
        return header.toString();
    }

    private static String uniqueEmail() {
        return "gql-" + UUID.randomUUID() + "@example.test";
    }

    private static String uniqueKey() {
        return "role-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
