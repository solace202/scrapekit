package dev.scrapekit.http;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Exponential backoff with jitter for transient failures (timeouts, 429, 5xx). */
public class RetryPolicy {

    private static final Set<Integer> RETRYABLE = Set.of(408, 425, 429, 500, 502, 503, 504);

    private final int maxAttempts;
    private final Duration initial;
    private final Duration max;
    private final DoubleSupplier random;

    public RetryPolicy(int maxAttempts, Duration initial, Duration max) {
        this(maxAttempts, initial, max, () -> ThreadLocalRandom.current().nextDouble());
    }

    public RetryPolicy(int maxAttempts, Duration initial, Duration max, DoubleSupplier random) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initial = initial;
        this.max = max;
        this.random = random;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean isRetryable(int status) {
        return RETRYABLE.contains(status);
    }

    /**
     * Delay before the next attempt. {@code attempt} is the attempt that just failed (1-based).
     * A server-provided Retry-After wins but is capped at the max backoff.
     */
    public Duration backoff(int attempt, Duration retryAfter) {
        if (retryAfter != null && !retryAfter.isNegative()) {
            return retryAfter.compareTo(max) > 0 ? max : retryAfter;
        }
        long base = initial.toMillis() << Math.min(attempt - 1, 20);
        long capped = Math.min(base, max.toMillis());
        // "equal jitter": half fixed, half random, so retries from many workers don't line up
        long half = capped / 2;
        return Duration.ofMillis(half + (long) (random.getAsDouble() * (capped - half)));
    }

    /** Parses a Retry-After header given in seconds; HTTP-date values are ignored. */
    public static Duration parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
