package com.aurelle.platform.graphql;

import com.aurelle.common.ApplicationException;
import com.aurelle.common.ErrorCode;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Turns exceptions thrown by resolvers into GraphQL errors with a stable {@code extensions.code}
 * and a safe message. Unexpected exceptions become {@code INTERNAL}: the cause is logged with a
 * correlation ID and only that ID is returned to the client.
 */
class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionResolver.class);
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        ErrorCode code;
        String message;
        if (ex instanceof ApplicationException app) {
            code = app.code();
            message = app.getMessage();
        } else {
            code = codeFor(ex);
            message = code.defaultMessage();
        }
        extensions.put(ErrorCodes.CODE, code.name());
        if (code == ErrorCode.INTERNAL) {
            String correlationId = UUID.randomUUID().toString();
            extensions.put(ErrorCodes.CORRELATION_ID, correlationId);
            log.atError()
                .addKeyValue(ErrorCodes.CORRELATION_ID, correlationId)
                .addKeyValue("graphqlPath", env.getExecutionStepInfo().getPath().toString())
                .setCause(ex)
                .log("Unhandled exception in GraphQL data fetcher");
        }
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorCodes.classificationOf(code))
            .message(message)
            .extensions(extensions)
            .build();
    }

    private ErrorCode codeFor(Throwable ex) {
        if (ex instanceof AuthenticationException) {
            return ErrorCode.UNAUTHENTICATED;
        }
        if (ex instanceof AccessDeniedException) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return trustResolver.isAuthenticated(authentication) ? ErrorCode.FORBIDDEN : ErrorCode.UNAUTHENTICATED;
        }
        if (ex instanceof OptimisticLockingFailureException) {
            return ErrorCode.CONFLICT;
        }
        if (ex instanceof QueryTimeoutException) {
            return ErrorCode.TIMEOUT;
        }
        if (ex instanceof DataAccessResourceFailureException) {
            return ErrorCode.UNAVAILABLE;
        }
        if (ex instanceof ConstraintViolationException) {
            return ErrorCode.BAD_REQUEST;
        }
        return ErrorCode.INTERNAL;
    }
}
