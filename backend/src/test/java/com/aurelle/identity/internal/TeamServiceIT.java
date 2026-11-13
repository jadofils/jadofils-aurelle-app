package com.aurelle.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aurelle.IntegrationTest;
import com.aurelle.authorization.RoleAdministration;
import com.aurelle.authorization.RoleAdministration.GrantInput;
import com.aurelle.common.ApplicationException;
import com.aurelle.common.CurrentPrincipal;
import com.aurelle.common.UserError;
import com.aurelle.identity.internal.ViewerService.ProfileChanges;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Service-level paths of team management and the viewer not reached through the GraphQL tests. */
class TeamServiceIT extends IntegrationTest {

    private static final UUID KIGALI = UUID.fromString("01970000-0000-7000-8001-000000001001");

    @Autowired
    TeamService team;
    @Autowired
    ViewerService viewers;
    @Autowired
    RoleAdministration roles;
    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void staffListClampsThePageSizeAndFiltersByRole() {
        CurrentPrincipal owner = actAs("owner@example.test");
        UUID packerRole = roleId(owner, "packer");

        TeamService.StaffPage all = team.staffMembers(500, null, " ", List.of(), null);
        TeamService.StaffPage packers = team.staffMembers(100, null, null, null, packerRole);
        TeamService.StaffPage onePacker = team.staffMembers(0, null, null, null, packerRole);

        assertThat(all.members()).hasSizeLessThanOrEqualTo(TeamService.MAX_PAGE);
        assertThat(packers.members()).extracting(TeamService.StaffMemberView::displayName).contains("Linda Cole")
            .doesNotContain("Zara Bello");
        assertThat(packers.members()).allSatisfy(m ->
            assertThat(m.roles()).anySatisfy(b -> assertThat(b.roleId()).isEqualTo(packerRole)));
        assertThat(onePacker.members()).as("page size is clamped to at least 1").hasSize(1);
        assertThat(onePacker.hasNextPage()).isEqualTo(packers.members().size() > 1);
        assertThat(packers.hasPreviousPage()).isFalse();
    }

    @Test
    void staffCannotBeGivenTheCustomerRoleOrUnknownRoles() {
        CurrentPrincipal owner = actAs("owner@example.test");
        UUID linda = accountId("packer@example.test");

        TeamService.StaffResult customer = team.setRoles(linda, List.of(roleId(owner, "customer")), null, version(linda));
        TeamService.StaffResult unknown = team.setRoles(linda, List.of(UUID.randomUUID()), null, version(linda));

        assertThat(customer.errors()).extracting(UserError::code).containsExactly("VALIDATION_FAILED");
        assertThat(unknown.errors()).extracting(UserError::code).containsExactly("VALIDATION_FAILED");
        assertThatThrownBy(() -> team.setRoles(UUID.randomUUID(), List.of(), null, 0))
            .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> team.setRoles(linda, List.of(), null, -1)).isInstanceOf(ApplicationException.class);
    }

    @Test
    void overridesCanBeClearedAndStaleVersionsConflict() {
        actAs("owner@example.test");
        UUID daniel = accountId("manager@example.test");

        assertThat(team.setOverrides(daniel, List.of(), version(daniel)).errors()).isEmpty();
        assertThat(roles.overrides(daniel)).isEmpty();
        assertThatThrownBy(() -> team.setOverrides(daniel, List.of(new GrantInput("orders.order.read", "ALLOW")), -1))
            .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> team.setOverrides(UUID.randomUUID(), List.of(), 0)).isInstanceOf(ApplicationException.class);
        assertThat(team.setOverrides(daniel, List.of(new GrantInput("orders.order.read", "MAYBE")), version(daniel)).errors())
            .extracting(UserError::code).containsExactly("VALIDATION_FAILED");
    }

    @Test
    void staffMemberCanMoveToAnotherLocation() {
        actAs("owner@example.test");
        UUID linda = accountId("packer@example.test");

        TeamService.StaffResult moved = team.update(linda, null, null, null, KIGALI, version(linda));

        assertThat(moved.errors()).isEmpty();
        assertThat(moved.staffMember().defaultLocation().name()).isEqualTo("Kigali studio");
    }

    @Test
    void staffUpdateTheirOwnDisplayNameAndSeeTheirLocation() {
        CurrentPrincipal daniel = actAs("manager@example.test");

        assertThat(viewers.updateMyProfile(daniel, new ProfileChanges("Daniel K. Osei", null, null, null, null, null,
            "LIGHT", null, null, null))).isEmpty();
        assertThat(viewers.updateMyProfile(daniel, new ProfileChanges(null, null, null, null, null, null, null, null,
            true, null))).isEmpty();

        ViewerService.ViewerView viewer = viewers.viewer(daniel.accountId()).orElseThrow();
        assertThat(viewer.displayName()).isEqualTo("Daniel K. Osei");
        assertThat(viewer.staffProfile().defaultLocation().name()).isEqualTo("Kigali studio");
        assertThat(viewer.customerProfile()).isNull();
        assertThat(viewers.updateMyProfile(daniel, new ProfileChanges(null, null, null, null, null, null, "NEON", null,
            null, null))).extracting(UserError::code).containsExactly("VALIDATION_FAILED");
        assertThat(viewers.viewer(UUID.randomUUID())).isEmpty();
    }

    @Test
    void customerProfileWithoutChangesStillSucceeds() {
        CurrentPrincipal amina = actAs("amina@example.test", CurrentPrincipal.CUSTOMER);

        assertThat(viewers.updateMyProfile(amina, new ProfileChanges(null, null, null, " ", null, null, null, null,
            null, null))).isEmpty();
        assertThat(viewers.viewer(amina.accountId()).orElseThrow().customerProfile().displayName()).isEqualTo("Amina Okoye");
    }

    // ------------------------------------------------------------- helpers

    private CurrentPrincipal actAs(String email) {
        return actAs(email, CurrentPrincipal.STAFF);
    }

    private CurrentPrincipal actAs(String email, String kind) {
        CurrentPrincipal principal = new CurrentPrincipal(accountId(email),
            jdbc.sql("select id from organization where slug = 'aurelle'").query(UUID.class).single(), kind, UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        return principal;
    }

    private UUID roleId(CurrentPrincipal owner, String key) {
        return roles.roles(owner.organizationId()).stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow().id();
    }

    private UUID accountId(String email) {
        return jdbc.sql("select id from user_account where email = ?").param(email).query(UUID.class).single();
    }

    private long version(UUID accountId) {
        return jdbc.sql("select version from user_account where id = ?").param(accountId).query(Long.class).single();
    }
}
