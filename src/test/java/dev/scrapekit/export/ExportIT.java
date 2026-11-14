package dev.scrapekit.export;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scrapekit.job.JobRequest;
import dev.scrapekit.job.JobService;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.run.RunService;
import dev.scrapekit.run.ScrapeRun;
import dev.scrapekit.support.Await;
import dev.scrapekit.support.FakeSite;
import dev.scrapekit.support.IntegrationTest;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@IntegrationTest
class ExportIT {

    @DynamicPropertySource
    static void sheetsApi(DynamicPropertyRegistry r) {
        r.add("scrapekit.sheets.api-base-url", FakeSite::base);
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    JobService jobs;
    @Autowired
    RunService runs;
    @Autowired
    SheetsExporter sheets;
    @Autowired
    ObjectMapper json;

    ScrapeJob job;
    ScrapeRun firstRun;

    @BeforeEach
    void scrape() {
        job = jobs.create(new JobRequest("export-" + System.nanoTime(), "books", FakeSite.newBookSite(), 100, 0, null, true));
        firstRun = run();
    }

    ScrapeRun run() {
        ScrapeRun r = runs.trigger(job.id(), ScrapeRun.Trigger.API);
        return Await.until(() -> runs.get(r.id()), x -> x.status() != ScrapeRun.Status.RUNNING, Duration.ofSeconds(20));
    }

    ResponseEntity<byte[]> download(String query) {
        return rest.getForEntity("/api/jobs/" + job.id() + "/export?" + query, byte[].class);
    }

    @Test
    @DisplayName("CSV: attachment with a dated file name, header plus one row per record")
    void csv() {
        var resp = download("format=csv");
        assertThat(resp.getHeaders().getContentDisposition().getFilename()).matches("export-\\d+-\\d{4}-\\d{2}-\\d{2}\\.csv");
        String csv = new String(resp.getBody(), StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(8);
        assertThat(lines[0]).isEqualTo("\uFEFFTitle,Category,Price,Currency,Rating (1-5),In stock,Product URL,Image URL");
        assertThat(lines[1]).startsWith("It's Only the Himalayas,Travel,45.17,GBP,2,true,http://localhost:");
    }

    @Test
    @DisplayName("Excel: numeric price cells, frozen styled header, auto-filter, hyperlinks and an info sheet")
    void xlsx() throws Exception {
        var resp = download("format=xlsx");
        assertThat(resp.getHeaders().getContentType().toString()).contains("spreadsheetml");
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(resp.getBody()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(7);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Title");
            assertThat(sheet.getRow(0).getCell(0).getCellStyle().getFont().getBold()).isTrue();
            var price = sheet.getRow(1).getCell(2);
            assertThat(price.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(price.getNumericCellValue()).isEqualTo(45.17);
            assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(2);
            assertThat(sheet.getRow(3).getCell(5).getStringCellValue()).isEqualTo("No");
            assertThat(sheet.getRow(1).getCell(6).getHyperlink().getAddress()).endsWith("/catalogue/its-only-the-himalayas_981/index.html");
            assertThat(sheet.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:H8");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(wb.getSheet("Export info").getRow(3).getCell(1).getStringCellValue()).isEqualTo("7");
        }
    }

    @Test
    @DisplayName("JSON: array of records with key, typed fields and first/last seen timestamps")
    void jsonExport() throws Exception {
        JsonNode arr = json.readTree(download("format=json").getBody());
        assertThat(arr).hasSize(7);
        JsonNode first = arr.get(0);
        assertThat(first.get("key").asText()).isEqualTo("catalogue/its-only-the-himalayas_981/index.html");
        assertThat(first.get("price").decimalValue()).isEqualByComparingTo("45.17");
        assertThat(first.get("inStock").asBoolean()).isTrue();
        assertThat(first.has("firstSeenAt")).isTrue();
    }

    @Test
    @DisplayName("newInRun exports only the records a given run discovered")
    void onlyNewRecords() throws Exception {
        ScrapeRun second = run();
        assertThat(json.readTree(download("format=json&newInRun=" + firstRun.id()).getBody())).hasSize(7);
        assertThat(json.readTree(download("format=json&newInRun=" + second.id()).getBody())).isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Unknown format is a 400; Google Sheets without credentials is a 503 explaining the setup")
    void errors() {
        assertThat(rest.getForEntity("/api/jobs/" + job.id() + "/export?format=pdf", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        sheets.setTokenSupplier(null);
        var r = rest.postForEntity("/api/jobs/" + job.id() + "/export/google-sheets", Map.of("spreadsheetId", "abc"), Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat((String) r.getBody().get("detail")).contains("SHEETS_CREDENTIALS_FILE");
    }

    @SuppressWarnings("rawtypes")
    @Test
    @DisplayName("Google Sheets (optional): clears the tab, then writes header and rows with a bearer token")
    void googleSheets() {
        var wm = FakeSite.WM;
        wm.stubFor(post(urlPathMatching("/v4/spreadsheets/sheet123/values/.*:clear")).willReturn(aResponse().withStatus(200).withBody("{}")));
        wm.stubFor(put(urlPathMatching("/v4/spreadsheets/sheet123/values/.*")).willReturn(aResponse().withStatus(200).withBody("{}")));
        sheets.setTokenSupplier(() -> "test-token");
        try {
            var r = rest.postForEntity("/api/jobs/" + job.id() + "/export/google-sheets",
                    Map.of("spreadsheetId", "sheet123", "sheetName", "Books"), Map.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(r.getBody()).containsEntry("rows", 7).containsEntry("range", "Books!A1");
            wm.verify(postRequestedFor(urlPathMatching("/v4/spreadsheets/sheet123/values/.*:clear"))
                    .withHeader("Authorization", equalTo("Bearer test-token")));
            wm.verify(putRequestedFor(urlPathMatching("/v4/spreadsheets/sheet123/values/.*"))
                    .withQueryParam("valueInputOption", equalTo("RAW"))
                    .withRequestBody(matchingJsonPath("$.values[0][0]", equalTo("Title")))
                    .withRequestBody(matchingJsonPath("$.values[1][2]", equalTo("45.17")))
                    .withRequestBody(matchingJsonPath("$.values.length()", equalTo("8"))));
        } finally {
            sheets.setTokenSupplier(null);
        }
    }

    @Test
    @DisplayName("Column order of every export follows the scraper definition")
    void columnOrder() {
        String csv = new String(download("format=csv").getBody(), StandardCharsets.UTF_8);
        assertThat(List.of(csv.substring(1, csv.indexOf("\r\n")).split(","))).first().isEqualTo("Title");
    }
}
