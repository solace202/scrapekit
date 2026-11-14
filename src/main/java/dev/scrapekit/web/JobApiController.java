package dev.scrapekit.web;

import java.util.List;

import dev.scrapekit.item.ItemStore;
import dev.scrapekit.item.StoredItem;
import dev.scrapekit.job.JobRequest;
import dev.scrapekit.job.JobService;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.run.RunRepository;
import dev.scrapekit.run.RunService;
import dev.scrapekit.run.ScrapeRun;
import dev.scrapekit.scrape.Column;
import dev.scrapekit.scrape.ScraperRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Jobs and runs")
public class JobApiController {

    private final JobService jobs;
    private final RunService runService;
    private final RunRepository runs;
    private final ItemStore items;
    private final ScraperRegistry scrapers;

    public JobApiController(JobService jobs, RunService runService, RunRepository runs, ItemStore items,
                            ScraperRegistry scrapers) {
        this.jobs = jobs;
        this.runService = runService;
        this.runs = runs;
        this.items = items;
        this.scrapers = scrapers;
    }

    public record SourceDto(String id, String description, String defaultStartUrl, List<Column> columns) {
    }

    @GetMapping("/sources")
    @Operation(summary = "Built-in scrapers and the columns they produce")
    public List<SourceDto> sources() {
        return scrapers.all().stream()
                .map(s -> new SourceDto(s.id(), s.description(), s.defaultStartUrl(), s.columns())).toList();
    }

    @GetMapping("/jobs")
    @Operation(summary = "List jobs")
    public List<ScrapeJob> list() {
        return jobs.list();
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a job", description = "cron uses 6 fields (second minute hour day month weekday). "
            + "Leave it empty for a manual-only job.")
    public ScrapeJob create(@Valid @RequestBody JobRequest request) {
        return jobs.create(request);
    }

    @GetMapping("/jobs/{id}")
    public ScrapeJob get(@PathVariable long id) {
        return jobs.get(id);
    }

    @PutMapping("/jobs/{id}")
    @Operation(summary = "Replace a job (re-registers its schedule)")
    public ScrapeJob update(@PathVariable long id, @Valid @RequestBody JobRequest request) {
        return jobs.update(id, request);
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a job with its runs and data")
    public void delete(@PathVariable long id) {
        jobs.delete(id);
    }

    @PostMapping("/jobs/{id}/runs")
    @Operation(summary = "Run a job now", description = "Returns 202 with the RUNNING run; poll GET /api/runs/{id}. "
            + "409 if the job is already running.")
    public ResponseEntity<ScrapeRun> run(@PathVariable long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(runService.trigger(id, ScrapeRun.Trigger.API));
    }

    @GetMapping("/jobs/{id}/runs")
    @Operation(summary = "Run history of a job, newest first")
    public List<ScrapeRun> history(@PathVariable long id, @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        jobs.get(id);
        return runs.findByJob(id, limit);
    }

    @GetMapping("/runs/{runId}")
    public ScrapeRun runStatus(@PathVariable long runId) {
        return runService.get(runId);
    }

    public record ItemPage(long total, int offset, int limit, List<StoredItem> items) {
    }

    @GetMapping("/jobs/{id}/items")
    @Operation(summary = "Browse the job's deduplicated dataset")
    public ItemPage items(@PathVariable long id, @RequestParam(defaultValue = "0") @Min(0) int offset,
                          @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        jobs.get(id);
        return new ItemPage(items.count(id), offset, limit, items.page(id, offset, limit));
    }
}
