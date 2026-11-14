package dev.scrapekit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI scrapeKitOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ScrapeKit API")
                .version("0.1.0")
                .description("""
                        Scheduled scraping jobs with retries, per-host rate limiting and dedup.

                        1. `GET /api/sources` lists the built-in scrapers.
                        2. `POST /api/jobs` creates a job (optional 6-field cron), `POST /api/jobs/{id}/runs` runs it now.
                        3. `GET /api/jobs/{id}/export?format=xlsx` downloads the data as Excel, CSV or JSON.""")
                .license(new License().name("MIT")));
    }
}
