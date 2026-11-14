package dev.scrapekit.run;

import java.time.Duration;
import java.time.Instant;

public record ScrapeRun(long id, long jobId, Trigger trigger, Status status, Instant startedAt, Instant finishedAt,
                        int pagesFetched, int itemsFound, int itemsNew, int itemsUpdated, int itemsUnchanged,
                        int retries, String message) {

    public enum Trigger { MANUAL, SCHEDULE, API }

    public enum Status { RUNNING, SUCCEEDED, PARTIAL, FAILED }

    public Duration duration() {
        return finishedAt == null ? null : Duration.between(startedAt, finishedAt);
    }
}
