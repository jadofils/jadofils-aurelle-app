package com.aurelle.identity.internal;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seed profile only: gives the seed scripts ({@code db/seed}) the Flyway placeholder
 * {@code seed_password_hash}, an Argon2id hash of {@code AURELLE_SEED_PASSWORD}. The password is
 * never committed; without it the seeded accounts get a hash of a random value nobody knows.
 */
@Component
@Profile("seed")
class SeedPasswordPlaceholder implements FlywayConfigurationCustomizer {

    static final String PLACEHOLDER = "seed_password_hash";
    private static final Logger log = LoggerFactory.getLogger(SeedPasswordPlaceholder.class);

    private final PasswordHasher hasher;
    private final String seedPassword;

    SeedPasswordPlaceholder(PasswordHasher hasher, @Value("${aurelle.seed.password:}") String seedPassword) {
        this.hasher = hasher;
        this.seedPassword = seedPassword;
    }

    @Override
    public void customize(FluentConfiguration configuration) {
        configuration.placeholders(Map.of(PLACEHOLDER, hasher.hash(passwordOrRandom())));
    }

    String passwordOrRandom() {
        if (seedPassword != null && !seedPassword.isBlank()) {
            return seedPassword;
        }
        log.warn("AURELLE_SEED_PASSWORD is not set: seeded accounts get a random password and cannot sign in");
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return Base64.getEncoder().encodeToString(random);
    }
}
