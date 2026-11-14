package dev.scrapekit.export;

import java.util.Collection;
import java.util.stream.Collectors;

import dev.scrapekit.scrape.Column;

/** Shared value formatting for the tabular exports. */
final class Cells {

    private Cells() {
    }

    static String text(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return String.valueOf(v);
    }

    /**
     * Neutralises spreadsheet formula injection (a scraped "=HYPERLINK(...)" must not execute when the
     * CSV is opened in Excel) by prefixing text that starts with a formula character with an apostrophe.
     */
    static String safeText(Object v, Column.Type type) {
        String s = text(v);
        if (type == Column.Type.NUMBER || type == Column.Type.INTEGER || type == Column.Type.BOOLEAN || s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        return (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') ? "'" + s : s;
    }
}
