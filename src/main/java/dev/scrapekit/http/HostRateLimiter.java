package dev.scrapekit.http;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Keeps a minimum gap between two requests to the same host, across all running jobs.
 * Each caller reserves the next free slot for the host and sleeps until it, so concurrent
 * callers are spaced out instead of bursting.
 */
public class HostRateLimiter {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;

        Sleeper THREAD = d -> {
            if (!d.isNegative() && !d.isZero()) {
                Thread.sleep(d);
            }
        };
    }

    private final LongSupplier nanoClock;
    private final Sleeper sleeper;
    private final Map<String, Long> nextSlot = new HashMap<>();

    public HostRateLimiter(LongSupplier nanoClock, Sleeper sleeper) {
        this.nanoClock = nanoClock;
        this.sleeper = sleeper;
    }

    /** Blocks until a request to {@code host} is allowed; returns how long it waited. */
    public Duration acquire(String host, Duration minInterval) throws InterruptedException {
        long wait = reserve(host.toLowerCase(), minInterval.toNanos());
        Duration d = Duration.ofNanos(wait);
        sleeper.sleep(d);
        return d;
    }

    private synchronized long reserve(String host, long interval) {
        long now = nanoClock.getAsLong();
        long slot = Math.max(now, nextSlot.getOrDefault(host, now));
        nextSlot.put(host, slot + interval);
        return slot - now;
    }
}
