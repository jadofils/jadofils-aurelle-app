package com.aurelle.platform.graphql;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds on every GraphQL operation ({@code aurelle.graphql.*}). Exceeding them returns
 * {@code QUERY_TOO_COMPLEX}.
 *
 * @param maxDepth      maximum selection depth (introspection queries count too)
 * @param maxComplexity maximum field count (graphql-java default complexity of 1 per field)
 */
@Validated
@ConfigurationProperties("aurelle.graphql")
record GraphQlLimitsProperties(
    @Min(1) @Max(50) int maxDepth,
    @Min(1) @Max(10_000) int maxComplexity) {
}
