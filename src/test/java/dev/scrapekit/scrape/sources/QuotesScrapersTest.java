package dev.scrapekit.scrape.sources;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scrapekit.support.Fixtures;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuotesScrapersTest {

    final QuotesApiScraper api = new QuotesApiScraper(new ObjectMapper());
    final URI apiBase = URI.create("https://quotes.toscrape.com/api/quotes");

    @Test
    @DisplayName("HTML page: 10 quotes with clean text (no curly quotes), author, tags and author URL")
    void parsesHtml() {
        var items = QuotesHtmlScraper.parse(Jsoup.parse(Fixtures.read("quotes-page1.html"), "https://quotes.toscrape.com/"));
        assertThat(items).hasSize(10);
        var f = items.getFirst().fields();
        assertThat((String) f.get("quote")).startsWith("The world as we have created it").doesNotContain("“", "”");
        assertThat(f.get("author")).isEqualTo("Albert Einstein");
        assertThat(f.get("tags")).isEqualTo(List.of("change", "deep-thoughts", "thinking", "world"));
        assertThat(f.get("authorUrl")).isEqualTo("https://quotes.toscrape.com/author/Albert-Einstein");
    }

    @Test
    @DisplayName("JSON API: items and has_next are read; the last page stops pagination")
    void parsesApi() {
        var first = api.parse(Fixtures.read("quotes-api-page1.json"), apiBase);
        assertThat(first.items()).hasSize(10);
        assertThat(first.hasNext()).isTrue();
        var last = api.parse(Fixtures.read("quotes-api-page10.json"), apiBase);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.items().getFirst().fields().get("author")).isEqualTo("J.K. Rowling");
    }

    @Test
    @DisplayName("HTML and JSON scrapers produce identical records and keys for the same quote")
    void sameRecordFromBothSources() {
        var html = QuotesHtmlScraper.parse(Jsoup.parse(Fixtures.read("quotes-page1.html"), "https://quotes.toscrape.com/"));
        var json = api.parse(Fixtures.read("quotes-api-page1.json"), apiBase).items();
        assertThat(json.getFirst().key()).isEqualTo(html.getFirst().key());
        assertThat(json.getFirst().fields()).isEqualTo(html.getFirst().fields());
    }

    @Test
    @DisplayName("A non-JSON response fails the run with a clear message")
    void rejectsHtmlFromApi() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> api.parse("<html>maintenance</html>", apiBase))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not the expected JSON");
    }
}
