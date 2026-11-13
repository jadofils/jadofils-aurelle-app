package com.aurelle;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Application-wide HTTP security baseline: deny by default.
 *
 * <p>Public: the liveness/readiness probes and the GraphQL endpoint. {@code /graphql} must be
 * reachable because public queries (serverInfo, shopSettings, catalog browse) need no account.
 * Every GraphQL operation is authorized in its use case/resolver. That is the real control.
 *
 * <p>CSRF is not applied to {@code /graphql} while no cookie-based session exists. Decision D5
 * (Phase 1) revisits this: if web sessions use cookies, CSRF protection is added for them.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    static final String GRAPHQL_PATH = "/graphql";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")
                .permitAll()
                .requestMatchers(GRAPHQL_PATH)
                .permitAll()
                .anyRequest().denyAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers(GRAPHQL_PATH))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable());
        return http.build();
    }
}
