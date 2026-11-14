package dev.scrapekit.export;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import dev.scrapekit.item.ItemStore;
import dev.scrapekit.item.StoredItem;
import dev.scrapekit.job.JobService;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.scrape.Column;
import dev.scrapekit.scrape.ScraperRegistry;

import org.springframework.stereotype.Service;

@Service
public class ExportService {

    private final JobService jobs;
    private final ItemStore items;
    private final ScraperRegistry scrapers;
    private final CsvExporter csv;
    private final XlsxExporter xlsx;
    private final JsonExporter json;
    private final SheetsExporter sheets;

    public ExportService(JobService jobs, ItemStore items, ScraperRegistry scrapers, CsvExporter csv, XlsxExporter xlsx,
                         JsonExporter json, SheetsExporter sheets) {
        this.jobs = jobs;
        this.items = items;
        this.scrapers = scrapers;
        this.csv = csv;
        this.xlsx = xlsx;
        this.json = json;
        this.sheets = sheets;
    }

    public String fileName(ScrapeJob job, ExportFormat f) {
        String slug = job.name().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug + "-" + LocalDate.now(ZoneOffset.UTC) + "." + f.extension();
    }

    public void write(ScrapeJob job, ExportFormat f, Long newInRun, OutputStream out) throws IOException {
        List<Column> cols = scrapers.get(job.source()).columns();
        Consumer<Consumer<StoredItem>> source = c -> items.stream(job.id(), newInRun, c);
        switch (f) {
            case CSV -> csv.write(cols, source, out);
            case XLSX -> xlsx.write(job, cols, source, out);
            case JSON -> json.write(cols, source, out);
        }
    }

    public SheetsExporter.Result toSheets(long jobId, String spreadsheetId, String sheetName) {
        if (!sheets.configured()) {
            throw new SheetsExporter.SheetsNotConfiguredException(
                    "Google Sheets export is not configured. Set SHEETS_CREDENTIALS_FILE to a service-account key "
                            + "and share the spreadsheet with that account (see README).");
        }
        ScrapeJob job = jobs.get(jobId);
        return sheets.export(spreadsheetId, sheetName, scrapers.get(job.source()).columns(),
                c -> items.stream(job.id(), null, c));
    }
}
