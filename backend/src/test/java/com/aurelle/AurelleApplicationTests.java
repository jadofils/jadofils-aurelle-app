package com.aurelle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Boots the full application against a real PostgreSQL and checks the security baseline. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AurelleApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Test
    void healthProbesArePublicAndUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk());
    }

    @Test
    void everythingElseIsDeniedByDefault() throws Exception {
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"{__typename}\"}"))
            .andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/env"))
            .andExpect(status().is4xxClientError());
        mvc.perform(get("/anything"))
            .andExpect(status().is4xxClientError());
    }
}
