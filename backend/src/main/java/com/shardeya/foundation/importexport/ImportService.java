package com.shardeya.foundation.importexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.shardeya.builder.plot.GridPlacementResolver;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.foundation.calculator.AreaConversionService;
import com.shardeya.foundation.calculator.MeasurementUnitRepository;
import com.shardeya.foundation.importexport.dto.ImportCommitRequest;
import com.shardeya.foundation.importexport.dto.ImportJobResponse;
import com.shardeya.foundation.importexport.dto.ImportRowResponse;
import com.shardeya.foundation.importexport.dto.PlotImportStartRequest;
import com.shardeya.foundation.media.MediaAsset;
import com.shardeya.foundation.media.MediaAssetRepository;
import com.shardeya.foundation.media.MediaProperties;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.QuotaExceededException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.AreaMeasure;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * B-06 Bulk Plot Upload / M-07 Import Engine, plot-specific configuration.
 * Deliberately synchronous (parse+validate inside the start() call, commit
 * chunked into separate REQUIRES_NEW transactions but driven from one HTTP
 * call, not a background worker) — the doc's "job runs async either way" is
 * aimed at very large files; M2's realistic scale (hundreds to low
 * thousands of rows, hard-capped at 10,000) parses in well under a second,
 * and a background-job/SSE-progress pipeline would be a lot of new
 * infrastructure for no real benefit yet.
 */
@Service
public class ImportService {

    private static final int MAX_ROWS = 10_000;
    private static final int COMMIT_CHUNK_SIZE = 200;
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]");

    private final ImportJobRepository jobRepository;
    private final ImportRowRepository rowRepository;
    private final ProjectRepository projectRepository;
    private final PlotRepository plotRepository;
    private final MeasurementUnitRepository measurementUnitRepository;
    private final AreaConversionService areaConversionService;
    private final EntitlementService entitlementService;
    private final ProjectAccessGuard accessGuard;
    private final PlotImportTemplateGenerator templateGenerator;
    private final S3Client s3Client;
    private final MediaProperties mediaProperties;
    private final MediaAssetRepository mediaAssetRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTransaction;
    private final TenantContextBinder tenantContextBinder;

    public ImportService(ImportJobRepository jobRepository, ImportRowRepository rowRepository,
                          ProjectRepository projectRepository, PlotRepository plotRepository,
                          MeasurementUnitRepository measurementUnitRepository, AreaConversionService areaConversionService,
                          EntitlementService entitlementService, ProjectAccessGuard accessGuard,
                          PlotImportTemplateGenerator templateGenerator, S3Client s3Client, MediaProperties mediaProperties,
                          MediaAssetRepository mediaAssetRepository, ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager, TenantContextBinder tenantContextBinder) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.projectRepository = projectRepository;
        this.plotRepository = plotRepository;
        this.measurementUnitRepository = measurementUnitRepository;
        this.areaConversionService = areaConversionService;
        this.entitlementService = entitlementService;
        this.accessGuard = accessGuard;
        this.templateGenerator = templateGenerator;
        this.s3Client = s3Client;
        this.mediaProperties = mediaProperties;
        this.mediaAssetRepository = mediaAssetRepository;
        this.objectMapper = objectMapper;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public byte[] generateTemplate(UUID projectId) {
        Project project = loadProject(projectId);
        return templateGenerator.generate(project.getName());
    }

    @Transactional
    public ImportJobResponse start(UUID projectId, PlotImportStartRequest req) {
        Project project = loadProject(projectId);
        // B-06 §9/§23.1: Excel import is Pro/Premium only, unlike Path A
        // (Quick Range Create), which has no plan gate at all. This was
        // deliberately left unenforced through M2/M3 (see EntitlementService's
        // own class javadoc) since no paid plan existed to test the unlocked
        // side; Quick Create's whole point as a Free-plan-available contrast
        // to this path is what makes enforcing it now actually meaningful.
        entitlementService.assertFeatureEnabled(project.getOrgId(), "BULK_UPLOAD_ENABLED", "error.import.bulkUploadRequiresUpgrade");
        MediaAsset source = mediaAssetRepository.findById(req.mediaId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));

        List<Map<String, String>> rawRows = parseWorkbook(download(source));
        if (rawRows.size() > MAX_ROWS) {
            throw new BadRequestException("file", "IMPORT_TOO_MANY_ROWS", "error.import.tooManyRows");
        }

        ImportJob job = new ImportJob(UUID.randomUUID(), project.getOrgId(), ImportJob.EntityType.PLOT,
                project.getId(), source.getId(), PlotImportColumns.TEMPLATE_VERSION, tenantContextBinder.current().userId());
        jobRepository.save(job);

        String duplicateMode = req.duplicateMode() == null ? "SKIP" : req.duplicateMode();
        Set<String> seenNorms = new HashSet<>();
        Set<String> seenCells = new HashSet<>();
        int validCount = 0;
        int rowNumber = 2; // header is row 1
        for (Map<String, String> raw : rawRows) {
            RowValidation result = validateRow(project, raw, seenNorms, seenCells, duplicateMode, req.autoPlace());
            ImportRow row = new ImportRow(UUID.randomUUID(), job.getId(), rowNumber++, writeJson(raw));
            row.setNormalisedData(result.normalized == null ? null : writeJson(result.normalized));
            row.setErrors(writeJson(result.errors));
            row.setStatus(result.errors.isEmpty() ? ImportRow.Status.VALID : ImportRow.Status.INVALID);
            rowRepository.save(row);
            if (result.errors.isEmpty()) validCount++;
        }

        job.setTotalRows(rawRows.size());
        job.setValidRows(validCount);
        job.setInvalidRows(rawRows.size() - validCount);
        job.setStatus(ImportJob.Status.PREVIEW_READY);
        jobRepository.save(job);
        return toResponse(job);
    }

    public ImportJobResponse status(UUID jobId) {
        return toResponse(loadJob(jobId));
    }

    public List<ImportRowResponse> rows(UUID jobId, ImportRow.Status status) {
        ImportJob job = loadJob(jobId);
        List<ImportRow> rows = status == null
                ? rowRepository.findByImportJobIdOrderByRowNumber(job.getId())
                : rowRepository.findByImportJobIdAndStatusOrderByRowNumber(job.getId(), status);
        return rows.stream().map(this::toRowResponse).toList();
    }

    @Transactional
    public ImportRowResponse patchRow(UUID jobId, UUID rowId, Map<String, String> patch) {
        ImportJob job = loadJob(jobId);
        Project project = projectRepository.findByIdAndDeletedAtIsNull(job.getTargetProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        ImportRow row = rowRepository.findInJob(job.getId(), rowId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));

        Map<String, String> merged = new LinkedHashMap<>(readRawMap(row.getRawData()));
        merged.putAll(patch);
        row.setRawData(writeJson(merged));

        // Re-validate against the rest of the (already-committed-to-rows)
        // file, not just this one row in isolation, so a fixed duplicate
        // correctly clears once it's no longer a duplicate.
        Set<String> seenNorms = new HashSet<>();
        Set<String> seenCells = new HashSet<>();
        for (ImportRow other : rowRepository.findByImportJobIdOrderByRowNumber(job.getId())) {
            if (other.getId().equals(row.getId())) continue;
            Map<String, String> otherRaw = readRawMap(other.getRawData());
            String norm = normalize(otherRaw.getOrDefault("plot_number", ""));
            if (!norm.isEmpty()) seenNorms.add(norm);
            String r = otherRaw.get("grid_row");
            String c = otherRaw.get("grid_col");
            if (r != null && !r.isBlank() && c != null && !c.isBlank()) seenCells.add(r.trim() + ":" + c.trim());
        }

        RowValidation result = validateRow(project, merged, seenNorms, seenCells, "SKIP", false);
        row.setNormalisedData(result.normalized == null ? null : writeJson(result.normalized));
        row.setErrors(writeJson(result.errors));
        boolean wasInvalid = row.getStatus() == ImportRow.Status.INVALID;
        row.setStatus(result.errors.isEmpty() ? ImportRow.Status.VALID : ImportRow.Status.INVALID);
        rowRepository.save(row);

        if (wasInvalid != (row.getStatus() == ImportRow.Status.INVALID)) {
            ImportJob fresh = loadJob(jobId);
            fresh.setValidRows(rowRepository.findByImportJobIdAndStatusOrderByRowNumber(job.getId(), ImportRow.Status.VALID).size());
            fresh.setInvalidRows(fresh.getTotalRows() - fresh.getValidRows());
            jobRepository.save(fresh);
        }
        return toRowResponse(row);
    }

    // Chunked commit (M-07 §7: 200 rows/transaction) so a failure partway
    // through doesn't roll back everything already imported — the job
    // records exactly how far it got.
    public ImportJobResponse commit(UUID jobId, ImportCommitRequest req) {
        ImportJob job = loadJob(jobId);
        if (!req.skipInvalid() && job.getInvalidRows() > 0) {
            throw new BadRequestException("skipInvalid", "IMPORT_HAS_INVALID_ROWS", "error.import.hasInvalidRows");
        }
        Project project = projectRepository.findByIdAndDeletedAtIsNull(job.getTargetProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));

        requiresNewTransaction.executeWithoutResult(status -> {
            ImportJob j = jobRepository.findById(jobId).orElseThrow();
            j.setStatus(ImportJob.Status.IMPORTING);
            jobRepository.save(j);
        });

        List<ImportRow> validRows = rowRepository.findByImportJobIdAndStatusOrderByRowNumber(job.getId(), ImportRow.Status.VALID);

        // Unlike the subscription quota (checked per-row inside commitChunk,
        // allowing a partial commit up to the limit), the declared plot
        // count is an all-or-nothing gate: reject the whole batch before
        // writing anything if it would exceed the project's own declared
        // target. Only rows that will actually create a NEW plot count --
        // UPDATE_EXISTING rows update a plot already counted, and rows that
        // resolve to SKIP create nothing.
        long newPlotRows = validRows.stream()
                .map(row -> readNormalisedMap(row.getNormalisedData()))
                .filter(data -> data != null && "CREATE".equals(data.getOrDefault("_action", "CREATE")))
                .count();
        long currentPlotCount = plotRepository.countByProjectIdAndDeletedAtIsNull(project.getId());
        if (currentPlotCount + newPlotRows > project.getDeclaredPlotCount()) {
            throw new ConflictException("error.plot.declaredCountExceeded", Map.of(
                    "declared", project.getDeclaredPlotCount(), "current", currentPlotCount, "requested", newPlotRows));
        }

        int imported = 0;
        for (int i = 0; i < validRows.size(); i += COMMIT_CHUNK_SIZE) {
            List<ImportRow> chunk = validRows.subList(i, Math.min(i + COMMIT_CHUNK_SIZE, validRows.size()));
            imported += commitChunk(project, chunk);
        }

        final int importedFinal = imported;
        requiresNewTransaction.executeWithoutResult(status -> {
            ImportJob j = jobRepository.findById(jobId).orElseThrow();
            j.setImportedRows(importedFinal);
            j.setStatus(ImportJob.Status.COMPLETED);
            j.setCompletedAt(Instant.now());
            jobRepository.save(j);
        });
        return toResponse(loadJob(jobId));
    }

    @Transactional
    public void cancel(UUID jobId) {
        ImportJob job = loadJob(jobId);
        job.setStatus(ImportJob.Status.CANCELLED);
        jobRepository.save(job);
    }

    private int commitChunk(Project project, List<ImportRow> chunk) {
        return requiresNewTransaction.execute(status -> {
            int count = 0;
            for (ImportRow row : chunk) {
                Map<String, Object> data = readNormalisedMap(row.getNormalisedData());
                if (data == null) continue;
                String action = (String) data.getOrDefault("_action", "CREATE");
                if ("SKIP".equals(action)) {
                    row.setStatus(ImportRow.Status.SKIPPED);
                    rowRepository.save(row);
                    continue;
                }

                try {
                    entitlementService.assertWithinQuota(project.getOrgId(), "BUILDER_PLOTS_PER_PROJECT", project.getId(),
                            "error.plot.quotaExceeded");
                } catch (QuotaExceededException e) {
                    // Letting this propagate out of the TransactionTemplate
                    // callback would roll back the WHOLE chunk transaction --
                    // including every row already successfully imported
                    // earlier in this same chunk, not just the ones after
                    // the limit. Confirmed empirically: bulk-importing 500
                    // rows against a 50-plot quota reported "used: 50" from
                    // the exception, yet left ZERO actual plot rows in the
                    // database once the transaction rolled back. Breaking
                    // out here instead keeps every row processed so far in
                    // this chunk committed; remaining rows stay VALID
                    // (never transition to IMPORTED) so the job response
                    // honestly reflects how many were actually created.
                    break;
                }

                Plot plot;
                if ("UPDATE".equals(action)) {
                    plot = plotRepository.findByIdAndDeletedAtIsNull(UUID.fromString((String) data.get("_existingPlotId")))
                            .orElse(null);
                    if (plot == null) continue;
                } else {
                    plot = new Plot(UUID.randomUUID(), project.getOrgId(), project.getId(),
                            (String) data.get("plotNumber"), new BigDecimal(data.get("sizeValue").toString()),
                            (String) data.get("sizeUnit"), new BigDecimal(data.get("sizeSqft").toString()),
                            new BigDecimal(data.get("price").toString()));
                }
                plot.setPlotNumber((String) data.get("plotNumber"));
                plot.setSizeValue(new BigDecimal(data.get("sizeValue").toString()));
                plot.setSizeUnit((String) data.get("sizeUnit"));
                plot.setSizeSqft(new BigDecimal(data.get("sizeSqft").toString()));
                plot.setPrice(new BigDecimal(data.get("price").toString()));
                if (data.get("facing") != null) plot.setFacing(Plot.Facing.valueOf((String) data.get("facing")));
                plot.setStatus(Plot.Status.valueOf((String) data.getOrDefault("status", "AVAILABLE")));
                plot.setReservedFor((String) data.get("reservedFor"));
                plot.setGarden(Boolean.TRUE.equals(data.get("isGarden")));
                plot.setCorner(Boolean.TRUE.equals(data.get("isCorner")));
                plot.setHot(Boolean.TRUE.equals(data.get("isHot")));
                plot.setRemarks((String) data.get("remarks"));
                if (data.get("gridRow") != null) {
                    plot.setGridRow(((Number) data.get("gridRow")).intValue());
                    plot.setGridCol(((Number) data.get("gridCol")).intValue());
                } else {
                    plot.setGridRow(null);
                    plot.setGridCol(null);
                }
                plotRepository.save(plot);
                row.setStatus(ImportRow.Status.IMPORTED);
                row.setCreatedEntityId(plot.getId());
                rowRepository.save(row);
                count++;
            }
            return count;
        });
    }

    private record RowValidation(Map<String, Object> normalized, List<ImportRowResponse.RowError> errors) {
    }

    private RowValidation validateRow(Project project, Map<String, String> raw, Set<String> seenNorms,
                                       Set<String> seenCells, String duplicateMode, boolean autoPlace) {
        List<ImportRowResponse.RowError> errors = new ArrayList<>();
        Map<String, Object> normalized = new HashMap<>();

        String plotNumber = trim(raw.get("plot_number"));
        String norm = normalize(plotNumber);
        if (plotNumber.isEmpty()) {
            errors.add(err("plot_number", "REQUIRED", "error.plot.numberRequired"));
        } else if (seenNorms.contains(norm)) {
            errors.add(err("plot_number", "DUPLICATE_IN_FILE", "error.import.duplicateInFile"));
        } else {
            seenNorms.add(norm);
            normalized.put("plotNumber", plotNumber);
        }

        String statusRaw = trim(raw.get("status")).toUpperCase();
        String status = statusRaw.isEmpty() ? "AVAILABLE" : statusRaw;
        if (status.equals("SOLD")) {
            // Same rule as manual create/edit (PlotService) — SOLD can only
            // exist as a side effect of a plot_sale, which doesn't exist
            // until M3.
            errors.add(err("status", "SOLD_NOT_SUPPORTED", "error.plot.soldNotDirect"));
        } else if (!status.equals("AVAILABLE") && !status.equals("RESERVED")) {
            errors.add(err("status", "INVALID_ENUM", "error.plot.statusInvalid"));
        } else {
            normalized.put("status", status);
        }
        String reservedFor = trim(raw.get("reserved_for"));
        if (status.equals("RESERVED") && reservedFor.isEmpty()) {
            errors.add(err("reserved_for", "REQUIRED_IF_RESERVED", "error.plot.reservedForRequired"));
        }
        normalized.put("reservedFor", reservedFor.isEmpty() ? null : reservedFor);

        BigDecimal sizeValue = parseDecimal(raw.get("size_value"));
        if (sizeValue == null || sizeValue.signum() <= 0) {
            errors.add(err("size_value", "INVALID", "error.plot.sizeInvalid"));
        }
        String sizeUnit = trim(raw.get("size_unit")).toUpperCase();
        boolean unitOk = !sizeUnit.isEmpty() && measurementUnitRepository.findBestMatch(sizeUnit, project.getStateCode()).isPresent();
        if (!unitOk) {
            errors.add(err("size_unit", "UNKNOWN_UNIT", "error.area.unitNotFound"));
        }
        if (sizeValue != null && unitOk) {
            AreaMeasure area = areaConversionService.toSqft(sizeValue, sizeUnit, project.getStateCode());
            normalized.put("sizeValue", area.value());
            normalized.put("sizeUnit", area.unit());
            normalized.put("sizeSqft", area.sqft());
        }

        String facingRaw = trim(raw.get("facing")).toUpperCase();
        if (!facingRaw.isEmpty()) {
            try {
                normalized.put("facing", Plot.Facing.valueOf(facingRaw).name());
            } catch (IllegalArgumentException e) {
                errors.add(err("facing", "INVALID_ENUM", "error.plot.facingInvalid"));
            }
        }

        BigDecimal price = parseDecimal(raw.get("price"));
        if (price == null || price.signum() < 0) {
            errors.add(err("price", "INVALID", "error.plot.priceInvalid"));
        } else {
            normalized.put("price", price);
        }

        normalized.put("isGarden", parseBoolean(raw.get("is_garden")));
        normalized.put("isCorner", parseBoolean(raw.get("is_corner")));
        normalized.put("isHot", parseBoolean(raw.get("is_hot")));
        normalized.put("remarks", trim(raw.get("remarks")));

        resolvePosition(project, raw, plotNumber, autoPlace, seenCells, normalized);

        if (!errors.isEmpty()) {
            return new RowValidation(null, errors);
        }

        if (plotRepository.findByNormalisedNumber(project.getId(), norm).isPresent()) {
            switch (duplicateMode) {
                case "FAIL" -> {
                    return new RowValidation(null, List.of(err("plot_number", "DUPLICATE", "error.plot.numberTaken")));
                }
                case "UPDATE_EXISTING" -> {
                    normalized.put("_action", "UPDATE");
                    normalized.put("_existingPlotId", plotRepository.findByNormalisedNumber(project.getId(), norm).get().getId().toString());
                }
                default -> normalized.put("_action", "SKIP");
            }
        } else {
            normalized.put("_action", "CREATE");
        }

        return new RowValidation(normalized, List.of());
    }

    // B-06 §10: duplicate grid coordinates *within the file* are not a
    // blocking error — the second occurrence simply becomes unplaced.
    // Explicitly-out-of-bounds or DB-occupied cells behave the same way
    // (silently unplaced), matching "grid position... or left unplaced for
    // manual arrangement" (M-07 §7).
    private void resolvePosition(Project project, Map<String, String> raw, String plotNumber, boolean autoPlace,
                                  Set<String> seenCells, Map<String, Object> normalized) {
        Integer row = parseInt(raw.get("grid_row"));
        Integer col = parseInt(raw.get("grid_col"));
        if (row == null && col == null && autoPlace && !plotNumber.isEmpty()) {
            GridPlacementResolver.Position pos = GridPlacementResolver.parseBlockPattern(plotNumber);
            if (pos != null) {
                row = pos.row();
                col = pos.col();
            }
        }
        if (row == null || col == null) {
            return;
        }
        boolean outOfBounds = GridPlacementResolver.isOutOfBounds(project, row, col);
        String cellKey = row + ":" + col;
        boolean occupied = outOfBounds || seenCells.contains(cellKey) || plotRepository.findByCell(project.getId(), row, col).isPresent();
        if (occupied) {
            return; // stays unplaced
        }
        seenCells.add(cellKey);
        normalized.put("gridRow", row);
        normalized.put("gridCol", col);
    }

    private List<Map<String, String>> parseWorkbook(byte[] bytes) {
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("file", "IMPORT_EMPTY_FILE", "error.import.emptyFile");
            }
            Map<Integer, String> columnIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = cell.getStringCellValue().trim().toLowerCase();
                if (!header.isEmpty()) columnIndex.put(cell.getColumnIndex(), header);
            }

            List<Map<String, String>> rows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) continue;
                Map<String, String> data = new LinkedHashMap<>();
                columnIndex.forEach((idx, header) -> data.put(header, cellAsString(row.getCell(idx))));
                rows.add(data);
            }
            return rows;
        } catch (IOException e) {
            throw new BadRequestException("file", "IMPORT_UNREADABLE_FILE", "error.import.unreadableFile");
        }
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK && !cellAsString(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cellAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getStringCellValue();
            default -> "";
        };
    }

    private byte[] download(MediaAsset asset) {
        try (var stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(mediaProperties.getBucketStandard()).key(asset.getStorageKey()).build())) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download import source file", e);
        }
    }

    private Project loadProject(UUID projectId) {
        Project project = projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(project.getId());
        return project;
    }

    private ImportJob loadJob(UUID jobId) {
        ImportJob job = jobRepository.findByIdAndDeletedAtIsNull(jobId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(job.getTargetProjectId());
        return job;
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(job.getId(), job.getStatus().name(), job.getTotalRows(), job.getValidRows(),
                job.getInvalidRows(), job.getImportedRows());
    }

    private ImportRowResponse toRowResponse(ImportRow row) {
        List<ImportRowResponse.RowError> errors = readErrors(row.getErrors());
        return new ImportRowResponse(row.getId(), row.getRowNumber(), readRawMap(row.getRawData()), row.getStatus().name(), errors);
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String normalize(String plotNumber) {
        return NON_ALNUM.matcher(plotNumber).replaceAll("").toUpperCase();
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // Currency strings like "₹ 12,50,000" (M-07 §10) normalised before parsing.
            String cleaned = raw.replaceAll("[₹,\\s]", "");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean parseBoolean(String raw) {
        if (raw == null) return false;
        String v = raw.trim().toLowerCase();
        return v.equals("true") || v.equals("yes") || v.equals("1");
    }

    private ImportRowResponse.RowError err(String column, String code, String messageKey) {
        return new ImportRowResponse.RowError(column, code, messageKey, Map.of());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readRawMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> readNormalisedMap(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private List<ImportRowResponse.RowError> readErrors(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ImportRowResponse.RowError>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
