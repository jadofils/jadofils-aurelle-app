package com.aurelle;

import com.aurelle.identity.SessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Application-wide HTTP security baseline: deny by default.
 *
 * <p>Public: the liveness/readiness probes and the GraphQL endpoint. {@code /graphql} must be
 * reachable because public queries (serverInfo, shopSettings, catalog browse) need no account.
 * Every GraphQL operation is authorized in its use case/resolver. That is the real control.
 *
 * <p>Sessions (decision D5): {@link SessionAuthenticationFilter} accepts a bearer token, or the
 * HttpOnly session cookie only when the request's {@code Origin} is allowed. That Origin check is the
 * CSRF protection for cookie sessions, so Spring's token-based CSRF filter is not used on /graphql.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    static final String GRAPHQL_PATH = "/graphql";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SessionAuthenticationFilter sessionFilter)
            throws Exception {
        http
            .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
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
