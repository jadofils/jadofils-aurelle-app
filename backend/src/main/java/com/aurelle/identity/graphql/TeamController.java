package com.aurelle.identity.graphql;

import com.aurelle.authorization.RoleAdministration.GrantInput;
import com.aurelle.identity.internal.AuthService.AuthResult;
import com.aurelle.identity.internal.AuthService.Client;
import com.aurelle.identity.internal.TeamService;
import com.aurelle.identity.internal.TeamService.RollbackWithErrors;
import com.aurelle.identity.internal.TeamService.StaffMemberView;
import com.aurelle.identity.internal.TeamService.StaffPage;
import com.aurelle.identity.internal.TeamService.StaffResult;
import com.aurelle.organization.LocationSummary;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Team screens (contracts/graphql/01-identity.graphqls). Permissions are checked in TeamService. */
@Controller
class TeamController {

    private final TeamService team;
    private final AccountController accounts;

    TeamController(TeamService team, AccountController accounts) {
        this.team = team;
        this.accounts = accounts;
    }

    @QueryMapping
    StaffMemberConnection staffMembers(@Argument Integer first, @Argument String after,
            @Argument Map<String, Object> filter) {
        Map<String, Object> f = filter == null ? Map.of() : filter;
        Object roleId = f.get("roleId");
        StaffPage page = team.staffMembers(first == null ? 25 : first, after, (String) f.get("search"),
            AccountController.strings(f, "status"), roleId == null ? null : UUID.fromString(roleId.toString()));
        List<StaffMemberEdge> edges = page.members().stream().map(m -> new StaffMemberEdge(m.cursor(), m)).toList();
        return new StaffMemberConnection(edges, new PageInfo(page.hasNextPage(), page.hasPreviousPage(),
            edges.isEmpty() ? null : edges.getFirst().cursor(), edges.isEmpty() ? null : edges.getLast().cursor()),
            page.totalCount());
    }

    @QueryMapping
    StaffMemberView staffMember(@Argument String id) {
        return team.staffMember(UUID.fromString(id)).orElse(null);
    }

    @QueryMapping
    List<LocationSummary> locations() {
        return team.locations();
    }

    @MutationMapping
    AccountController.AuthPayload acceptStaffInvite(@Argument Map<String, Object> input,
            @ContextValue(ClientContext.KEY) ClientContext client) {
        String label = AccountController.str(input, "deviceLabel");
        AuthResult result = team.acceptInvite(AccountController.str(input, "token"),
            AccountController.str(input, "password"), AccountController.str(input, "displayName"),
            new Client(client.platform(), label, client.rateKey()));
        return accounts.authPayload(result, client);
    }

    @MutationMapping
    StaffResult inviteStaffMember(@Argument Map<String, Object> input) {
        return guarded(() -> team.invite(AccountController.str(input, "displayName"),
            AccountController.str(input, "email"), AccountController.str(input, "jobTitle"), ids(input.get("roleIds")),
            uuid(input.get("locationId"))));
    }

    @MutationMapping
    StaffResult updateStaffMember(@Argument Map<String, Object> input) {
        return guarded(() -> team.update(uuid(input.get("staffMemberId")), AccountController.str(input, "displayName"),
            AccountController.str(input, "jobTitle"), AccountController.str(input, "status"),
            uuid(input.get("defaultLocationId")), ((Number) input.get("expectedVersion")).longValue()));
    }

    @MutationMapping
    StaffResult setStaffRoles(@Argument Map<String, Object> input) {
        return guarded(() -> team.setRoles(uuid(input.get("staffMemberId")), ids(input.get("roleIds")),
            uuid(input.get("locationId")), ((Number) input.get("expectedVersion")).longValue()));
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    StaffResult setStaffPermissionOverrides(@Argument Map<String, Object> input) {
        List<GrantInput> overrides = ((Collection<Map<String, Object>>) input.get("overrides")).stream()
            .map(g -> new GrantInput((String) g.get("key"), g.get("effect").toString())).toList();
        return guarded(() -> team.setOverrides(uuid(input.get("staffMemberId")), overrides,
            ((Number) input.get("expectedVersion")).longValue()));
    }

    /** Business errors detected after writes roll the transaction back and come back as UserErrors. */
    private static StaffResult guarded(Supplier<StaffResult> call) {
        try {
            return call.get();
        } catch (RollbackWithErrors ex) {
            return new StaffResult(null, ex.errors());
        }
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> ids(Object value) {
        return value == null ? List.of()
            : ((Collection<Object>) value).stream().map(v -> UUID.fromString(v.toString())).toList();
    }

    record StaffMemberEdge(String cursor, StaffMemberView node) {
    }

    record PageInfo(boolean hasNextPage, boolean hasPreviousPage, String startCursor, String endCursor) {
    }

    record StaffMemberConnection(List<StaffMemberEdge> edges, PageInfo pageInfo, long totalCount) {
    }
}
