package com.aurelle.common;

import java.util.Objects;

/**
 * An expected failure that maps to a GraphQL error code. The message must be safe to show to the
 * caller: it is returned as-is. Use the code's default message when in doubt.
 */
public class ApplicationException extends RuntimeException {

    private final ErrorCode code;

    public ApplicationException(ErrorCode code) {
        this(code, Objects.requireNonNull(code, "code").defaultMessage());
    }

    public ApplicationException(ErrorCode code, String safeMessage) {
        super(Objects.requireNonNull(safeMessage, "safeMessage"));
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode code() {
        return code;
    }
}
