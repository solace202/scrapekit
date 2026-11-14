package dev.scrapekit.scrape.sources;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import dev.scrapekit.scrape.ScrapedItem;
import dev.scrapekit.support.Fixtures;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parser tests against pages saved from books.toscrape.com. */
class BooksScraperTest {

    static final String CAT = "https://books.toscrape.com/catalogue/category/books/mystery_3/";

    Document page(String fixture, String url) {
        return Jsoup.parse(Fixtures.read(fixture), url);
    }

    @Test
    @DisplayName("Listing page: 20 products with typed price, 1-5 rating, stock and absolute URLs")
    void parsesListing() {
        var items = BooksScraper.parseListing(page("books-mystery-page1.html", CAT + "index.html"), "Mystery");

        assertThat(items).hasSize(20);
        ScrapedItem first = items.getFirst();
        assertThat(first.fields().get("title")).isEqualTo("Sharp Objects");
        assertThat(first.fields().get("price")).isEqualTo(new BigDecimal("47.82"));
        assertThat(first.fields().get("rating")).isEqualTo(4);
        assertThat(first.fields().get("inStock")).isEqualTo(true);
        assertThat(first.fields().get("category")).isEqualTo("Mystery");
        assertThat((String) first.fields().get("url")).isEqualTo("https://books.toscrape.com/catalogue/sharp-objects_997/index.html");
        assertThat((String) first.fields().get("imageUrl")).startsWith("https://books.toscrape.com/media/cache/");
        assertThat(items).extracting(ScrapedItem::key).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Pagination: page 1 links to page 2, the last page has no next link")
    void followsPagination() {
        assertThat(BooksScraper.nextPage(page("books-mystery-page1.html", CAT + "index.html")))
                .contains(CAT + "page-2.html");
        assertThat(BooksScraper.nextPage(page("books-mystery-page2.html", CAT + "page-2.html"))).isEmpty();
        assertThat(BooksScraper.parseListing(page("books-mystery-page2.html", CAT + "page-2.html"), "Mystery")).hasSize(12);
    }

    @Test
    @DisplayName("Home page: all 50 categories are discovered with absolute URLs")
    void discoversCategories() {
        var cats = BooksScraper.parseCategories(page("books-home.html", "https://books.toscrape.com/index.html"));
        assertThat(cats).hasSize(50);
        assertThat(cats.getFirst().name()).isEqualTo("Travel");
        assertThat(cats.getFirst().url()).isEqualTo("https://books.toscrape.com/catalogue/category/books/travel_2/index.html");
    }

    @Test
    @DisplayName("The same book gets the same dedup key from the home page and from its category page")
    void stableKeys() {
        var fromCategory = BooksScraper.parseListing(page("books-mystery-page1.html", CAT + "index.html"), "Mystery");
        assertThat(fromCategory.getFirst().key()).isEqualTo("catalogue/sharp-objects_997/index.html")
                .isEqualTo(BooksScraper.productKey("https://books.toscrape.com/catalogue/sharp-objects_997/index.html"));
        var home = BooksScraper.parseListing(page("books-home.html", "https://books.toscrape.com/index.html"), null);
        assertThat(home.getFirst().key()).isEqualTo("catalogue/a-light-in-the-attic_1000/index.html");
    }

    @Test
    @DisplayName("Price and rating parsing tolerate currency symbols and missing values")
    void priceAndRating() {
        assertThat(BooksScraper.parsePrice("Â£51.77")).isEqualTo(new BigDecimal("51.77"));
        assertThat(BooksScraper.parsePrice("")).isNull();
        assertThat(BooksScraper.parseRating(Jsoup.parse("<p class='star-rating Five'></p>").selectFirst("p"))).isEqualTo(5);
        assertThat(BooksScraper.parseRating(null)).isNull();
    }
}
