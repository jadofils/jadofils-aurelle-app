package com.aurelle.common;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The signed-in caller of the current request, set by the identity module's session filter.
 * Other modules read it through {@link #current()} and authorize with the authorization module.
 *
 * @param accountId      {@code user_account.id}
 * @param organizationId tenant scope
 * @param kind           {@code CUSTOMER} or {@code STAFF}
 * @param sessionId      {@code auth_session.id} of this request
 */
public record CurrentPrincipal(UUID accountId, UUID organizationId, String kind, UUID sessionId) {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String STAFF = "STAFF";

    public CurrentPrincipal {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sessionId, "sessionId");
    }

    public boolean isStaff() {
        return STAFF.equals(kind);
    }

    /** The caller of the current request, or empty for guests. */
    public static Optional<CurrentPrincipal> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}
