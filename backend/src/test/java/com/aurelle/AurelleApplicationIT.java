package com.aurelle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Boots the full application against PostgreSQL and checks the HTTP security baseline. */
class AurelleApplicationIT extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthProbesArePublicAndUp() {
        assertThat(mvc.get().uri("/actuator/health/readiness"))
            .hasStatusOk()
            .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void healthDetailsAreNotExposed() {
        assertThat(mvc.get().uri("/actuator/health"))
            .bodyJson().doesNotHavePath("$.components");
    }

    @Test
    void everythingExceptHealthAndGraphqlIsDenied() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus4xxClientError();
        assertThat(mvc.get().uri("/actuator/info")).hasStatus4xxClientError();
        assertThat(mvc.get().uri("/anything")).hasStatus4xxClientError();
        assertThat(mvc.post().uri("/login")).hasStatus4xxClientError();
        assertThat(mvc.get().uri("/graphiql")).hasStatus4xxClientError();
    }
}
