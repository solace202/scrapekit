package dev.scrapekit.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HostRateLimiterTest {

    final AtomicLong now = new AtomicLong();
    final List<Duration> sleeps = new ArrayList<>();
    final HostRateLimiter limiter = new HostRateLimiter(now::get, d -> {
        sleeps.add(d);
        now.addAndGet(d.toNanos());
    });

    @Test
    @DisplayName("Back-to-back requests to one host are spaced by the minimum interval")
    void spacesRequests() throws InterruptedException {
        Duration gap = Duration.ofMillis(500);
        limiter.acquire("books.toscrape.com", gap);
        limiter.acquire("books.toscrape.com", gap);
        limiter.acquire("BOOKS.toscrape.com", gap);
        assertThat(sleeps).containsExactly(Duration.ZERO, gap, gap);
    }

    @Test
    @DisplayName("Different hosts don't wait for each other, and idle time is not 'saved up'")
    void hostsAreIndependent() throws InterruptedException {
        Duration gap = Duration.ofSeconds(1);
        limiter.acquire("a.example", gap);
        limiter.acquire("b.example", gap);
        now.addAndGet(Duration.ofSeconds(10).toNanos());
        limiter.acquire("a.example", gap);
        limiter.acquire("a.example", gap);
        assertThat(sleeps).containsExactly(Duration.ZERO, Duration.ZERO, Duration.ZERO, gap);
    }
}
