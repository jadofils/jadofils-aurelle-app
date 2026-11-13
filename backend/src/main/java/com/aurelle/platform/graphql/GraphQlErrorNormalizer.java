package com.aurelle.platform.graphql;

import com.aurelle.common.ErrorCode;
import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.graphql.execution.ErrorType;

/**
 * Guarantees that every error in a response carries {@code extensions.code} and a safe message,
 * including errors raised before any resolver runs (syntax, validation, depth/complexity limits).
 * Errors that already have a code (from {@link GraphQlExceptionResolver}) are returned unchanged.
 */
class GraphQlErrorNormalizer {

    GraphQLError normalize(GraphQLError error) {
        Map<String, Object> existing = error.getExtensions();
        if (existing != null && existing.get(ErrorCodes.CODE) instanceof String) {
            return error;
        }
        ErrorCode code = codeFor(error);
        Map<String, Object> extensions = new LinkedHashMap<>();
        if (existing != null) {
            extensions.putAll(existing);
        }
        extensions.put(ErrorCodes.CODE, code.name());
        // Parser, validation and limit messages describe the client's query and are safe to return.
        // Anything else is replaced with the generic message for its code.
        String message = keepsOriginalMessage(code) ? error.getMessage() : code.defaultMessage();
        return GraphqlErrorBuilder.newError()
            .message(message)
            .locations(error.getLocations())
            .path(error.getPath())
            .errorType(error.getErrorType() != null ? error.getErrorType() : ErrorCodes.classificationOf(code))
            .extensions(extensions)
            .build();
    }

    /**
     * graphql-java classifications that are not part of {@link graphql.ErrorType} but describe the
     * client's request: introspection switched off (prod profile) and bad-faith introspection.
     */
    private static final Map<String, ErrorCode> NAMED_CLASSIFICATIONS = Map.of(
        "IntrospectionDisabled", ErrorCode.BAD_REQUEST,
        "BadFaithIntrospection", ErrorCode.QUERY_TOO_COMPLEX);

    private static ErrorCode codeFor(GraphQLError error) {
        ErrorClassification classification = error.getErrorType();
        if (classification == null) {
            return ErrorCode.INTERNAL;
        }
        ErrorCode named = NAMED_CLASSIFICATIONS.get(String.valueOf(classification.toSpecification(error)));
        if (named != null) {
            return named;
        }
        if (classification instanceof graphql.ErrorType graphqlType) {
            return switch (graphqlType) {
                case InvalidSyntax, ValidationError, OperationNotSupported -> ErrorCode.BAD_REQUEST;
                case ExecutionAborted -> ErrorCode.QUERY_TOO_COMPLEX;
                case DataFetchingException, NullValueInNonNullableField -> ErrorCode.INTERNAL;
            };
        }
        if (classification instanceof ErrorType springType) {
            return switch (springType) {
                case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
                case UNAUTHORIZED -> ErrorCode.UNAUTHENTICATED;
                case FORBIDDEN -> ErrorCode.FORBIDDEN;
                case NOT_FOUND -> ErrorCode.NOT_FOUND;
                case INTERNAL_ERROR -> ErrorCode.INTERNAL;
            };
        }
        return ErrorCode.INTERNAL;
    }

    private static boolean keepsOriginalMessage(ErrorCode code) {
        return code == ErrorCode.BAD_REQUEST || code == ErrorCode.QUERY_TOO_COMPLEX;
    }
}
