package com.aurelle.identity;

import com.aurelle.common.CurrentPrincipal;
import java.util.Optional;

/** Resolves an opaque access token to the signed-in principal (active session, active account). */
public interface SessionAuthenticator {

    Optional<CurrentPrincipal> authenticate(String accessToken);
}
