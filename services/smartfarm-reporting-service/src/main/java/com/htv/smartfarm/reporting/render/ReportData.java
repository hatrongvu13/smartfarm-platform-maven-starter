package com.htv.smartfarm.reporting.render;

import java.util.ArrayList;
import java.util.List;

/**
 * A rendered report as tabular data: a title, column headers and rows. Format-agnostic — the
 * {@link ReportRenderer} turns it into CSV / XLSX / PDF. Keeps the data source (gRPC calls)
 * decoupled from the output encoding.
 */
public class ReportData {

    private final String title;
    private final List<String> headers;
    private final List<List<String>> rows = new ArrayList<>();

    public ReportData(String title, List<String> headers) {
        this.title = title;
        this.headers = List.copyOf(headers);
    }

    public ReportData addRow(List<String> row) {
        rows.add(List.copyOf(row));
        return this;
    }

    public String title() {
        return title;
    }

    public List<String> headers() {
        return headers;
    }

    public List<List<String>> rows() {
        return rows;
    }
}
