package dev.scrapekit.export;

import java.util.Locale;

import org.springframework.http.MediaType;

public enum ExportFormat {
    CSV("csv", MediaType.parseMediaType("text/csv; charset=UTF-8")),
    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
    JSON("json", MediaType.APPLICATION_JSON);

    private final String extension;
    private final MediaType mediaType;

    ExportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public static ExportFormat parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown format '" + s + "'. Use csv, xlsx or json");
        }
    }
}
