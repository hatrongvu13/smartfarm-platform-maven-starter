package com.htv.smartfarm.reporting.render;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link ReportData} table into the requested format and writes it to disk.
 * CSV is hand-written (RFC-4180 quoting), XLSX via Apache POI, PDF via OpenPDF.
 */
@Component
public class ReportRenderer {

    /** Write the report to {@code file} in the format implied by {@code reportFormat}. */
    public void render(String reportFormat, ReportData data, Path file) throws IOException {
        switch (reportFormat) {
            case "REPORT_FORMAT_XLSX" -> xlsx(data, file);
            case "REPORT_FORMAT_PDF" -> pdf(data, file);
            default -> csv(data, file);
        }
    }

    private void csv(ReportData d, Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(joinCsv(d.headers())).append('\n');
        for (var row : d.rows()) sb.append(joinCsv(row)).append('\n');
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String joinCsv(java.util.List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvCell(cells.get(i)));
        }
        return sb.toString();
    }

    private static String csvCell(String v) {
        String s = v == null ? "" : v;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private void xlsx(ReportData d, Path file) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName(d.title()));
            CellStyle headStyle = wb.createCellStyle();
            var font = wb.createFont();
            font.setBold(true);
            headStyle.setFont(font);
            Row header = sheet.createRow(0);
            for (int c = 0; c < d.headers().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(d.headers().get(c));
                cell.setCellStyle(headStyle);
            }
            int r = 1;
            for (var row : d.rows()) {
                Row xr = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) xr.createCell(c).setCellValue(row.get(c));
            }
            for (int c = 0; c < d.headers().size(); c++) sheet.autoSizeColumn(c);
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
    }

    private void pdf(ReportData d, Path file) throws IOException {
        Document doc = new Document();
        try (OutputStream out = Files.newOutputStream(file)) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            // Unicode-capable base font: the report data (e.g. livestock task titles) contains
            // Vietnamese text, which the built-in Helvetica (WinAnsi/Cp1252) cannot encode —
            // that throws OpenPDF's ExceptionConverter. Load a system TrueType font with
            // IDENTITY_H + embedding so all glyphs render; fall back to Helvetica if none found.
            BaseFont base = unicodeBaseFont();
            Font titleFont = new Font(base, 14, Font.BOLD);
            Font headFont = new Font(base, 10, Font.BOLD);
            Font bodyFont = new Font(base, 9, Font.NORMAL);
            doc.add(new Paragraph(d.title(), titleFont));
            doc.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(Math.max(1, d.headers().size()));
            table.setWidthPercentage(100);
            for (String h : d.headers()) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headFont));
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(cell);
            }
            for (var row : d.rows()) {
                for (String v : row) table.addCell(new PdfPCell(new Paragraph(v == null ? "" : v, bodyFont)));
            }
            doc.add(table);
            // Close the Document BEFORE the try-with-resources closes `out`: doc.close()
            // makes PdfWriter flush the PDF trailer to the stream. If `out` were closed
            // first (which try-with-resources does on block exit, before finally), that
            // final flush would hit a closed channel -> ExceptionConverter: ClosedChannelException.
            doc.close();
        } catch (Exception e) {
            if (doc.isOpen()) doc.close();
            throw new IOException("PDF render failed", e);
        }
    }

    /** Candidate system TTFs with full Unicode/Vietnamese coverage, across macOS/Linux/Windows. */
    private static final String[] UNICODE_TTF_CANDIDATES = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",   // macOS
            "/Library/Fonts/Arial Unicode.ttf",                       // macOS (older)
            "/System/Library/Fonts/Helvetica.ttc,0",                  // macOS (has Vietnamese)
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",        // Debian/Ubuntu
            "/usr/share/fonts/dejavu/DejaVuSans.ttf",                 // Fedora
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "C:/Windows/Fonts/arial.ttf",                             // Windows
    };

    private static BaseFont cachedUnicodeFont;

    private static synchronized BaseFont unicodeBaseFont() {
        if (cachedUnicodeFont != null) return cachedUnicodeFont;
        for (String path : UNICODE_TTF_CANDIDATES) {
            String file = path.contains(",") ? path.substring(0, path.indexOf(',')) : path;
            if (!java.nio.file.Files.exists(java.nio.file.Path.of(file))) continue;
            try {
                cachedUnicodeFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                return cachedUnicodeFont;
            } catch (Exception ignore) {
                // try next candidate
            }
        }
        try {
            // Last resort: built-in Helvetica (WinAnsi). Non-Latin1 glyphs will be dropped,
            // but rendering no longer throws — better than a hard failure.
            cachedUnicodeFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("no usable PDF base font", e);
        }
        return cachedUnicodeFont;
    }

    private static String sheetName(String title) {
        String s = title.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
