package com.shardeya.foundation.importexport;

import com.shardeya.foundation.calculator.MeasurementUnitRepository;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Template is generated, not static" (M-07 §7): embeds Excel data-validation
 * dropdowns for status/facing/unit so most input errors are impossible
 * rather than caught after upload. A hidden version cell lets a future
 * upload be rejected as "please download the latest template" if the format
 * ever changes.
 */
@Component
public class PlotImportTemplateGenerator {

    private final MeasurementUnitRepository measurementUnitRepository;

    public PlotImportTemplateGenerator(MeasurementUnitRepository measurementUnitRepository) {
        this.measurementUnitRepository = measurementUnitRepository;
    }

    public byte[] generate(String projectName) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Plots");
            Sheet meta = workbook.createSheet("_meta");
            meta.createRow(0).createCell(0).setCellValue(PlotImportColumns.TEMPLATE_VERSION);
            workbook.setSheetHidden(workbook.getSheetIndex(meta), true);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);

            Row header = sheet.createRow(0);
            List<String> headers = PlotImportColumns.HEADERS;
            for (int i = 0; i < headers.size(); i++) {
                var cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("A-12");
            sample.createCell(1).setCellValue("AVAILABLE");
            sample.createCell(3).setCellValue(1200);
            sample.createCell(4).setCellValue("SQ_FT");
            sample.createCell(6).setCellValue(4200000);

            DataValidationHelper validationHelper = sheet.getDataValidationHelper();
            addDropdown(sheet, validationHelper, 1, headers.indexOf("status"),
                    PlotImportColumns.STATUS_VALUES.toArray(new String[0]));
            addDropdown(sheet, validationHelper, 1, headers.indexOf("facing"),
                    PlotImportColumns.FACING_VALUES.toArray(new String[0]));
            List<String> unitCodes = measurementUnitRepository.findByActiveTrueAndStateCodeIsNull().stream()
                    .map(com.shardeya.foundation.calculator.MeasurementUnit::getCode).distinct().collect(Collectors.toList());
            addDropdown(sheet, validationHelper, 1, headers.indexOf("size_unit"), unitCodes.toArray(new String[0]));

            for (int i = 0; i < headers.size(); i++) {
                sheet.setColumnWidth(i, 4000);
            }
            // Excel strips leading zeros from plain numeric cells (e.g. "007"
            // -> 7) — forcing the plot_number column to TEXT format keeps the
            // raw string a builder actually typed (B-06 §10 edge case).
            var textFormat = workbook.createCellStyle();
            textFormat.setDataFormat(workbook.createDataFormat().getFormat("@"));
            sheet.setDefaultColumnStyle(headers.indexOf("plot_number"), textFormat);

            Sheet instructions = workbook.createSheet("Instructions");
            Row title = instructions.createRow(0);
            title.createCell(0).setCellValue("Shardeya plot import template — " + projectName);
            instructions.createRow(2).createCell(0).setCellValue(
                    "Fill one row per plot. status, size_unit and facing must use the dropdown values. "
                            + "plot_number keeps leading zeros as typed. Rows with status=SOLD are not supported "
                            + "yet — import as AVAILABLE or RESERVED.");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate import template", e);
        }
    }

    private void addDropdown(Sheet sheet, DataValidationHelper helper, int firstRow, int col, String[] values) {
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, 1000, col, col);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }
}
