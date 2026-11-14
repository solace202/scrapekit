package dev.scrapekit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("scrapekit")
public record ScrapeKitProperties(Http http, Scheduler scheduler, Sheets sheets) {

    /**
     * Polite HTTP client settings.
     *
     * @param userAgent       sent with every request and matched against robots.txt groups
     * @param connectTimeout  TCP connect timeout
     * @param requestTimeout  timeout for a whole request
     * @param maxAttempts     total attempts per URL (1 = no retry)
     * @param initialBackoff  first retry delay, doubled on every attempt (with jitter)
     * @param maxBackoff      upper bound for one retry delay, also caps Retry-After
     * @param defaultDelay    minimum gap between two requests to the same host when a job sets none
     * @param respectRobots   skip URLs disallowed by robots.txt
     */
    public record Http(@DefaultValue("ScrapeKit/0.1 (+https://github.com/solace202/scrapekit)") String userAgent,
                       @DefaultValue("10s") Duration connectTimeout,
                       @DefaultValue("30s") Duration requestTimeout,
                       @DefaultValue("4") int maxAttempts,
                       @DefaultValue("1s") Duration initialBackoff,
                       @DefaultValue("30s") Duration maxBackoff,
                       @DefaultValue("500ms") Duration defaultDelay,
                       @DefaultValue("true") boolean respectRobots) {
    }

    /**
     * @param enabled register cron jobs on startup
     * @param zone    time zone used to evaluate cron expressions
     */
    public record Scheduler(@DefaultValue("true") boolean enabled, @DefaultValue("UTC") String zone) {
    }

    /**
     * Optional Google Sheets export. Disabled unless a service-account key file is configured.
     *
     * @param credentialsFile path to a service-account JSON key (the sheet must be shared with that account)
     * @param spreadsheetId   default spreadsheet to write to
     * @param apiBaseUrl      Sheets API base URL (overridable for tests)
     */
    public record Sheets(String credentialsFile, String spreadsheetId,
                         @DefaultValue("https://sheets.googleapis.com") String apiBaseUrl) {

        public boolean configured() {
            return credentialsFile != null && !credentialsFile.isBlank();
        }
    }
}
