package dev.scrapekit.scrape.sources;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.scrapekit.http.FetchResult;
import dev.scrapekit.scrape.Column;
import dev.scrapekit.scrape.Keys;
import dev.scrapekit.scrape.ScrapeContext;
import dev.scrapekit.scrape.ScrapedItem;
import dev.scrapekit.scrape.Scraper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/** quotes.toscrape.com rendered as HTML, following the "Next" link. */
@Component
public class QuotesHtmlScraper implements Scraper {

    @Override
    public String id() {
        return "quotes";
    }

    @Override
    public String description() {
        return "Quotes (HTML pages with a Next link). Quote, author, tags, author page.";
    }

    @Override
    public String defaultStartUrl() {
        return "https://quotes.toscrape.com/";
    }

    @Override
    public List<Column> columns() {
        return QuoteColumns.COLUMNS;
    }

    @Override
    public void scrape(ScrapeContext ctx) {
        URI next = ctx.startUrl();
        while (next != null) {
            FetchResult page = ctx.fetch(next);
            Document doc = Jsoup.parse(page.body(), page.uri().toString());
            parse(doc).forEach(ctx::emit);
            Element a = doc.selectFirst("li.next a");
            next = a == null ? null : URI.create(a.absUrl("href"));
        }
    }

    static List<ScrapedItem> parse(Document doc) {
        List<ScrapedItem> out = new ArrayList<>();
        for (Element q : doc.select("div.quote")) {
            String text = QuoteColumns.cleanQuote(q.select("span.text").text());
            String author = q.select("small.author").text().strip();
            Element authorLink = q.selectFirst("a[href*=/author/]");
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("quote", text);
            f.put("author", author);
            f.put("tags", q.select("div.tags a.tag").eachText());
            f.put("authorUrl", authorLink == null ? null : authorLink.absUrl("href"));
            out.add(new ScrapedItem(Keys.hash(author, text), f));
        }
        return out;
    }
}
