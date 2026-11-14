package dev.scrapekit.scrape.sources;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scrapekit.http.FetchResult;
import dev.scrapekit.scrape.Column;
import dev.scrapekit.scrape.Keys;
import dev.scrapekit.scrape.ScrapeContext;
import dev.scrapekit.scrape.ScrapedItem;
import dev.scrapekit.scrape.Scraper;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** The JSON API behind quotes.toscrape.com: {@code ?page=N} until {@code has_next} is false. */
@Component
public class QuotesApiScraper implements Scraper {

    private final ObjectMapper json;

    public QuotesApiScraper(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String id() {
        return "quotes-api";
    }

    @Override
    public String description() {
        return "Quotes (JSON API, page-number pagination with has_next).";
    }

    @Override
    public String defaultStartUrl() {
        return "https://quotes.toscrape.com/api/quotes";
    }

    @Override
    public List<Column> columns() {
        return QuoteColumns.COLUMNS;
    }

    @Override
    public void scrape(ScrapeContext ctx) {
        int page = 1;
        boolean hasNext = true;
        while (hasNext) {
            URI uri = UriComponentsBuilder.fromUri(ctx.startUrl()).replaceQueryParam("page", page).build(true).toUri();
            FetchResult r = ctx.fetch(uri);
            Page p = parse(r.body(), ctx.startUrl());
            p.items().forEach(ctx::emit);
            hasNext = p.hasNext() && !p.items().isEmpty();
            page++;
        }
    }

    record Page(List<ScrapedItem> items, boolean hasNext) {
    }

    Page parse(String body, URI base) {
        try {
            JsonNode root = json.readTree(body);
            List<ScrapedItem> items = new ArrayList<>();
            for (JsonNode q : root.path("quotes")) {
                String text = QuoteColumns.cleanQuote(q.path("text").asText());
                String author = q.path("author").path("name").asText();
                List<String> tags = new ArrayList<>();
                q.path("tags").forEach(t -> tags.add(t.asText()));
                String slug = q.path("author").path("slug").asText("");
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("quote", text);
                f.put("author", author);
                f.put("tags", tags);
                f.put("authorUrl", slug.isEmpty() ? null : base.resolve("/author/" + slug).toString());
                items.add(new ScrapedItem(Keys.hash(author, text), f));
            }
            return new Page(items, root.path("has_next").asBoolean(false));
        } catch (Exception e) {
            throw new IllegalStateException("Response is not the expected JSON: " + e.getMessage(), e);
        }
    }
}
