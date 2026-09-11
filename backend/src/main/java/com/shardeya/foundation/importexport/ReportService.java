package com.shardeya.foundation.importexport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.foundation.importexport.dto.ReportColumn;
import com.shardeya.foundation.importexport.dto.ReportDefinitionSummary;
import com.shardeya.foundation.importexport.dto.ReportPreviewResponse;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M-10 §7 "a report definition is data": one Java method + one query per
 * report code, dispatched from {@link #ROW_SUPPLIERS}/{@link #COLUMN_DEFS} --
 * adding a report means adding an entry to both maps and a private method,
 * never a new endpoint. The SAME method backs both {@link #preview} and
 * {@link #export} (M-10 §7 "the on-screen table and the downloaded file can
 * never disagree") -- pagination for preview is sliced in Java, not a
 * second SQL query, since this milestone's realistic dataset never
 * approaches the row counts where that would matter (see CLAUDE.md).
 */
@Service
public class ReportService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ReportDefinitionRepository definitionRepository;
    private final TenantContextBinder tenantContextBinder;
    private final EntitlementService entitlementService;
    private final ReportExcelExporter excelExporter;
    private final ObjectMapper objectMapper;

    // Reports whose declared REPORT_VIEW_OWN baseline is scoped up to ALL
    // when the caller also holds REPORT_VIEW_ALL -- the same service-layer
    // "check the permission set directly, branch ALL vs OWN" pattern
    // CustomerService/TrackerService already established (CLAUDE.md M4/M5).
    private static final java.util.Set<String> OWN_OR_ALL_REPORTS = java.util.Set.of("LEAD_CUSTOMER", "FOLLOWUP_DUE");

    public ReportService(NamedParameterJdbcTemplate jdbc, ReportDefinitionRepository definitionRepository,
                          TenantContextBinder tenantContextBinder, EntitlementService entitlementService,
                          ReportExcelExporter excelExporter, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.definitionRepository = definitionRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.entitlementService = entitlementService;
        this.excelExporter = excelExporter;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ReportDefinitionSummary> list() {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        boolean exportEnabled = isExportEnabled(tenant.orgId());
        List<ReportDefinitionSummary> out = new ArrayList<>();
        for (ReportDefinition def : definitionRepository.findByProfileOrderBySortOrder("BUILDER")) {
            if (!canView(tenant, def.getCode(), def.getRequiredPermission())) continue;
            out.add(new ReportDefinitionSummary(def.getCode(), def.getProfile(), def.getNameEn(), def.getNameHi(),
                    def.getDescriptionKey(), readJsonList(def.getSupportedFilters()), readJsonStringList(def.getSupportedFormats()),
                    exportEnabled && tenant.permissions().contains("EXPORT_DATA")));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ReportPreviewResponse preview(String code, Map<String, String> filters, int page, int pageSize) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        ReportDefinition def = requireVisible(tenant, code);
        List<Map<String, Object>> allRows = runQuery(code, tenant, filters);
        int from = Math.min(page * pageSize, allRows.size());
        int to = Math.min(from + pageSize, allRows.size());
        return new ReportPreviewResponse(COLUMN_DEFS.get(code), allRows.subList(from, to), allRows.size(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public byte[] export(String code, Map<String, String> filters, String format) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        requireVisible(tenant, code);
        if (!tenant.permissions().contains("EXPORT_DATA")) {
            throw new ForbiddenException("error.report.exportNotPermitted");
        }
        entitlementService.assertFeatureEnabled(tenant.orgId(), "EXPORT_ENABLED", "error.report.exportNotEnabled");
        List<Map<String, Object>> rows = runQuery(code, tenant, filters);
        List<ReportColumn> columns = COLUMN_DEFS.get(code);
        ReportDefinition def = definitionRepository.findById(code).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        String title = tenant.role() != null && "hi".equals(filters.get("lang")) ? def.getNameHi() : def.getNameEn();
        return switch (format) {
            case "XLSX" -> excelExporter.toXlsx(title, columns, rows);
            case "CSV" -> excelExporter.toCsv(columns, rows);
            default -> throw new BadRequestException("format", "UNSUPPORTED_FORMAT", "error.report.unsupportedFormat");
        };
    }

    private boolean isExportEnabled(UUID orgId) {
        try {
            entitlementService.assertFeatureEnabled(orgId, "EXPORT_ENABLED", "error.report.exportNotEnabled");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private ReportDefinition requireVisible(TenantContext.Tenant tenant, String code) {
        ReportDefinition def = definitionRepository.findById(code).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (!canView(tenant, code, def.getRequiredPermission())) {
            throw new ResourceNotFoundException("error.notFound");
        }
        return def;
    }

    private boolean canView(TenantContext.Tenant tenant, String code, String requiredPermission) {
        if (tenant.permissions().contains(requiredPermission)) return true;
        // The two lead-shaped reports also open up to a REPORT_VIEW_ALL
        // holder even though their own declared minimum is REPORT_VIEW_OWN.
        return OWN_OR_ALL_REPORTS.contains(code) && tenant.permissions().contains("REPORT_VIEW_ALL");
    }

    private boolean scopedToOwn(TenantContext.Tenant tenant, String code) {
        return OWN_OR_ALL_REPORTS.contains(code) && !tenant.permissions().contains("REPORT_VIEW_ALL");
    }

    // --- dispatch ----------------------------------------------------------

    private List<Map<String, Object>> runQuery(String code, TenantContext.Tenant tenant, Map<String, String> filters) {
        return switch (code) {
            case "PROJECT_SUMMARY" -> queryProjectSummary(tenant, filters);
            case "PLOT_INVENTORY" -> queryPlotInventory(tenant, filters);
            case "SALES" -> querySales(tenant, filters);
            case "COLLECTION" -> queryCollection(tenant, filters);
            case "PENDING_COLLECTIONS" -> queryPendingCollections(tenant, filters);
            case "BROKER_COMMISSION" -> queryBrokerCommission(tenant, filters);
            case "LEAD_CUSTOMER" -> queryLeadCustomer(tenant, filters);
            case "FOLLOWUP_DUE" -> queryFollowupDue(tenant, filters);
            case "STAFF_ACTIVITY" -> queryStaffActivity(tenant, filters);
            default -> throw new ResourceNotFoundException("error.notFound");
        };
    }

    // --- individual report queries -----------------------------------------

    private List<Map<String, Object>> queryProjectSummary(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("p.org_id = :orgId AND p.deleted_at IS NULL");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        applyProjectScope(tenant, where, params, "p.id");
        if (f.get("projectId") != null) { where.append(" AND p.id = :projectId"); params.addValue("projectId", uuid(f.get("projectId"))); }
        if (f.get("status") != null) { where.append(" AND p.status = CAST(:status AS project_status)"); params.addValue("status", f.get("status")); }
        if (f.get("from") != null) { where.append(" AND p.launch_date >= :from"); params.addValue("from", date(f.get("from"))); }
        if (f.get("to") != null) { where.append(" AND p.launch_date <= :to"); params.addValue("to", date(f.get("to"))); }

        String sql = """
                SELECT p.name AS project_name, p.status, p.city, p.total_area_sqft, p.declared_plot_count,
                       COUNT(pl.id) FILTER (WHERE pl.deleted_at IS NULL) AS total_plots,
                       COUNT(pl.id) FILTER (WHERE pl.status = 'AVAILABLE' AND pl.deleted_at IS NULL) AS available_plots,
                       COUNT(pl.id) FILTER (WHERE pl.status = 'SOLD' AND pl.deleted_at IS NULL) AS sold_plots,
                       COUNT(pl.id) FILTER (WHERE pl.status = 'RESERVED' AND pl.deleted_at IS NULL) AS reserved_plots,
                       COALESCE(SUM(s.deal_value) FILTER (WHERE s.status <> 'CANCELLED' AND s.deleted_at IS NULL), 0) AS revenue
                FROM project p
                LEFT JOIN plot pl ON pl.project_id = p.id
                LEFT JOIN plot_sale s ON s.plot_id = pl.id AND s.status <> 'CANCELLED' AND s.deleted_at IS NULL
                WHERE %s
                GROUP BY p.id, p.name, p.status, p.city, p.total_area_sqft, p.declared_plot_count
                ORDER BY p.name
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> queryPlotInventory(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("pl.org_id = :orgId AND pl.deleted_at IS NULL");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        applyProjectScope(tenant, where, params, "pl.project_id");
        if (f.get("projectId") != null) { where.append(" AND pl.project_id = :projectId"); params.addValue("projectId", uuid(f.get("projectId"))); }
        if (f.get("status") != null) { where.append(" AND pl.status = CAST(:status AS plot_status)"); params.addValue("status", f.get("status")); }
        if (f.get("facing") != null) { where.append(" AND pl.facing = CAST(:facing AS plot_facing)"); params.addValue("facing", f.get("facing")); }
        if (f.get("minSqft") != null) { where.append(" AND pl.size_sqft >= :minSqft"); params.addValue("minSqft", new BigDecimal(f.get("minSqft"))); }
        if (f.get("maxSqft") != null) { where.append(" AND pl.size_sqft <= :maxSqft"); params.addValue("maxSqft", new BigDecimal(f.get("maxSqft"))); }

        String sql = """
                SELECT pl.plot_number, p.name AS project_name, pl.status, pl.facing, pl.size_sqft, pl.size_value, pl.size_unit,
                       pl.price, pl.price_per_unit
                FROM plot pl JOIN project p ON p.id = pl.project_id
                WHERE %s
                ORDER BY p.name, pl.plot_number
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> querySales(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("s.org_id = :orgId AND s.deleted_at IS NULL");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        applyProjectScope(tenant, where, params, "s.project_id");
        if (f.get("projectId") != null) { where.append(" AND s.project_id = :projectId"); params.addValue("projectId", uuid(f.get("projectId"))); }
        if (f.get("from") != null) { where.append(" AND s.purchase_date >= :from"); params.addValue("from", date(f.get("from"))); }
        if (f.get("to") != null) { where.append(" AND s.purchase_date <= :to"); params.addValue("to", date(f.get("to"))); }
        if (f.get("brokerPartnerId") != null) { where.append(" AND s.broker_partner_id = :brokerId"); params.addValue("brokerId", uuid(f.get("brokerPartnerId"))); }

        String sql = """
                SELECT s.buyer_name, pl.plot_number, p.name AS project_name, s.deal_value, s.purchase_date,
                       s.payment_type, s.status, b.full_name AS broker_name
                FROM plot_sale s
                JOIN plot pl ON pl.id = s.plot_id
                JOIN project p ON p.id = s.project_id
                LEFT JOIN broker_partner b ON b.id = s.broker_partner_id
                WHERE %s
                ORDER BY s.purchase_date DESC
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> queryCollection(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("r.org_id = :orgId");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        applyProjectScope(tenant, where, params, "r.project_id");
        if (f.get("projectId") != null) { where.append(" AND r.project_id = :projectId"); params.addValue("projectId", uuid(f.get("projectId"))); }
        if (f.get("from") != null) { where.append(" AND r.paid_on >= :from"); params.addValue("from", date(f.get("from"))); }
        if (f.get("to") != null) { where.append(" AND r.paid_on <= :to"); params.addValue("to", date(f.get("to"))); }
        if (f.get("mode") != null) { where.append(" AND r.mode = CAST(:mode AS payment_mode)"); params.addValue("mode", f.get("mode")); }

        String sql = """
                SELECT r.receipt_no, s.buyer_name, pl.plot_number, p.name AS project_name, r.amount, r.paid_on, r.mode
                FROM payment_record r
                JOIN plot_sale s ON s.id = r.plot_sale_id
                JOIN plot pl ON pl.id = r.plot_id
                JOIN project p ON p.id = r.project_id
                WHERE %s
                ORDER BY r.paid_on DESC
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> queryPendingCollections(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("s.org_id = :orgId AND sch.deleted_at IS NULL AND s.deleted_at IS NULL "
                + "AND sch.status IN ('PENDING','PARTIALLY_PAID','OVERDUE')");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        applyProjectScope(tenant, where, params, "s.project_id");
        if (f.get("projectId") != null) { where.append(" AND s.project_id = :projectId"); params.addValue("projectId", uuid(f.get("projectId"))); }
        if ("true".equals(f.get("overdueOnly"))) { where.append(" AND sch.due_date < :today"); params.addValue("today", LocalDate.now()); }

        String sql = """
                SELECT s.buyer_name, pl.plot_number, p.name AS project_name,
                       (sch.expected_amount - sch.amount_allocated) AS amount_due, sch.due_date,
                       GREATEST(0, (CURRENT_DATE - sch.due_date)) AS days_overdue, sch.status
                FROM payment_schedule sch
                JOIN plot_sale s ON s.id = sch.plot_sale_id
                JOIN plot pl ON pl.id = s.plot_id
                JOIN project p ON p.id = s.project_id
                WHERE %s
                ORDER BY sch.due_date
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> queryBrokerCommission(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("c.org_id = :orgId AND c.deleted_at IS NULL");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        applyProjectScope(tenant, where, params, "c.project_id");
        if (f.get("brokerPartnerId") != null) { where.append(" AND c.broker_partner_id = :brokerId"); params.addValue("brokerId", uuid(f.get("brokerPartnerId"))); }
        if (f.get("projectId") != null) { where.append(" AND c.project_id = :projectId"); params.addValue("projectId", uuid(f.get("projectId"))); }
        if (f.get("from") != null) { where.append(" AND c.deal_date >= :from"); params.addValue("from", date(f.get("from"))); }
        if (f.get("to") != null) { where.append(" AND c.deal_date <= :to"); params.addValue("to", date(f.get("to"))); }

        String sql = """
                SELECT b.full_name AS broker_name, p.name AS project_name, pl.plot_number, c.deal_date, c.deal_value,
                       c.total_commission, c.amount_paid, c.balance_due, c.status
                FROM commission_ledger_entry c
                JOIN broker_partner b ON b.id = c.broker_partner_id
                JOIN project p ON p.id = c.project_id
                JOIN plot pl ON pl.id = c.plot_id
                WHERE %s
                ORDER BY c.deal_date DESC
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> queryLeadCustomer(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("cu.org_id = :orgId AND cu.deleted_at IS NULL");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        applyProjectScope(tenant, where, params, "cu.interested_project_id");
        if (scopedToOwn(tenant, "LEAD_CUSTOMER")) { where.append(" AND cu.assigned_to = :selfId"); params.addValue("selfId", tenant.userId()); }
        if (f.get("projectId") != null) { where.append(" AND cu.interested_project_id = :projectId"); params.addValue("projectId", uuid(f.get("projectId"))); }
        if (f.get("status") != null) { where.append(" AND cu.status = CAST(:status AS lead_status)"); params.addValue("status", f.get("status")); }
        if (f.get("source") != null) { where.append(" AND cu.source = CAST(:source AS customer_source)"); params.addValue("source", f.get("source")); }
        if (f.get("assignedTo") != null) { where.append(" AND cu.assigned_to = :assignedTo"); params.addValue("assignedTo", uuid(f.get("assignedTo"))); }

        String sql = """
                SELECT cu.full_name, cu.mobile, cu.status, cu.source, p.name AS project_name,
                       u.full_name AS assigned_to_name, cu.follow_up_date
                FROM customer cu
                LEFT JOIN project p ON p.id = cu.interested_project_id
                LEFT JOIN app_user u ON u.id = cu.assigned_to
                WHERE %s
                ORDER BY cu.created_at DESC
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> queryFollowupDue(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("cu.org_id = :orgId AND cu.deleted_at IS NULL AND cu.follow_up_date IS NOT NULL");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        if (scopedToOwn(tenant, "FOLLOWUP_DUE")) { where.append(" AND cu.assigned_to = :selfId"); params.addValue("selfId", tenant.userId()); }
        if (f.get("assignedTo") != null) { where.append(" AND cu.assigned_to = :assignedTo"); params.addValue("assignedTo", uuid(f.get("assignedTo"))); }
        if (f.get("from") != null) { where.append(" AND cu.follow_up_date >= :from"); params.addValue("from", date(f.get("from"))); }
        if (f.get("to") != null) { where.append(" AND cu.follow_up_date <= :to"); params.addValue("to", date(f.get("to"))); }

        String sql = """
                SELECT cu.full_name, cu.mobile, u.full_name AS assigned_to_name, cu.follow_up_date,
                       (cu.follow_up_date - CURRENT_DATE) AS days_until_due
                FROM customer cu
                LEFT JOIN app_user u ON u.id = cu.assigned_to
                WHERE %s
                ORDER BY cu.follow_up_date
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    private List<Map<String, Object>> queryStaffActivity(TenantContext.Tenant tenant, Map<String, String> f) {
        StringBuilder where = new StringBuilder("a.org_id = :orgId");
        MapSqlParameterSource params = new MapSqlParameterSource("orgId", tenant.orgId());
        if (f.get("assignedTo") != null) { where.append(" AND a.user_id = :assignedTo"); params.addValue("assignedTo", uuid(f.get("assignedTo"))); }
        if (f.get("from") != null) { where.append(" AND a.month >= date_trunc('month', CAST(:from AS date))"); params.addValue("from", date(f.get("from"))); }
        if (f.get("to") != null) { where.append(" AND a.month <= date_trunc('month', CAST(:to AS date))"); params.addValue("to", date(f.get("to"))); }

        String sql = """
                SELECT u.full_name AS staff_name, SUM(a.leads_handled) AS leads_handled, SUM(a.deals_closed) AS deals_closed,
                       SUM(a.follow_ups_logged) AS follow_ups_logged, SUM(a.payments_recorded) AS payments_recorded
                FROM mv_staff_activity a
                JOIN app_user u ON u.id = a.user_id
                WHERE %s
                GROUP BY u.full_name
                ORDER BY u.full_name
                """.formatted(where);
        return jdbc.query(sql, params, this::rowToMap);
    }

    // --- helpers -------------------------------------------------------

    private void applyProjectScope(TenantContext.Tenant tenant, StringBuilder where, MapSqlParameterSource params, String projectIdColumn) {
        if (!tenant.allProjects()) {
            where.append(" AND ").append(projectIdColumn).append(" = ANY(:scopeProjectIds)");
            params.addValue("scopeProjectIds", tenant.projectScope().toArray(new UUID[0]));
        }
    }

    private Map<String, Object> rowToMap(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        java.sql.ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String key = toCamelCase(meta.getColumnLabel(i));
            Object value = rs.getObject(i);
            row.put(key, value instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : value);
        }
        return row;
    }

    private String toCamelCase(String snake) {
        String[] parts = snake.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }

    private UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            throw new BadRequestException("filters", "INVALID_UUID_FILTER", "error.report.invalidFilter");
        }
    }

    private LocalDate date(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw new BadRequestException("filters", "INVALID_DATE_FILTER", "error.report.invalidFilter");
        }
    }

    private List<Object> readJsonList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> readJsonStringList(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of("XLSX", "CSV");
        }
    }

    // --- column definitions (M-10 §7: columns[{key, labelKey, type}]) -----

    private static final Map<String, List<ReportColumn>> COLUMN_DEFS = Map.ofEntries(
            Map.entry("PROJECT_SUMMARY", List.of(
                    new ReportColumn("projectName", "columns.projectName", "TEXT"),
                    new ReportColumn("status", "columns.status", "TEXT"),
                    new ReportColumn("city", "columns.city", "TEXT"),
                    new ReportColumn("totalAreaSqft", "columns.totalAreaSqft", "NUMBER"),
                    new ReportColumn("totalPlots", "columns.totalPlots", "NUMBER"),
                    new ReportColumn("availablePlots", "columns.availablePlots", "NUMBER"),
                    new ReportColumn("soldPlots", "columns.soldPlots", "NUMBER"),
                    new ReportColumn("reservedPlots", "columns.reservedPlots", "NUMBER"),
                    new ReportColumn("revenue", "columns.revenue", "MONEY"))),
            Map.entry("PLOT_INVENTORY", List.of(
                    new ReportColumn("plotNumber", "columns.plotNumber", "TEXT"),
                    new ReportColumn("projectName", "columns.projectName", "TEXT"),
                    new ReportColumn("status", "columns.status", "TEXT"),
                    new ReportColumn("facing", "columns.facing", "TEXT"),
                    new ReportColumn("sizeSqft", "columns.sizeSqft", "NUMBER"),
                    new ReportColumn("price", "columns.price", "MONEY"),
                    new ReportColumn("pricePerUnit", "columns.pricePerUnit", "MONEY"))),
            Map.entry("SALES", List.of(
                    new ReportColumn("buyerName", "columns.buyerName", "TEXT"),
                    new ReportColumn("plotNumber", "columns.plotNumber", "TEXT"),
                    new ReportColumn("projectName", "columns.projectName", "TEXT"),
                    new ReportColumn("dealValue", "columns.dealValue", "MONEY"),
                    new ReportColumn("purchaseDate", "columns.purchaseDate", "DATE"),
                    new ReportColumn("paymentType", "columns.paymentType", "TEXT"),
                    new ReportColumn("status", "columns.status", "TEXT"),
                    new ReportColumn("brokerName", "columns.brokerName", "TEXT"))),
            Map.entry("COLLECTION", List.of(
                    new ReportColumn("receiptNo", "columns.receiptNo", "TEXT"),
                    new ReportColumn("buyerName", "columns.buyerName", "TEXT"),
                    new ReportColumn("plotNumber", "columns.plotNumber", "TEXT"),
                    new ReportColumn("projectName", "columns.projectName", "TEXT"),
                    new ReportColumn("amount", "columns.amount", "MONEY"),
                    new ReportColumn("paidOn", "columns.paidOn", "DATE"),
                    new ReportColumn("mode", "columns.mode", "TEXT"))),
            Map.entry("PENDING_COLLECTIONS", List.of(
                    new ReportColumn("buyerName", "columns.buyerName", "TEXT"),
                    new ReportColumn("plotNumber", "columns.plotNumber", "TEXT"),
                    new ReportColumn("projectName", "columns.projectName", "TEXT"),
                    new ReportColumn("amountDue", "columns.amountDue", "MONEY"),
                    new ReportColumn("dueDate", "columns.dueDate", "DATE"),
                    new ReportColumn("daysOverdue", "columns.daysOverdue", "NUMBER"),
                    new ReportColumn("status", "columns.status", "TEXT"))),
            Map.entry("BROKER_COMMISSION", List.of(
                    new ReportColumn("brokerName", "columns.brokerName", "TEXT"),
                    new ReportColumn("projectName", "columns.projectName", "TEXT"),
                    new ReportColumn("plotNumber", "columns.plotNumber", "TEXT"),
                    new ReportColumn("dealDate", "columns.dealDate", "DATE"),
                    new ReportColumn("dealValue", "columns.dealValue", "MONEY"),
                    new ReportColumn("totalCommission", "columns.totalCommission", "MONEY"),
                    new ReportColumn("amountPaid", "columns.amountPaid", "MONEY"),
                    new ReportColumn("balanceDue", "columns.balanceDue", "MONEY"),
                    new ReportColumn("status", "columns.status", "TEXT"))),
            Map.entry("LEAD_CUSTOMER", List.of(
                    new ReportColumn("fullName", "columns.fullName", "TEXT"),
                    new ReportColumn("mobile", "columns.mobile", "TEXT"),
                    new ReportColumn("status", "columns.status", "TEXT"),
                    new ReportColumn("source", "columns.source", "TEXT"),
                    new ReportColumn("projectName", "columns.projectName", "TEXT"),
                    new ReportColumn("assignedToName", "columns.assignedToName", "TEXT"),
                    new ReportColumn("followUpDate", "columns.followUpDate", "DATE"))),
            Map.entry("FOLLOWUP_DUE", List.of(
                    new ReportColumn("fullName", "columns.fullName", "TEXT"),
                    new ReportColumn("mobile", "columns.mobile", "TEXT"),
                    new ReportColumn("assignedToName", "columns.assignedToName", "TEXT"),
                    new ReportColumn("followUpDate", "columns.followUpDate", "DATE"),
                    new ReportColumn("daysUntilDue", "columns.daysUntilDue", "NUMBER"))),
            Map.entry("STAFF_ACTIVITY", List.of(
                    new ReportColumn("staffName", "columns.staffName", "TEXT"),
                    new ReportColumn("leadsHandled", "columns.leadsHandled", "NUMBER"),
                    new ReportColumn("dealsClosed", "columns.dealsClosed", "NUMBER"),
                    new ReportColumn("followUpsLogged", "columns.followUpsLogged", "NUMBER"),
                    new ReportColumn("paymentsRecorded", "columns.paymentsRecorded", "NUMBER")))
    );
}
