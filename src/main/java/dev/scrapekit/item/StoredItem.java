package dev.scrapekit.item;

import java.time.Instant;
import java.util.Map;

public record StoredItem(String key, Map<String, Object> fields, Instant firstSeenAt, Instant lastSeenAt,
                         Instant changedAt) {
}
