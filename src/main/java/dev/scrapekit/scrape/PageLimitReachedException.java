package dev.scrapekit.scrape;

/** Thrown by {@link ScrapeContext#fetch} when the job's page budget is used up; ends the run normally. */
public class PageLimitReachedException extends RuntimeException {

    public PageLimitReachedException(int limit) {
        super("Stopped at the page limit (" + limit + " pages)", null, false, false);
    }
}
