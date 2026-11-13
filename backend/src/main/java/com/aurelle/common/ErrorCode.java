package com.aurelle.common;

/**
 * Stable GraphQL error codes ({@code errors[].extensions.code}) from {@code contracts/errors.md}.
 *
 * <p>These are protocol identifiers shared with clients, so they are a fixed enum. Each code has
 * a safe default message that never reveals internals.
 */
public enum ErrorCode {
    UNAUTHENTICATED("Sign in to continue."),
    FORBIDDEN("You don't have access to this."),
    NOT_FOUND("We couldn't find that."),
    BAD_REQUEST("The request is not valid."),
    CONFLICT("This was changed by someone else. Reload and try again."),
    RATE_LIMITED("Too many requests. Please wait and try again."),
    QUERY_TOO_COMPLEX("The query is too large or too deep."),
    TIMEOUT("The request took too long. Please try again."),
    UNAVAILABLE("The service is temporarily unavailable. Please try again."),
    INTERNAL("Something went wrong. Please try again.");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
