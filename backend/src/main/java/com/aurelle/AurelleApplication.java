package com.aurelle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Aurelle modular monolith.
 *
 * <p>Each direct sub-package of {@code com.aurelle} (e.g. {@code identity}, {@code catalog},
 * {@code inventory}) is an application module. Boundaries are verified by Spring Modulith in
 * {@code ModularityTests}. Modules are added with their first vertical slice; see
 * {@code docs/architecture.md}.
 *
 * <p>The default in-memory user (with a generated password logged at startup) is excluded:
 * identities come from the identity module, never from a demo account.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AurelleApplication {

    public static void main(String[] args) {
        SpringApplication.run(AurelleApplication.class, args);
    }
}
