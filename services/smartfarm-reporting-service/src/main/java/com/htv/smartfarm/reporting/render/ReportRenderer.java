package com.htv.smartfarm.reporting.render;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
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
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            doc.add(new Paragraph(d.title(), titleFont));
            doc.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(Math.max(1, d.headers().size()));
            table.setWidthPercentage(100);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            for (String h : d.headers()) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, headFont));
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                table.addCell(cell);
            }
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            for (var row : d.rows()) {
                for (String v : row) table.addCell(new PdfPCell(new Paragraph(v == null ? "" : v, bodyFont)));
            }
            doc.add(table);
        } catch (Exception e) {
            throw new IOException("PDF render failed", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    private static String sheetName(String title) {
        String s = title.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
