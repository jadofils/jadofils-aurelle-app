package com.aurelle;

import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Base for integration tests (*IT). All subclasses share one Spring context and one PostgreSQL
 * container, and run a real server on a random port so GraphQL goes through Tomcat and security.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "aurelle.seed.password=" + IntegrationTest.SEED_PASSWORD)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import({PostgresTestConfiguration.class, SchemaReportCapture.class})
public abstract class IntegrationTest {

    /** Test-only password of every seeded account (not a secret; tests run against throwaway databases). */
    public static final String SEED_PASSWORD = "Seed-Password-For-Tests-1";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
        new ParameterizedTypeReference<>() { };

    @LocalServerPort
    protected int port;

    /** POSTs a GraphQL document and returns the HTTP response with the parsed JSON body. */
    protected ResponseEntity<Map<String, Object>> graphql(String query) {
        return RestClient.create("http://localhost:" + port)
            .post()
            .uri("/graphql")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON)
            .body(Map.of("query", query))
            .retrieve()
            .onStatus(status -> true, (request, response) -> { })
            .toEntity(JSON_MAP);
    }
}
