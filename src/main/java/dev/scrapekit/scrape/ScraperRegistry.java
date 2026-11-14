package dev.scrapekit.scrape;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class ScraperRegistry {

    private final Map<String, Scraper> byId = new LinkedHashMap<>();

    public ScraperRegistry(List<Scraper> scrapers) {
        scrapers.forEach(s -> byId.put(s.id(), s));
    }

    public Optional<Scraper> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Scraper get(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown source: " + id));
    }

    public Collection<Scraper> all() {
        return byId.values();
    }
}
