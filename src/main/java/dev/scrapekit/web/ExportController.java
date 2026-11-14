package dev.scrapekit.web;

import dev.scrapekit.export.ExportFormat;
import dev.scrapekit.export.ExportService;
import dev.scrapekit.export.SheetsExporter;
import dev.scrapekit.job.JobService;
import dev.scrapekit.job.ScrapeJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@Tag(name = "Exports")
public class ExportController {

    private final JobService jobs;
    private final ExportService exports;

    public ExportController(JobService jobs, ExportService exports) {
        this.jobs = jobs;
        this.exports = exports;
    }

    @GetMapping("/api/jobs/{id}/export")
    @Operation(summary = "Download the job's data as CSV, Excel (xlsx) or JSON")
    public ResponseEntity<StreamingResponseBody> export(
            @PathVariable long id,
            @RequestParam(defaultValue = "csv") @Parameter(description = "csv, xlsx or json") String format,
            @RequestParam(required = false) @Parameter(description = "Only records first seen in this run") Long newInRun) {
        ScrapeJob job = jobs.get(id);
        ExportFormat f = ExportFormat.parse(format);
        StreamingResponseBody body = out -> exports.write(job, f, newInRun, out);
        return ResponseEntity.ok()
                .contentType(f.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(exports.fileName(job, f)).build().toString())
                .body(body);
    }

    public record SheetsRequest(String spreadsheetId, String sheetName) {
    }

    @PostMapping("/api/jobs/{id}/export/google-sheets")
    @Operation(summary = "Write the job's data to a Google Sheet (optional, needs a service account)",
            description = "Replaces the content of the tab. Returns 503 when SHEETS_CREDENTIALS_FILE is not set.")
    public SheetsExporter.Result sheets(@PathVariable long id, @RequestBody(required = false) SheetsRequest req) {
        return exports.toSheets(id, req == null ? null : req.spreadsheetId(), req == null ? null : req.sheetName());
    }
}
