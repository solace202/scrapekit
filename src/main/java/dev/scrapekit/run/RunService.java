package dev.scrapekit.run;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import dev.scrapekit.http.FetchException;
import dev.scrapekit.item.ItemStore;
import dev.scrapekit.job.JobRepository;
import dev.scrapekit.job.NotFoundException;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.scrape.PageLimitReachedException;
import dev.scrapekit.scrape.ScrapeContext;
import dev.scrapekit.scrape.ScrapedItem;
import dev.scrapekit.scrape.Scraper;
import dev.scrapekit.scrape.ScraperRegistry;
import dev.scrapekit.http.HttpFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Starts runs and executes them in the background. Items are written in batches as they are found,
 * so a failure halfway keeps what was scraped (status PARTIAL) and progress is visible while running.
 */
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);
    static final int BATCH = 200;

    private final JobRepository jobs;
    private final RunRepository runs;
    private final ItemStore items;
    private final ScraperRegistry scrapers;
    private final HttpFetcher fetcher;
    private final ExecutorService executor;

    public RunService(JobRepository jobs, RunRepository runs, ItemStore items, ScraperRegistry scrapers,
                      HttpFetcher fetcher, ExecutorService runExecutor) {
        this.jobs = jobs;
        this.runs = runs;
        this.items = items;
        this.scrapers = scrapers;
        this.fetcher = fetcher;
        this.executor = runExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    void cleanUpAfterRestart() {
        int n = runs.failOrphans();
        if (n > 0) {
            log.warn("Marked {} interrupted run(s) as FAILED", n);
        }
    }

    /** Creates a RUNNING run and executes it asynchronously. */
    public ScrapeRun trigger(long jobId, ScrapeRun.Trigger trigger) {
        ScrapeJob job = jobs.findById(jobId).orElseThrow(() -> new NotFoundException("Job " + jobId + " not found"));
        ScrapeRun run;
        try {
            run = runs.start(jobId, trigger);
        } catch (DuplicateKeyException e) {
            throw new JobAlreadyRunningException(jobId);
        }
        executor.submit(() -> execute(job, run));
        return run;
    }

    public ScrapeRun get(long runId) {
        return runs.findById(runId).orElseThrow(() -> new NotFoundException("Run " + runId + " not found"));
    }

    void execute(ScrapeJob job, ScrapeRun run) {
        Scraper scraper = scrapers.get(job.source());
        RunStats stats = new RunStats();
        List<ScrapedItem> buffer = new ArrayList<>(BATCH);
        Set<String> seen = new HashSet<>();
        int[] duplicates = {0};
        var ctx = new ScrapeContext[1];
        ctx[0] = new ScrapeContext(fetcher, URI.create(job.startUrl()), job.maxPages(), Duration.ofMillis(job.delayMs()),
                item -> {
                    if (!seen.add(item.key())) {     // same record listed twice in one crawl
                        duplicates[0]++;
                        return;
                    }
                    buffer.add(item);
                    if (buffer.size() >= BATCH) {
                        flush(job, run, buffer, stats);
                        runs.progress(run.id(), ctx[0].pagesFetched(), stats, ctx[0].retries());
                    }
                });
        ScrapeRun.Status status = ScrapeRun.Status.SUCCEEDED;
        List<String> notes = new ArrayList<>();
        try {
            scraper.scrape(ctx[0]);
        } catch (PageLimitReachedException e) {
            notes.add(e.getMessage());
        } catch (FetchException | IllegalStateException e) {
            log.warn("Run {} of job '{}' failed: {}", run.id(), job.name(), e.getMessage());
            notes.add(e.getMessage());
            status = ScrapeRun.Status.FAILED;
        } catch (RuntimeException e) {
            log.error("Run {} of job '{}' crashed", run.id(), job.name(), e);
            notes.add("Unexpected error: " + e);
            status = ScrapeRun.Status.FAILED;
        }
        try {
            flush(job, run, buffer, stats);
        } catch (RuntimeException e) {
            log.error("Could not save items of run {}", run.id(), e);
            notes.add("Could not save items: " + e.getMessage());
            status = ScrapeRun.Status.FAILED;
        }
        if (status == ScrapeRun.Status.FAILED && stats.found > 0) {
            status = ScrapeRun.Status.PARTIAL;
        }
        if (duplicates[0] > 0) {
            notes.add(duplicates[0] + " duplicate record(s) skipped");
        }
        runs.finish(run.id(), status, ctx[0].pagesFetched(), stats, ctx[0].retries(),
                notes.isEmpty() ? null : String.join("; ", notes));
        log.info("Run {} of job '{}' {}: {} pages, {} items ({} new, {} updated)", run.id(), job.name(), status,
                ctx[0].pagesFetched(), stats.found, stats.created, stats.updated);
    }

    private void flush(ScrapeJob job, ScrapeRun run, List<ScrapedItem> buffer, RunStats stats) {
        if (!buffer.isEmpty()) {
            stats.add(items.upsert(job.id(), run.id(), List.copyOf(buffer)));
            buffer.clear();
        }
    }
}
