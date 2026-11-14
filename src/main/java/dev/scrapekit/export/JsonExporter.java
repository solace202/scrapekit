package dev.scrapekit.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scrapekit.item.StoredItem;
import dev.scrapekit.scrape.Column;

import org.springframework.stereotype.Component;

/** Streams a JSON array: {"key", ...fields in column order, "firstSeenAt", "lastSeenAt"}. */
@Component
public class JsonExporter {

    private final ObjectMapper json;

    public JsonExporter(ObjectMapper json) {
        this.json = json;
    }

    public void write(List<Column> columns, Consumer<Consumer<StoredItem>> source, OutputStream out) throws IOException {
        try (JsonGenerator g = json.getFactory().createGenerator(out)) {
            g.useDefaultPrettyPrinter();
            g.writeStartArray();
            try {
                source.accept(item -> {
                    try {
                        g.writeStartObject();
                        g.writeStringField("key", item.key());
                        for (Column c : columns) {
                            g.writeFieldName(c.key());
                            json.writeValue(g, item.fields().get(c.key()));
                        }
                        g.writeStringField("firstSeenAt", item.firstSeenAt().toString());
                        g.writeStringField("lastSeenAt", item.lastSeenAt().toString());
                        g.writeEndObject();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            g.writeEndArray();
        }
    }
}
