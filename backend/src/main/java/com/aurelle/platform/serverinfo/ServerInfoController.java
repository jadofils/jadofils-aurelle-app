package com.aurelle.platform.serverinfo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Public {@code serverInfo} query (no authentication; returns no sensitive data). */
@Controller
@EnableConfigurationProperties(ApiProperties.class)
class ServerInfoController {

    private final ServerInfo serverInfo;

    ServerInfoController(ApiProperties properties) {
        this.serverInfo = new ServerInfo(properties.version(), properties.contractPhase());
    }

    @QueryMapping
    ServerInfo serverInfo() {
        return serverInfo;
    }

    /** GraphQL {@code ServerInfo}. */
    record ServerInfo(String apiVersion, int contractPhase) {
    }
}
