/**
 * Shared kernel: small, stable primitives used by every module (error codes, money).
 *
 * <p>Keep this package small. Mutable authorization data (roles, grants) never lives here.
 */
@ApplicationModule(displayName = "Shared kernel", type = ApplicationModule.Type.OPEN)
package com.aurelle.common;

import org.springframework.modulith.ApplicationModule;
