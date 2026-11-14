package dev.scrapekit.scrape.sources;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.scrapekit.http.FetchResult;
import dev.scrapekit.scrape.Column;
import dev.scrapekit.scrape.ScrapeContext;
import dev.scrapekit.scrape.ScrapedItem;
import dev.scrapekit.scrape.Scraper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.springframework.stereotype.Component;

/**
 * books.toscrape.com, an e-commerce sandbox. From the home page it walks every category and follows
 * each category's "next" pagination; a category URL as start page crawls just that category.
 */
@Component
public class BooksScraper implements Scraper {

    private static final Map<String, Integer> RATINGS = Map.of("one", 1, "two", 2, "three", 3, "four", 4, "five", 5);

    @Override
    public String id() {
        return "books";
    }

    @Override
    public String description() {
        return "Product catalogue (HTML): every category, with pagination. Title, price, rating, stock.";
    }

    @Override
    public String defaultStartUrl() {
        return "https://books.toscrape.com/";
    }

    @Override
    public List<Column> columns() {
        return List.of(Column.text("title", "Title"), Column.text("category", "Category"),
                Column.number("price", "Price"), Column.text("currency", "Currency"),
                Column.integer("rating", "Rating (1-5)"), Column.bool("inStock", "In stock"),
                Column.url("url", "Product URL"), Column.url("imageUrl", "Image URL"));
    }

    @Override
    public void scrape(ScrapeContext ctx) {
        URI start = ctx.startUrl();
        if (start.getPath().contains("/category/books/")) {
            crawlCategory(ctx, start, null);
            return;
        }
        FetchResult home = ctx.fetch(start);
        Document doc = Jsoup.parse(home.body(), home.uri().toString());
        List<Category> categories = parseCategories(doc);
        if (categories.isEmpty()) {          // not a home page: treat it as a listing
            crawlListing(ctx, doc, null);
            return;
        }
        for (Category c : categories) {
            crawlCategory(ctx, URI.create(c.url()), c.name());
        }
    }

    private void crawlCategory(ScrapeContext ctx, URI first, String category) {
        URI next = first;
        while (next != null) {
            FetchResult page = ctx.fetch(next);
            Document doc = Jsoup.parse(page.body(), page.uri().toString());
            next = crawlListing(ctx, doc, category);
        }
    }

    private URI crawlListing(ScrapeContext ctx, Document doc, String category) {
        String cat = category != null ? category : pageHeader(doc);
        for (ScrapedItem item : parseListing(doc, cat)) {
            ctx.emit(item);
        }
        return nextPage(doc).map(URI::create).orElse(null);
    }

    record Category(String name, String url) {
    }

    static List<Category> parseCategories(Document doc) {
        List<Category> out = new ArrayList<>();
        for (Element a : doc.select("div.side_categories ul li ul li a")) {
            out.add(new Category(a.text().strip(), a.absUrl("href")));
        }
        return out;
    }

    static List<ScrapedItem> parseListing(Document doc, String category) {
        List<ScrapedItem> items = new ArrayList<>();
        for (Element pod : doc.select("article.product_pod")) {
            Element link = pod.selectFirst("h3 a");
            if (link == null) {
                continue;
            }
            String url = link.absUrl("href");
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("title", link.hasAttr("title") ? link.attr("title") : link.text());
            f.put("category", category);
            f.put("price", parsePrice(text(pod, "p.price_color")));
            f.put("currency", "GBP");
            f.put("rating", parseRating(pod.selectFirst("p.star-rating")));
            f.put("inStock", text(pod, "p.availability").toLowerCase().contains("in stock"));
            f.put("url", url);
            Element img = pod.selectFirst("div.image_container img");
            f.put("imageUrl", img == null ? null : img.absUrl("src"));
            items.add(new ScrapedItem(productKey(url), f));
        }
        return items;
    }

    static Optional<String> nextPage(Document doc) {
        Element a = doc.selectFirst("li.next a");
        return a == null ? Optional.empty() : Optional.of(a.absUrl("href"));
    }

    /** "catalogue/a-light-in-the-attic_1000/index.html": the same book has the same key on every page. */
    static String productKey(String url) {
        int i = url.indexOf("catalogue/");
        String path = i >= 0 ? url.substring(i) : URI.create(url).getPath();
        return path.replace("catalogue/category/books/", "catalogue/");
    }

    static BigDecimal parsePrice(String raw) {
        String digits = raw.replaceAll("[^0-9.]", "");
        return digits.isEmpty() ? null : new BigDecimal(digits);
    }

    static Integer parseRating(Element el) {
        if (el == null) {
            return null;
        }
        for (String cls : el.classNames()) {
            Integer r = RATINGS.get(cls.toLowerCase());
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static String pageHeader(Document doc) {
        Element h1 = doc.selectFirst("div.page-header h1");
        return h1 == null ? null : h1.text().strip();
    }

    private static String text(Element root, String css) {
        Element e = root.selectFirst(css);
        return e == null ? "" : e.text().strip();
    }
}
