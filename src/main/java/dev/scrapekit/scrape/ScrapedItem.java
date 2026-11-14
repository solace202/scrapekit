package dev.scrapekit.scrape;

import java.util.Map;

/**
 * One record found by a scraper.
 *
 * @param key    stable natural key used for dedup (e.g. product URL), unique per job
 * @param fields column values; keys match the scraper's {@link Column#key()}
 */
public record ScrapedItem(String key, Map<String, Object> fields) {
}
