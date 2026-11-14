package dev.scrapekit.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import dev.scrapekit.config.ScrapeKitProperties;
import dev.scrapekit.job.JobRepository;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.run.JobAlreadyRunningException;
import dev.scrapekit.run.RunService;
import dev.scrapekit.run.ScrapeRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/** Keeps one cron trigger per enabled job; re-registered whenever a job is created, changed or deleted. */
@Component
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final RunService runs;
    private final JobRepository jobs;
    private final ScrapeKitProperties.Scheduler cfg;
    private final ZoneId zone;
    private final Map<Long, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    public JobScheduler(ThreadPoolTaskScheduler jobTaskScheduler, @Lazy RunService runs, JobRepository jobs,
                        ScrapeKitProperties props) {
        this.taskScheduler = jobTaskScheduler;
        this.runs = runs;
        this.jobs = jobs;
        this.cfg = props.scheduler();
        this.zone = ZoneId.of(cfg.zone());
    }

    @EventListener(ApplicationReadyEvent.class)
    void registerAll() {
        if (!cfg.enabled()) {
            log.info("Scheduler disabled (scrapekit.scheduler.enabled=false)");
            return;
        }
        jobs.findAll().forEach(this::refresh);
        log.info("Scheduled {} job(s), zone {}", scheduled.size(), zone);
    }

    public void refresh(ScrapeJob job) {
        unschedule(job.id());
        if (!cfg.enabled() || !job.scheduled()) {
            return;
        }
        scheduled.put(job.id(), taskScheduler.schedule(() -> fire(job.id()), new CronTrigger(job.cron(), zone)));
    }

    public void unschedule(long jobId) {
        ScheduledFuture<?> f = scheduled.remove(jobId);
        if (f != null) {
            f.cancel(false);
        }
    }

    public boolean isScheduled(long jobId) {
        return scheduled.containsKey(jobId);
    }

    /** Next fire time of an enabled job, for the UI. */
    public Optional<ZonedDateTime> nextRun(ScrapeJob job) {
        if (!cfg.enabled() || !job.scheduled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CronExpression.parse(job.cron()).next(ZonedDateTime.ofInstant(Instant.now(), zone)));
    }

    public ZoneId zone() {
        return zone;
    }

    private void fire(long jobId) {
        try {
            runs.trigger(jobId, ScrapeRun.Trigger.SCHEDULE);
        } catch (JobAlreadyRunningException e) {
            log.info("Skipping scheduled run of job {}: previous run still in progress", jobId);
        } catch (RuntimeException e) {
            log.error("Scheduled run of job {} could not start", jobId, e);
        }
    }
}
