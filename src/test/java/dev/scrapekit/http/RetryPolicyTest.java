package dev.scrapekit.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    @DisplayName("Only transient statuses are retried (429 and 5xx yes, 404 and 403 no)")
    void retryableStatuses() {
        var p = new RetryPolicy(4, Duration.ofSeconds(1), Duration.ofSeconds(30));
        assertThat(p.isRetryable(429)).isTrue();
        assertThat(p.isRetryable(503)).isTrue();
        assertThat(p.isRetryable(404)).isFalse();
        assertThat(p.isRetryable(403)).isFalse();
    }

    @Test
    @DisplayName("Backoff doubles per attempt, with jitter between 50% and 100% of the step")
    void exponentialWithJitter() {
        var low = new RetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(30), () -> 0.0);
        var high = new RetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(30), () -> 0.999999);
        assertThat(low.backoff(1, null)).isEqualTo(Duration.ofMillis(500));
        assertThat(low.backoff(3, null)).isEqualTo(Duration.ofMillis(2000));
        assertThat(high.backoff(3, null)).isBetween(Duration.ofMillis(3990), Duration.ofMillis(4000));
    }

    @Test
    @DisplayName("Backoff never exceeds the maximum")
    void capped() {
        var p = new RetryPolicy(30, Duration.ofSeconds(1), Duration.ofSeconds(30), () -> 0.999999);
        assertThat(p.backoff(25, null)).isLessThanOrEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("Retry-After from the server wins, capped at the maximum backoff")
    void retryAfter() {
        var p = new RetryPolicy(4, Duration.ofSeconds(1), Duration.ofSeconds(30));
        assertThat(p.backoff(1, RetryPolicy.parseRetryAfter("7"))).isEqualTo(Duration.ofSeconds(7));
        assertThat(p.backoff(1, RetryPolicy.parseRetryAfter("3600"))).isEqualTo(Duration.ofSeconds(30));
        assertThat(RetryPolicy.parseRetryAfter("Wed, 21 Oct 2015 07:28:00 GMT")).isNull();
    }
}
