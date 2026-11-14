package dev.scrapekit.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.scrapekit.http.HostRateLimiter;
import dev.scrapekit.http.HttpFetcher;
import dev.scrapekit.http.RetryPolicy;
import dev.scrapekit.http.RobotsPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HttpClient scrapeHttpClient(ScrapeKitProperties props) {
        return HttpClient.newBuilder()
                .connectTimeout(props.http().connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    HostRateLimiter hostRateLimiter() {
        return new HostRateLimiter(System::nanoTime, HostRateLimiter.Sleeper.THREAD);
    }

    @Bean
    RetryPolicy retryPolicy(ScrapeKitProperties props) {
        var h = props.http();
        return new RetryPolicy(h.maxAttempts(), h.initialBackoff(), h.maxBackoff());
    }

    @Bean
    RobotsPolicy robotsPolicy(HttpClient scrapeHttpClient, ScrapeKitProperties props) {
        return new RobotsPolicy(scrapeHttpClient, props.http().userAgent(), props.http().requestTimeout());
    }

    @Bean
    HttpFetcher httpFetcher(HttpClient scrapeHttpClient, HostRateLimiter limiter, RetryPolicy retryPolicy,
                            RobotsPolicy robots, ScrapeKitProperties props) {
        return new HttpFetcher(scrapeHttpClient, limiter, retryPolicy, robots, props.http(), HostRateLimiter.Sleeper.THREAD);
    }

    /** Scrape runs are I/O bound and mostly sleep between requests: one virtual thread per run. */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService runExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("scrape-run-", 1).factory());
    }

    @Bean
    ThreadPoolTaskScheduler jobTaskScheduler() {
        var s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("cron-");
        s.setWaitForTasksToCompleteOnShutdown(false);
        return s;
    }
}
