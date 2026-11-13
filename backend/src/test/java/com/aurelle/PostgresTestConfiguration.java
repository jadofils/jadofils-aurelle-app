package com.aurelle;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container for all integration tests. Same version as production (D23); the
 * Spring test context (and so the container) is shared by every IT that imports this class.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    public static final String POSTGRES_IMAGE = "postgres:18.7-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
