package dev.scrapekit.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import dev.scrapekit.item.ItemStore;
import dev.scrapekit.job.JobService;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.run.JobAlreadyRunningException;
import dev.scrapekit.run.RunRepository;
import dev.scrapekit.run.RunService;
import dev.scrapekit.run.ScrapeRun;
import dev.scrapekit.schedule.JobScheduler;
import dev.scrapekit.scrape.ScraperRegistry;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Small server-rendered dashboard: jobs, run history, data preview, run-now and download buttons. */
@Controller
public class UiController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss");

    private final JobService jobs;
    private final RunService runService;
    private final RunRepository runs;
    private final ItemStore items;
    private final JobScheduler scheduler;
    private final ScraperRegistry scrapers;

    public UiController(JobService jobs, RunService runService, RunRepository runs, ItemStore items,
                        JobScheduler scheduler, ScraperRegistry scrapers) {
        this.jobs = jobs;
        this.runService = runService;
        this.runs = runs;
        this.items = items;
        this.scheduler = scheduler;
        this.scrapers = scrapers;
    }

    public record JobRow(ScrapeJob job, String next, ScrapeRun last, long items) {
    }

    @GetMapping("/")
    public String index(Model model) {
        List<JobRow> rows = jobs.list().stream().map(j -> new JobRow(j,
                scheduler.nextRun(j).map(t -> TIME.format(t)).orElse(null),
                runs.lastOf(j.id()).orElse(null), items.count(j.id()))).toList();
        List<ScrapeRun> recent = runs.recent(12);
        model.addAttribute("rows", rows);
        model.addAttribute("recent", recent);
        model.addAttribute("jobNames", rows.stream().collect(java.util.stream.Collectors.toMap(r -> r.job().id(), r -> r.job().name())));
        model.addAttribute("totalItems", items.countAll());
        model.addAttribute("fmt", new Fmt(scheduler.zone()));
        return "index";
    }

    @GetMapping("/jobs/{id}")
    public String job(@PathVariable long id, Model model) {
        ScrapeJob job = jobs.get(id);
        model.addAttribute("job", job);
        model.addAttribute("next", scheduler.nextRun(job).map(TIME::format).orElse(null));
        model.addAttribute("runs", runs.findByJob(id, 25));
        model.addAttribute("columns", scrapers.get(job.source()).columns().stream()
                .filter(c -> !c.key().equals("imageUrl")).toList());
        model.addAttribute("items", items.page(id, 0, 15));
        model.addAttribute("total", items.count(id));
        model.addAttribute("fmt", new Fmt(scheduler.zone()));
        return "job";
    }

    @PostMapping("/jobs/{id}/run")
    public String runNow(@PathVariable long id, RedirectAttributes flash) {
        try {
            ScrapeRun r = runService.trigger(id, ScrapeRun.Trigger.MANUAL);
            flash.addFlashAttribute("notice", "Run #" + r.id() + " started");
        } catch (JobAlreadyRunningException e) {
            flash.addFlashAttribute("notice", e.getMessage());
        }
        return "redirect:/jobs/" + id;
    }

    /** View helpers for Thymeleaf. */
    public record Fmt(ZoneId zone) {

        public String time(Instant t) {
            return t == null ? "" : TIME.format(t.atZone(zone));
        }

        public String duration(ScrapeRun r) {
            Duration d = r.finishedAt() == null ? Duration.between(r.startedAt(), Instant.now()) : r.duration();
            long s = d.toSeconds();
            return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : (d.toMillis() < 1000 ? d.toMillis() + " ms" : s + "s");
        }

        public String cell(Object v, dev.scrapekit.scrape.Column col) {
            if (v instanceof Number n && col.type() == dev.scrapekit.scrape.Column.Type.NUMBER) {
                return String.format(java.util.Locale.ROOT, "%,.2f", n.doubleValue());
            }
            if (v instanceof Boolean b) {
                return b ? "Yes" : "No";
            }
            if (v instanceof java.util.Collection<?> c) {
                return String.join(", ", c.stream().map(String::valueOf).toList());
            }
            return v == null ? "" : String.valueOf(v);
        }

        public String badge(ScrapeRun.Status s) {
            return Map.of(ScrapeRun.Status.SUCCEEDED, "ok", ScrapeRun.Status.RUNNING, "run",
                    ScrapeRun.Status.PARTIAL, "warn", ScrapeRun.Status.FAILED, "err").get(s);
        }
    }
}
