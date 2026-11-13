package com.aurelle.common;

/** Too many attempts; the client should wait {@link #retryAfterSeconds()} (GraphQL {@code RATE_LIMITED}). */
public class RateLimitedException extends ApplicationException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
