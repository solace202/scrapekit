package dev.scrapekit.job;

import java.time.Instant;

public record ScrapeJob(long id, String name, String source, String startUrl, int maxPages, int delayMs,
                        String cron, boolean enabled, Instant createdAt, Instant updatedAt) {

    public boolean scheduled() {
        return enabled && cron != null && !cron.isBlank();
    }
}
