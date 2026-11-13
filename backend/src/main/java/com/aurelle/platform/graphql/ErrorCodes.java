package com.aurelle.platform.graphql;

import com.aurelle.common.ErrorCode;
import graphql.ErrorClassification;
import org.springframework.graphql.execution.ErrorType;

/** Shared constants and the mapping from Aurelle error codes to Spring GraphQL classifications. */
final class ErrorCodes {

    /** Extension key carrying the stable code from {@code contracts/errors.md}. */
    static final String CODE = "code";
    /** Extension key carrying the correlation ID logged with an {@code INTERNAL} error. */
    static final String CORRELATION_ID = "correlationId";

    private ErrorCodes() {
    }

    static ErrorClassification classificationOf(ErrorCode code) {
        return switch (code) {
            case UNAUTHENTICATED -> ErrorType.UNAUTHORIZED;
            case FORBIDDEN -> ErrorType.FORBIDDEN;
            case NOT_FOUND -> ErrorType.NOT_FOUND;
            case BAD_REQUEST, CONFLICT, RATE_LIMITED, QUERY_TOO_COMPLEX -> ErrorType.BAD_REQUEST;
            case TIMEOUT, UNAVAILABLE, INTERNAL -> ErrorType.INTERNAL_ERROR;
        };
    }
}
