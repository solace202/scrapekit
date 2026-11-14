package dev.scrapekit.scrape;

import java.util.List;

/** A site-specific extractor. Implementations are Spring beans, picked by {@link #id()}. */
public interface Scraper {

    /** Value stored in {@code scrape_job.source}. */
    String id();

    String description();

    String defaultStartUrl();

    List<Column> columns();

    /** Crawls from {@link ScrapeContext#startUrl()}, following pagination, and emits items. */
    void scrape(ScrapeContext ctx);
}
