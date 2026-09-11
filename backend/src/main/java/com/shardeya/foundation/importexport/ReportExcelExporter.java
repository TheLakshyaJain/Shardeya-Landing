package com.shardeya.foundation.importexport;

import com.shardeya.foundation.importexport.dto.ReportColumn;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * M-10 §7: "Excel via SXSSF streaming (constant memory), with frozen
 * header, auto-filter, Indian currency number format, date format
 * dd-MM-yyyy, localised headers" and "CSV + Devanagari -> UTF-8 with BOM"
 * -- the first generic (not fixed-schema, unlike PlotImportTemplateGenerator)
 * tabular export in this codebase.
 */
@Component
public class ReportExcelExporter {

    private static final java.util.Set<Character> CSV_INJECTION_PREFIXES = java.util.Set.of('=', '+', '-', '@');

    public byte[] toXlsx(String title, List<ReportColumn> columns, List<Map<String, Object>> rows) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            SXSSFSheet sheet = workbook.createSheet(safeSheetName(title));

            DataFormat format = workbook.createDataFormat();
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(format.getFormat("₹#,##,##0.00"));
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(format.getFormat("dd-MM-yyyy"));

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            // Header text is the column key expanded to Title Case, not a
            // fully localised (Hindi) label -- doing that properly needs a
            // backend-side resource bundle mirroring the frontend's i18next
            // JSON, which this round doesn't build (a real, deliberate
            // simplification; see CLAUDE.md). Every other user-facing
            // string in this codebase resolves client-side, but an XLSX
            // file has no client-side rendering step to defer to.
            SXSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                SXSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(titleCase(columns.get(i).key()));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Map<String, Object> row : rows) {
                SXSSFRow r = sheet.createRow(rowIdx++);
                for (int i = 0; i < columns.size(); i++) {
                    ReportColumn col = columns.get(i);
                    Object value = row.get(col.key());
                    SXSSFCell cell = r.createCell(i);
                    writeCell(cell, col, value, moneyStyle, dateStyle);
                }
            }

            if (rowIdx > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));
            }
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < columns.size(); i++) {
                sheet.trackColumnForAutoSizing(i);
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write XLSX export", e);
        }
    }

    private void writeCell(SXSSFCell cell, ReportColumn col, Object value, CellStyle moneyStyle, CellStyle dateStyle) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        switch (col.type()) {
            case "MONEY" -> {
                cell.setCellValue(new BigDecimal(value.toString()).doubleValue());
                cell.setCellStyle(moneyStyle);
            }
            case "NUMBER" -> cell.setCellValue(new BigDecimal(value.toString()).doubleValue());
            case "DATE" -> cell.setCellValue(value.toString());
            default -> cell.setCellValue(value.toString());
        }
    }

    /** UTF-8 BOM (Excel-on-Windows Devanagari mangling, M-10 §10) + CSV-injection guard (M-10 §10: cells starting =+-@ get a leading '). */
    public byte[] toCsv(List<ReportColumn> columns, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿');
        sb.append(String.join(",", columns.stream().map(c -> escapeCsv(titleCase(c.key()))).toList())).append("\r\n");
        for (Map<String, Object> row : rows) {
            List<String> cells = columns.stream().map(c -> escapeCsv(stringify(row.get(c.key())))).toList();
            sb.append(String.join(",", cells)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    private String escapeCsv(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && CSV_INJECTION_PREFIXES.contains(v.charAt(0))) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private String titleCase(String camelCaseKey) {
        String withSpaces = camelCaseKey.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(withSpaces.charAt(0)) + withSpaces.substring(1);
    }

    private String safeSheetName(String title) {
        String cleaned = title.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }
}
