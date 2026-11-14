package dev.scrapekit.scrape.sources;

import java.util.List;

import dev.scrapekit.scrape.Column;

final class QuoteColumns {

    static final List<Column> COLUMNS = List.of(Column.text("quote", "Quote"), Column.text("author", "Author"),
            Column.list("tags", "Tags"), Column.url("authorUrl", "Author URL"));

    private QuoteColumns() {
    }

    /** Strips the curly quotes the site wraps every quote in. */
    static String cleanQuote(String s) {
        return s.strip().replaceAll("^[“\"]+|[”\"]+$", "").strip();
    }
}
