package dev.scrapekit.job;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import dev.scrapekit.scrape.Scraper;
import dev.scrapekit.scrape.ScraperRegistry;
import dev.scrapekit.schedule.JobScheduler;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    private final JobRepository jobs;
    private final ScraperRegistry scrapers;
    private final JobScheduler scheduler;

    public JobService(JobRepository jobs, ScraperRegistry scrapers, JobScheduler scheduler) {
        this.jobs = jobs;
        this.scrapers = scrapers;
        this.scheduler = scheduler;
    }

    public List<ScrapeJob> list() {
        return jobs.findAll();
    }

    public ScrapeJob get(long id) {
        return jobs.findById(id).orElseThrow(() -> new NotFoundException("Job " + id + " not found"));
    }

    public ScrapeJob create(JobRequest r) {
        Valid v = validate(r);
        try {
            ScrapeJob job = jobs.insert(r.name().strip(), v.scraper.id(), v.startUrl, v.maxPages, v.delayMs, v.cron, v.enabled);
            scheduler.refresh(job);
            return job;
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("A job named '" + r.name().strip() + "' already exists");
        }
    }

    public ScrapeJob update(long id, JobRequest r) {
        get(id);
        Valid v = validate(r);
        try {
            ScrapeJob job = jobs.update(id, r.name().strip(), v.scraper.id(), v.startUrl, v.maxPages, v.delayMs, v.cron, v.enabled);
            scheduler.refresh(job);
            return job;
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("A job named '" + r.name().strip() + "' already exists");
        }
    }

    public void delete(long id) {
        if (!jobs.delete(id)) {
            throw new NotFoundException("Job " + id + " not found");
        }
        scheduler.unschedule(id);
    }

    private record Valid(Scraper scraper, String startUrl, int maxPages, int delayMs, String cron, boolean enabled) {
    }

    private Valid validate(JobRequest r) {
        Scraper scraper = scrapers.find(r.source().strip().toLowerCase(Locale.ROOT)).orElseThrow(() ->
                new IllegalArgumentException("Unknown source '" + r.source() + "'. Available: "
                        + scrapers.all().stream().map(Scraper::id).toList()));
        String url = r.startUrl() == null || r.startUrl().isBlank() ? scraper.defaultStartUrl() : r.startUrl().strip();
        try {
            URI u = URI.create(url);
            if (!("http".equals(u.getScheme()) || "https".equals(u.getScheme())) || u.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("startUrl must be an absolute http(s) URL");
        }
        String cron = r.cron() == null || r.cron().isBlank() ? null : r.cron().strip();
        if (cron != null && !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("Invalid cron '" + cron
                    + "'. Use 6 fields: second minute hour day month weekday, e.g. '0 0 6 * * *'");
        }
        return new Valid(scraper, url, r.maxPages() == null ? 100 : r.maxPages(),
                r.delayMs() == null ? 500 : r.delayMs(), cron, r.enabled() == null || r.enabled());
    }
}
