package dev.scrapekit.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import dev.scrapekit.item.StoredItem;
import dev.scrapekit.scrape.Column;

import org.springframework.stereotype.Component;

/** RFC 4180 CSV, UTF-8 with a BOM so Excel shows non-ASCII text correctly. */
@Component
public class CsvExporter {

    public void write(List<Column> columns, Consumer<Consumer<StoredItem>> source, OutputStream out) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write('\uFEFF');
        writeRow(w, columns.stream().map(Column::header).toList());
        try {
            source.accept(item -> {
                try {
                    writeRow(w, columns.stream().map(c -> Cells.safeText(item.fields().get(c.key()), c.type())).toList());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        w.flush();
    }

    static void writeRow(Writer w, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(escape(values.get(i)));
        }
        w.write("\r\n");
    }

    static String escape(String v) {
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
