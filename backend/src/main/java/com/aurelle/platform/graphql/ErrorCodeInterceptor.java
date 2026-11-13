package com.aurelle.platform.graphql;

import graphql.GraphQLError;
import java.util.List;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;

/** Applies {@link GraphQlErrorNormalizer} to every HTTP and WebSocket GraphQL response. */
class ErrorCodeInterceptor implements WebGraphQlInterceptor {

    private final GraphQlErrorNormalizer normalizer;

    ErrorCodeInterceptor(GraphQlErrorNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        return chain.next(request).map(response -> {
            List<GraphQLError> errors = response.getExecutionResult().getErrors();
            if (errors.isEmpty()) {
                return response;
            }
            List<GraphQLError> normalized = errors.stream().map(normalizer::normalize).toList();
            return response.transform(builder -> builder.errors(normalized));
        });
    }
}
