package dev.scrapekit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import dev.scrapekit.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@IntegrationTest
class JobApiIT {

    @Autowired
    TestRestTemplate rest;

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("GET /api/sources lists the three built-in scrapers with their columns")
    void sources() {
        List body = rest.getForObject("/api/sources", List.class);
        assertThat(body).extracting(s -> ((Map) s).get("id")).containsExactlyInAnyOrder("books", "quotes", "quotes-api");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Demo jobs are seeded by Flyway; create, read, update and delete a job")
    void crud() {
        assertThat(rest.getForObject("/api/jobs", List.class)).hasSizeGreaterThanOrEqualTo(3);

        var created = rest.postForEntity("/api/jobs", Map.of("name", "Mystery books", "source", "books",
                "startUrl", "https://books.toscrape.com/catalogue/category/books/mystery_3/index.html",
                "cron", "0 0 7 * * MON-FRI"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number id = (Number) created.getBody().get("id");
        assertThat(created.getBody()).containsEntry("maxPages", 100).containsEntry("delayMs", 500).containsEntry("enabled", true);

        var updated = rest.exchange("/api/jobs/" + id, HttpMethod.PUT, new HttpEntity<>(Map.of("name", "Mystery books",
                "source", "books", "maxPages", 5, "cron", "")), Map.class);
        assertThat(updated.getBody()).containsEntry("maxPages", 5).containsEntry("startUrl", "https://books.toscrape.com/");
        assertThat(updated.getBody().get("cron")).isNull();

        rest.delete("/api/jobs/" + id);
        assertThat(rest.getForEntity("/api/jobs/" + id, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Invalid cron, unknown source, bad URL and duplicate names are 400 problems with a clear message")
    void validation() {
        var cron = rest.postForEntity("/api/jobs", Map.of("name", "x1", "source", "books", "cron", "0 6 * * *"), Map.class);
        assertThat(cron.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) cron.getBody().get("detail")).contains("6 fields");

        var source = rest.postForEntity("/api/jobs", Map.of("name", "x2", "source", "amazon"), Map.class);
        assertThat((String) source.getBody().get("detail")).contains("Unknown source");

        var url = rest.postForEntity("/api/jobs", Map.of("name", "x3", "source", "books", "startUrl", "file:///etc/passwd"), Map.class);
        assertThat(url.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var dup = rest.postForEntity("/api/jobs", Map.of("name", "Quotes (HTML pages)", "source", "quotes"), Map.class);
        assertThat((String) dup.getBody().get("detail")).contains("already exists");

        var blank = rest.postForEntity("/api/jobs", Map.of("source", "quotes", "maxPages", 0), Map.class);
        assertThat((String) blank.getBody().get("detail")).contains("maxPages").contains("name");
    }

    @Test
    @DisplayName("Unknown job or run: 404 problem response")
    void notFound() {
        assertThat(rest.postForEntity("/api/jobs/999999/runs", null, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/runs/999999", Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Dashboard and Swagger UI render")
    void ui() {
        assertThat(rest.getForObject("/", String.class)).contains("Books catalogue (all categories)", "Run now", "Excel");
        assertThat(rest.getForObject("/jobs/1", String.class)).contains("Run history", "Data preview", "robots.txt respected");
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getBody()).contains("/api/jobs/{id}/export");
    }
}
