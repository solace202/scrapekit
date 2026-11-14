package dev.scrapekit.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.scrapekit.item.StoredItem;
import dev.scrapekit.scrape.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsvExporterTest {

    final List<Column> cols = List.of(Column.text("title", "Title"), Column.number("price", "Price"),
            Column.list("tags", "Tags"));

    String export(Map<String, Object> fields) throws Exception {
        var out = new ByteArrayOutputStream();
        new CsvExporter().write(cols, c -> c.accept(new StoredItem("k", fields, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)), out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("UTF-8 BOM, header row, CRLF line endings, lists joined with commas")
    void basics() throws Exception {
        String csv = export(new LinkedHashMap<>(Map.of("title", "Café", "price", new BigDecimal("9.50"), "tags", List.of("a", "b"))));
        assertThat(csv).startsWith("\uFEFFTitle,Price,Tags\r\n");
        assertThat(csv).endsWith("Café,9.50,\"a, b\"\r\n");
    }

    @Test
    @DisplayName("Commas, quotes and line breaks are quoted per RFC 4180")
    void quoting() {
        assertThat(CsvExporter.escape("say \"hi\", ok")).isEqualTo("\"say \"\"hi\"\", ok\"");
        assertThat(CsvExporter.escape("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(CsvExporter.escape("plain")).isEqualTo("plain");
    }

    @Test
    @DisplayName("Scraped text that looks like a formula is neutralised; negative numbers are not")
    void formulaInjection() throws Exception {
        String csv = export(Map.of("title", "=HYPERLINK(\"http://evil\")", "price", -3));
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\",-3,");
    }
}
