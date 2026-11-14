package dev.scrapekit.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * A tiny books.toscrape.com look-alike served by WireMock. Every test gets its own path prefix,
 * so tests can change pages without affecting each other.
 */
public final class FakeSite {

    public static final WireMockServer WM = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final AtomicInteger SEQ = new AtomicInteger();

    static {
        WM.start();
        WM.stubFor(get(urlEqualTo("/robots.txt")).willReturn(aResponse().withStatus(200)
                .withBody("User-agent: *\nDisallow: /private/\n")));
    }

    public record Book(String slug, String title, String price, String rating, boolean inStock) {
    }

    public static final List<Book> TRAVEL_1 = List.of(
            new Book("its-only-the-himalayas_981", "It's Only the Himalayas", "45.17", "Two", true),
            new Book("full-moon-over-noahs-ark_980", "Full Moon over Noah’s Ark", "49.43", "Four", true),
            new Book("see-america_979", "See America: A Celebration", "48.87", "Three", false));
    public static final List<Book> TRAVEL_2 = List.of(
            new Book("vagabonding_978", "Vagabonding", "36.94", "Two", true),
            new Book("under-the-tuscan-sun_977", "Under the Tuscan Sun", "37.33", "Three", true));
    public static final List<Book> POETRY = List.of(
            new Book("a-light-in-the-attic_1000", "A Light in the Attic", "51.77", "Three", true),
            new Book("olio_984", "Olio", "23.88", "One", true));

    private FakeSite() {
    }

    /** Registers a fresh 2-category site and returns its home page URL. */
    public static String newBookSite() {
        String p = "/s" + SEQ.incrementAndGet();
        page(p + "/index.html", home());
        page(p + "/catalogue/category/books/travel_2/index.html", listing("Travel", TRAVEL_1, "page-2.html"));
        page(p + "/catalogue/category/books/travel_2/page-2.html", listing("Travel", TRAVEL_2, null));
        page(p + "/catalogue/category/books/poetry_23/index.html", listing("Poetry", POETRY, null));
        return url(p + "/index.html");
    }

    public static String prefixOf(String homeUrl) {
        return homeUrl.substring(base().length()).replace("/index.html", "");
    }

    public static void page(String path, String html) {
        WM.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8").withBody(html)));
    }

    public static String base() {
        return "http://localhost:" + WM.port();
    }

    public static String url(String path) {
        return base() + path;
    }

    static String home() {
        return """
                <html><body><div class="side_categories"><ul><li><a href="catalogue/category/books_1/index.html">Books</a>
                <ul>
                  <li><a href="catalogue/category/books/travel_2/index.html"> Travel </a></li>
                  <li><a href="catalogue/category/books/poetry_23/index.html"> Poetry </a></li>
                </ul></li></ul></div></body></html>""";
    }

    public static String listing(String category, List<Book> books, String next) {
        StringBuilder sb = new StringBuilder("<html><body><div class=\"page-header action\"><h1>" + category + "</h1></div><ol>");
        for (Book b : books) {
            sb.append("""
                    <li><article class="product_pod">
                      <div class="image_container"><a href="../../../%1$s/index.html"><img src="../../../../media/cache/%1$s.jpg" alt="x"></a></div>
                      <p class="star-rating %4$s"></p>
                      <h3><a href="../../../%1$s/index.html" title="%2$s">%2$s</a></h3>
                      <div class="product_price"><p class="price_color">£%3$s</p>
                      <p class="instock availability">%5$s</p></div>
                    </article></li>""".formatted(b.slug(), b.title(), b.price(), b.rating(),
                    b.inStock() ? "In stock" : "Out of stock"));
        }
        sb.append("</ol>");
        if (next != null) {
            sb.append("<ul class=\"pager\"><li class=\"next\"><a href=\"").append(next).append("\">next</a></li></ul>");
        }
        return sb.append("</body></html>").toString();
    }
}
