package dev.scrapekit.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import dev.scrapekit.config.ScrapeKitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GET with the three things a production scraper needs: robots.txt, per-host rate limiting and
 * retries with backoff. Non-retryable statuses (404, 403, …) fail fast with a {@link FetchException}.
 */
public class HttpFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpFetcher.class);

    private final HttpClient http;
    private final HostRateLimiter limiter;
    private final RetryPolicy retry;
    private final RobotsPolicy robots;
    private final ScrapeKitProperties.Http cfg;
    private final HostRateLimiter.Sleeper sleeper;

    public HttpFetcher(HttpClient http, HostRateLimiter limiter, RetryPolicy retry, RobotsPolicy robots,
                       ScrapeKitProperties.Http cfg, HostRateLimiter.Sleeper sleeper) {
        this.http = http;
        this.limiter = limiter;
        this.retry = retry;
        this.robots = robots;
        this.cfg = cfg;
        this.sleeper = sleeper;
    }

    public FetchResult get(URI uri, Duration minDelay) {
        if (cfg.respectRobots() && !robots.isAllowed(uri)) {
            throw new FetchException("Blocked by robots.txt: " + uri, -1);
        }
        Duration delay = minDelay != null ? minDelay : cfg.defaultDelay();
        var request = HttpRequest.newBuilder(uri)
                .timeout(cfg.requestTimeout())
                .header("User-Agent", cfg.userAgent())
                .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
                .GET().build();
        int attempt = 0;
        while (true) {
            attempt++;
            Duration retryAfter = null;
            String failure;
            int status = -1;
            try {
                limiter.acquire(uri.getHost(), delay);
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    return new FetchResult(resp.uri(), status,
                            resp.headers().firstValue("Content-Type").orElse(""), resp.body(), attempt);
                }
                if (!retry.isRetryable(status)) {
                    throw new FetchException("HTTP " + status + " for " + uri, status);
                }
                retryAfter = RetryPolicy.parseRetryAfter(resp.headers().firstValue("Retry-After").orElse(null));
                failure = "HTTP " + status;
            } catch (IOException e) {
                failure = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("Interrupted while fetching " + uri, e);
            }
            if (attempt >= retry.maxAttempts()) {
                throw new FetchException("Giving up on " + uri + " after " + attempt + " attempts (" + failure + ")", status);
            }
            Duration wait = retry.backoff(attempt, retryAfter);
            log.info("Attempt {} for {} failed ({}), retrying in {} ms", attempt, uri, failure, wait.toMillis());
            try {
                sleeper.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FetchException("Interrupted while fetching " + uri, e);
            }
        }
    }
}
