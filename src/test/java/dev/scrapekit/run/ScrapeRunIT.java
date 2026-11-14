package dev.scrapekit.run;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import dev.scrapekit.item.ItemStore;
import dev.scrapekit.job.JobRequest;
import dev.scrapekit.job.JobService;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.support.Await;
import dev.scrapekit.support.FakeSite;
import dev.scrapekit.support.FakeSite.Book;
import dev.scrapekit.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ScrapeRunIT {

    @Autowired
    JobService jobs;
    @Autowired
    RunService runs;
    @Autowired
    ItemStore items;

    static int seq;

    ScrapeJob job(String source, String url, int maxPages) {
        return jobs.create(new JobRequest("job-" + (++seq) + "-" + System.nanoTime(), source, url, maxPages, 0, null, true));
    }

    ScrapeRun runToEnd(ScrapeJob job) {
        ScrapeRun started = runs.trigger(job.id(), ScrapeRun.Trigger.API);
        return Await.until(() -> runs.get(started.id()), r -> r.status() != ScrapeRun.Status.RUNNING, Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("Crawls every category and follows pagination: 4 pages, 7 books, all new")
    void fullCrawl() {
        ScrapeJob job = job("books", FakeSite.newBookSite(), 100);
        ScrapeRun run = runToEnd(job);

        assertThat(run.status()).isEqualTo(ScrapeRun.Status.SUCCEEDED);
        assertThat(run.pagesFetched()).isEqualTo(4);
        assertThat(run.itemsFound()).isEqualTo(7);
        assertThat(run.itemsNew()).isEqualTo(7);
        assertThat(items.count(job.id())).isEqualTo(7);
        var first = items.page(job.id(), 0, 1).getFirst();
        assertThat(first.fields()).containsEntry("title", "It's Only the Himalayas").containsEntry("category", "Travel")
                .containsEntry("rating", 2).containsEntry("inStock", true);
        assertThat(new BigDecimal(first.fields().get("price").toString())).isEqualByComparingTo("45.17");
    }

    @Test
    @DisplayName("Dedup: a second run stores nothing new and marks every record unchanged")
    void rerunIsIdempotent() {
        ScrapeJob job = job("books", FakeSite.newBookSite(), 100);
        runToEnd(job);
        ScrapeRun second = runToEnd(job);

        assertThat(second.itemsNew()).isZero();
        assertThat(second.itemsUnchanged()).isEqualTo(7);
        assertThat(items.count(job.id())).isEqualTo(7);
    }

    @Test
    @DisplayName("Change detection: a price change on the site is one UPDATED record")
    void detectsChanges() {
        String home = FakeSite.newBookSite();
        ScrapeJob job = job("books", home, 100);
        runToEnd(job);
        List<Book> repriced = List.of(new Book("a-light-in-the-attic_1000", "A Light in the Attic", "39.99", "Three", true),
                FakeSite.POETRY.get(1));
        FakeSite.page(FakeSite.prefixOf(home) + "/catalogue/category/books/poetry_23/index.html",
                FakeSite.listing("Poetry", repriced, null));

        ScrapeRun second = runToEnd(job);

        assertThat(second.itemsUpdated()).isEqualTo(1);
        assertThat(second.itemsUnchanged()).isEqualTo(6);
        var attic = items.page(job.id(), 0, 10).stream().filter(i -> i.key().contains("attic")).findFirst().orElseThrow();
        assertThat(attic.fields().get("price").toString()).isEqualTo("39.99");
    }

    @Test
    @DisplayName("Page budget: the run stops cleanly at maxPages and keeps what it found")
    void pageLimit() {
        ScrapeJob job = job("books", FakeSite.newBookSite(), 2);
        ScrapeRun run = runToEnd(job);

        assertThat(run.status()).isEqualTo(ScrapeRun.Status.SUCCEEDED);
        assertThat(run.pagesFetched()).isEqualTo(2);
        assertThat(run.itemsFound()).isEqualTo(3);
        assertThat(run.message()).contains("page limit");
    }

    @Test
    @DisplayName("Retries: two 503 responses, then success; the run succeeds and reports 2 retries")
    void retriesTransientErrors() {
        String home = FakeSite.newBookSite();
        String path = FakeSite.prefixOf(home) + "/catalogue/category/books/poetry_23/index.html";
        String html = FakeSite.listing("Poetry", FakeSite.POETRY, null);
        var wm = FakeSite.WM;
        wm.stubFor(get(urlPathEqualTo(path)).inScenario(path).whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("one"));
        wm.stubFor(get(urlPathEqualTo(path)).inScenario(path).whenScenarioStateIs("one")
                .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "0")).willSetStateTo("two"));
        wm.stubFor(get(urlPathEqualTo(path)).inScenario(path).whenScenarioStateIs("two")
                .willReturn(aResponse().withStatus(200).withBody(html)));

        ScrapeRun run = runToEnd(job("books", home, 100));

        assertThat(run.status()).isEqualTo(ScrapeRun.Status.SUCCEEDED);
        assertThat(run.retries()).isEqualTo(2);
        assertThat(run.itemsFound()).isEqualTo(7);
        wm.verify(3, getRequestedFor(urlPathEqualTo(path)));
    }

    @Test
    @DisplayName("A broken page after some progress gives PARTIAL: earlier records are kept, the error is recorded")
    void partialFailure() {
        String home = FakeSite.newBookSite();
        String path = FakeSite.prefixOf(home) + "/catalogue/category/books/poetry_23/index.html";
        FakeSite.WM.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(404)));

        ScrapeJob job = job("books", home, 100);
        ScrapeRun run = runToEnd(job);

        assertThat(run.status()).isEqualTo(ScrapeRun.Status.PARTIAL);
        assertThat(run.message()).contains("HTTP 404");
        assertThat(items.count(job.id())).isEqualTo(5);
    }

    @Test
    @DisplayName("Retries are bounded: a page that keeps failing ends the run as FAILED")
    void givesUp() {
        String path = "/down-" + System.nanoTime() + "/index.html";
        FakeSite.WM.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(500)));

        ScrapeRun run = runToEnd(job("books", FakeSite.url(path), 100));

        assertThat(run.status()).isEqualTo(ScrapeRun.Status.FAILED);
        assertThat(run.message()).contains("after 3 attempts");
        FakeSite.WM.verify(3, getRequestedFor(urlPathEqualTo(path)));
    }

    @Test
    @DisplayName("robots.txt is respected: a disallowed URL is never requested")
    void robots() {
        ScrapeRun run = runToEnd(job("quotes", FakeSite.url("/private/quotes"), 10));

        assertThat(run.status()).isEqualTo(ScrapeRun.Status.FAILED);
        assertThat(run.message()).contains("robots.txt");
        FakeSite.WM.verify(0, getRequestedFor(urlPathEqualTo("/private/quotes")));
    }

    @Test
    @DisplayName("Only one run per job at a time: a second start while running is rejected")
    void oneRunAtATime() {
        String path = "/slow-" + System.nanoTime() + "/index.html";
        FakeSite.WM.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)
                .withFixedDelay(1500).withBody(FakeSite.listing("Slow", FakeSite.POETRY, null))));
        ScrapeJob job = job("books", FakeSite.url(path), 10);

        ScrapeRun first = runs.trigger(job.id(), ScrapeRun.Trigger.API);
        assertThatThrownBy(() -> runs.trigger(job.id(), ScrapeRun.Trigger.API))
                .isInstanceOf(JobAlreadyRunningException.class);
        Await.until(() -> runs.get(first.id()), r -> r.status() == ScrapeRun.Status.SUCCEEDED, Duration.ofSeconds(20));
        assertThat(runToEnd(job).status()).isEqualTo(ScrapeRun.Status.SUCCEEDED);
    }

    @Test
    @DisplayName("JSON API source: follows ?page=N until has_next is false")
    void jsonApi() {
        String api = "/api-" + System.nanoTime() + "/quotes";
        FakeSite.WM.stubFor(get(urlPathEqualTo(api)).withQueryParam("page", com.github.tomakehurst.wiremock.client.WireMock.equalTo("1"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"has_next": true, "page": 1, "quotes": [
                          {"author": {"name": "Albert Einstein", "slug": "Albert-Einstein"}, "tags": ["life"], "text": "“Life is like riding a bicycle.”"},
                          {"author": {"name": "Jane Austen", "slug": "Jane-Austen"}, "tags": [], "text": "“A lady's imagination is very rapid.”"}]}""")));
        FakeSite.WM.stubFor(get(urlPathEqualTo(api)).withQueryParam("page", com.github.tomakehurst.wiremock.client.WireMock.equalTo("2"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"has_next": false, "page": 2, "quotes": [
                          {"author": {"name": "Mark Twain", "slug": "Mark-Twain"}, "tags": ["humor"], "text": "“Get your facts first.”"}]}""")));

        ScrapeJob job = job("quotes-api", FakeSite.url(api), 10);
        ScrapeRun run = runToEnd(job);

        assertThat(run.status()).isEqualTo(ScrapeRun.Status.SUCCEEDED);
        assertThat(run.pagesFetched()).isEqualTo(2);
        assertThat(run.itemsNew()).isEqualTo(3);
        assertThat(items.page(job.id(), 0, 1).getFirst().fields()).containsEntry("quote", "Life is like riding a bicycle.");
    }
}
