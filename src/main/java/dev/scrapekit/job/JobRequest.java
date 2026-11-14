package dev.scrapekit.job;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobRequest(
        @NotBlank @Size(max = 120) @Schema(example = "Mystery books") String name,
        @NotBlank @Schema(example = "books", description = "One of GET /api/sources") String source,
        @Size(max = 2000) @Schema(example = "https://books.toscrape.com/catalogue/category/books/mystery_3/index.html",
                description = "Defaults to the source's start page") String startUrl,
        @Min(1) @Max(10000) @Schema(example = "20") Integer maxPages,
        @Min(0) @Max(60000) @Schema(example = "500", description = "Minimum gap between requests to the host") Integer delayMs,
        @Size(max = 120) @Schema(example = "0 0 6 * * *", description = "Spring 6-field cron; empty = manual only") String cron,
        Boolean enabled) {
}
