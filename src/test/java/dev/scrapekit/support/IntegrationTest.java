package dev.scrapekit.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Full application against PostgreSQL 16 (Testcontainers) and a WireMock "website". */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "scrapekit.scheduler.enabled=false",
        "scrapekit.http.max-attempts=3",
        "scrapekit.http.initial-backoff=10ms",
        "scrapekit.http.max-backoff=50ms",
        "scrapekit.http.default-delay=0ms"})
@Import(TestcontainersConfig.class)
public @interface IntegrationTest {
}
