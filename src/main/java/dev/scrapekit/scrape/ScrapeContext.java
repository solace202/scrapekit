package dev.scrapekit.scrape;

import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;

import dev.scrapekit.http.FetchResult;
import dev.scrapekit.http.HttpFetcher;

/**
 * What a scraper sees during a run: a polite fetcher with a page budget, and a sink for items.
 * Counts pages and retries for the run history.
 */
public class ScrapeContext {

    private final HttpFetcher fetcher;
    private final URI startUrl;
    private final int maxPages;
    private final Duration delay;
    private final Consumer<ScrapedItem> sink;
    private int pages;
    private int retries;

    public ScrapeContext(HttpFetcher fetcher, URI startUrl, int maxPages, Duration delay, Consumer<ScrapedItem> sink) {
        this.fetcher = fetcher;
        this.startUrl = startUrl;
        this.maxPages = maxPages;
        this.delay = delay;
        this.sink = sink;
    }

    public URI startUrl() {
        return startUrl;
    }

    public FetchResult fetch(URI uri) {
        if (pages >= maxPages) {
            throw new PageLimitReachedException(maxPages);
        }
        FetchResult r = fetcher.get(uri, delay);
        pages++;
        retries += r.retries();
        return r;
    }

    public void emit(ScrapedItem item) {
        sink.accept(item);
    }

    public int pagesFetched() {
        return pages;
    }

    public int retries() {
        return retries;
    }
}
