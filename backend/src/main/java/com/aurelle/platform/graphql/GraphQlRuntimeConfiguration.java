package com.aurelle.platform.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * GraphQL runtime shared by every module: scalars, query depth/complexity limits, error codes.
 * Spring Boot picks up {@code Instrumentation}, {@code DataFetcherExceptionResolver} and
 * {@code WebGraphQlInterceptor} beans automatically.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GraphQlLimitsProperties.class)
class GraphQlRuntimeConfiguration {

    @Bean
    RuntimeWiringConfigurer aurelleScalars() {
        return wiring -> wiring
            .scalar(Scalars.DATE_TIME)
            .scalar(Scalars.DATE)
            .scalar(Scalars.LONG);
    }

    @Bean
    MaxQueryDepthInstrumentation maxQueryDepthInstrumentation(GraphQlLimitsProperties limits) {
        return new MaxQueryDepthInstrumentation(limits.maxDepth());
    }

    @Bean
    MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation(GraphQlLimitsProperties limits) {
        return new MaxQueryComplexityInstrumentation(limits.maxComplexity());
    }

    @Bean
    GraphQlExceptionResolver graphQlExceptionResolver() {
        return new GraphQlExceptionResolver();
    }

    @Bean
    ErrorCodeInterceptor errorCodeInterceptor() {
        return new ErrorCodeInterceptor(new GraphQlErrorNormalizer());
    }
}
