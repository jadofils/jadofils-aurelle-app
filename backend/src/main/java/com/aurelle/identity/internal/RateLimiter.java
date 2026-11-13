package com.aurelle.identity.internal;

import com.aurelle.common.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window attempt limiter per key, bounded in memory. Best effort per instance: the
 * correctness control against password guessing is the per-account lock in the database.
 */
class RateLimiter {

    static final int MAX_KEYS = 50_000;

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    RateLimiter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    /** Counts one attempt for {@code key}; throws {@link RateLimitedException} above the limit. */
    void acquire(String key) {
        long now = clock.millis();
        if (windows.size() >= MAX_KEYS) {
            windows.entrySet().removeIf(e -> e.getValue().endsAt <= now);
            if (windows.size() >= MAX_KEYS) {
                windows.clear();
            }
        }
        Window current = windows.compute(key, (k, w) -> w == null || w.endsAt <= now
            ? new Window(now + window.toMillis(), 1)
            : new Window(w.endsAt, w.count + 1));
        if (current.count > limit) {
            throw new RateLimitedException((current.endsAt - now + 999) / 1000);
        }
    }

    private record Window(long endsAt, int count) {
    }
}
