package dev.scrapekit.export;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import dev.scrapekit.item.StoredItem;
import dev.scrapekit.job.ScrapeJob;
import dev.scrapekit.scrape.Column;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import org.springframework.stereotype.Component;

/**
 * Excel export that is ready to use when opened: typed cells (numbers stay numbers), styled and frozen
 * header, auto-filter, clickable links, sensible column widths and an "Export info" sheet.
 * Uses POI's streaming workbook, so memory stays flat for large datasets.
 */
@Component
public class XlsxExporter {

    static final int MAX_CELL = 32_767;
    static final int MAX_LINKS = 60_000;

    public int write(ScrapeJob job, List<Column> columns, Consumer<Consumer<StoredItem>> source, OutputStream out)
            throws IOException {
        try (var wb = new SXSSFWorkbook(200)) {
            var header = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true);
            hf.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(hf);
            header.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);
            var money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            var centered = wb.createCellStyle();
            centered.setAlignment(HorizontalAlignment.CENTER);
            var link = wb.createCellStyle();
            Font lf = wb.createFont();
            lf.setUnderline(Font.U_SINGLE);
            lf.setColor(IndexedColors.BLUE.getIndex());
            link.setFont(lf);

            SXSSFSheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(job.name()));
            Row h = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(columns.get(c).header());
                cell.setCellStyle(header);
                sheet.setColumnWidth(c, width(columns.get(c)) * 256);
            }
            sheet.createFreezePane(0, 1);
            int[] row = {1};
            int[] links = {0};
            var helper = wb.getCreationHelper();
            source.accept(item -> {
                Row r = sheet.createRow(row[0]++);
                for (int c = 0; c < columns.size(); c++) {
                    Column col = columns.get(c);
                    Object v = item.fields().get(col.key());
                    if (v == null) {
                        continue;
                    }
                    Cell cell = r.createCell(c);
                    switch (col.type()) {
                        case NUMBER -> {
                            cell.setCellValue(((Number) toNumber(v)).doubleValue());
                            cell.setCellStyle(money);
                        }
                        case INTEGER -> {
                            cell.setCellValue(((Number) toNumber(v)).longValue());
                            cell.setCellStyle(centered);
                        }
                        case BOOLEAN -> {
                            cell.setCellValue(Boolean.parseBoolean(String.valueOf(v)) ? "Yes" : "No");
                            cell.setCellStyle(centered);
                        }
                        case URL -> {
                            String url = String.valueOf(v);
                            cell.setCellValue(url);
                            if (links[0] < MAX_LINKS && url.startsWith("http")) {
                                var hl = helper.createHyperlink(HyperlinkType.URL);
                                hl.setAddress(url);
                                cell.setHyperlink(hl);
                                cell.setCellStyle(link);
                                links[0]++;
                            }
                        }
                        default -> cell.setCellValue(truncate(Cells.text(v)));
                    }
                }
            });
            int rows = row[0] - 1;
            if (rows > 0) {
                sheet.setAutoFilter(new CellRangeAddress(0, rows, 0, columns.size() - 1));
            }
            info(wb.createSheet("Export info"), job, rows, header);
            wb.write(out);
            wb.dispose();
            return rows;
        }
    }

    private static void info(Sheet s, ScrapeJob job, int rows, CellStyle bold) {
        String[][] lines = {
                {"Job", job.name()}, {"Source", job.source()}, {"Start URL", job.startUrl()},
                {"Rows", String.valueOf(rows)},
                {"Exported at (UTC)", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC).withNano(0))},
                {"Generated by", "ScrapeKit"}};
        for (int i = 0; i < lines.length; i++) {
            Row r = s.createRow(i);
            Cell k = r.createCell(0);
            k.setCellValue(lines[i][0]);
            k.setCellStyle(bold);
            r.createCell(1).setCellValue(lines[i][1]);
        }
        s.setColumnWidth(0, 20 * 256);
        s.setColumnWidth(1, 70 * 256);
    }

    private static Object toNumber(Object v) {
        return v instanceof Number n ? n : Double.valueOf(String.valueOf(v));
    }

    private static String truncate(String s) {
        return s.length() > MAX_CELL ? s.substring(0, MAX_CELL - 1) + "…" : s;
    }

    static int width(Column c) {
        return switch (c.type()) {
            case NUMBER, INTEGER, BOOLEAN -> Math.max(12, c.header().length() + 5);
            case URL -> 45;
            case LIST -> 30;
            case TEXT -> c.key().equals("quote") ? 80 : (c.key().equals("title") ? 50 : 24);
        };
    }
}
