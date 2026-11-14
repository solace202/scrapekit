package dev.scrapekit.export;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.google.auth.oauth2.GoogleCredentials;
import dev.scrapekit.config.ScrapeKitProperties;
import dev.scrapekit.item.StoredItem;
import dev.scrapekit.scrape.Column;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Optional: replaces the content of one sheet tab with the job's dataset through the Sheets API v4.
 * Needs a Google Cloud service-account key ({@code SHEETS_CREDENTIALS_FILE}) and a spreadsheet shared
 * with that service account's e-mail. Without a key the endpoint answers 503 with setup instructions.
 */
@Component
public class SheetsExporter {

    static final int MAX_ROWS = 50_000;
    private static final String SCOPE = "https://www.googleapis.com/auth/spreadsheets";

    private final ScrapeKitProperties.Sheets cfg;
    private final RestClient http;
    private Supplier<String> tokens;

    public SheetsExporter(ScrapeKitProperties props, RestClient.Builder builder) {
        this.cfg = props.sheets();
        // HttpURLConnection: plain HTTP/1.1, no h2c upgrade attempts
        this.http = builder.baseUrl(cfg.apiBaseUrl()).requestFactory(new SimpleClientHttpRequestFactory()).build();
    }

    public boolean configured() {
        return cfg.configured() || tokens != null;
    }

    /** Test hook: supply access tokens without a real service account. */
    void setTokenSupplier(Supplier<String> tokens) {
        this.tokens = tokens;
    }

    public record Result(String spreadsheetId, String range, int rows, String url) {
    }

    public Result export(String spreadsheetId, String sheetName, List<Column> columns,
                         java.util.function.Consumer<java.util.function.Consumer<StoredItem>> source) {
        String id = spreadsheetId != null && !spreadsheetId.isBlank() ? spreadsheetId : cfg.spreadsheetId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("spreadsheetId is required (or set SHEETS_SPREADSHEET_ID)");
        }
        String tab = sheetName == null || sheetName.isBlank() ? "ScrapeKit" : sheetName.replace("'", "");
        List<List<Object>> values = new ArrayList<>();
        values.add(new ArrayList<>(columns.stream().map(Column::header).toList()));
        source.accept(item -> {
            if (values.size() <= MAX_ROWS) {
                List<Object> row = new ArrayList<>(columns.size());
                for (Column c : columns) {
                    Object v = item.fields().get(c.key());
                    row.add(v instanceof Number || v instanceof Boolean ? v : Cells.safeText(v, c.type()));
                }
                values.add(row);
            }
        });
        String token = "Bearer " + token();
        String range = "'" + tab + "'";
        http.post().uri("/v4/spreadsheets/{id}/values/{range}:clear", id, range)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).body(Map.of())
                .retrieve().toBodilessEntity();
        http.put().uri("/v4/spreadsheets/{id}/values/{range}?valueInputOption=RAW", id, range + "!A1")
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("majorDimension", "ROWS", "values", values))
                .retrieve().toBodilessEntity();
        return new Result(id, tab + "!A1", values.size() - 1, "https://docs.google.com/spreadsheets/d/" + id);
    }

    private String token() {
        if (tokens != null) {
            return tokens.get();
        }
        try (var in = new FileInputStream(cfg.credentialsFile())) {
            GoogleCredentials creds = GoogleCredentials.fromStream(in).createScoped(SCOPE);
            creds.refreshIfExpired();
            return creds.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new SheetsNotConfiguredException("Could not load Google credentials: " + e.getMessage());
        }
    }

    public static class SheetsNotConfiguredException extends RuntimeException {
        public SheetsNotConfiguredException(String message) {
            super(message);
        }
    }
}
