package dev.scrapekit.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import dev.scrapekit.job.JobRequest;
import dev.scrapekit.job.JobService;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.schedule.JobScheduler;
import dev.scrapekit.support.Await;
import dev.scrapekit.support.FakeSite;
import dev.scrapekit.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "scrapekit.scheduler.enabled=true")
class SchedulerIT {

    @Autowired
    JobService jobs;
    @Autowired
    RunRepository runs;
    @Autowired
    JobScheduler scheduler;

    @Test
    @DisplayName("Cron: a job scheduled every 2 seconds runs by itself (trigger SCHEDULE); disabling it unschedules it")
    void cronRuns() {
        ScrapeJob job = jobs.create(new JobRequest("cron-" + System.nanoTime(), "books", FakeSite.newBookSite(), 100, 0,
                "*/2 * * * * *", true));
        assertThat(scheduler.isScheduled(job.id())).isTrue();
        assertThat(scheduler.nextRun(job)).isPresent();

        var run = Await.until(() -> runs.findByJob(job.id(), 5),
                l -> l.stream().anyMatch(r -> r.status() == ScrapeRun.Status.SUCCEEDED), Duration.ofSeconds(15));
        assertThat(run).allMatch(r -> r.trigger() == ScrapeRun.Trigger.SCHEDULE);

        jobs.update(job.id(), new JobRequest(job.name(), "books", job.startUrl(), 100, 0, "*/2 * * * * *", false));
        assertThat(scheduler.isScheduled(job.id())).isFalse();
    }
}
