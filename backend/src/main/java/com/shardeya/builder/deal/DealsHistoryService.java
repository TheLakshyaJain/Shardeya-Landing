package com.shardeya.builder.deal;

import com.shardeya.builder.deal.dto.DealDetailResponse;
import com.shardeya.builder.deal.dto.DealRow;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.sale.PlotDocumentRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.builder.sale.dto.PlotDocumentResponse;
import com.shardeya.foundation.customer.InteractionService;
import com.shardeya.foundation.customer.dto.InteractionResponse;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.Cursor;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * B-10 Builder Deals History -- read-only archive over plot_sale WHERE
 * status IN (COMPLETED, CANCELLED). Corrections happen in the source
 * modules (B-04/B-05) and flow through; this module writes nothing.
 */
@Service
public class DealsHistoryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantContextBinder tenantContextBinder;
    private final ProjectAccessGuard accessGuard;
    private final PlotSaleRepository saleRepository;
    private final PlotDocumentRepository documentRepository;
    private final PaymentService paymentService;
    private final InteractionService interactionService;

    public DealsHistoryService(NamedParameterJdbcTemplate jdbc, TenantContextBinder tenantContextBinder,
                                ProjectAccessGuard accessGuard, PlotSaleRepository saleRepository,
                                PlotDocumentRepository documentRepository, PaymentService paymentService,
                                InteractionService interactionService) {
        this.jdbc = jdbc;
        this.tenantContextBinder = tenantContextBinder;
        this.accessGuard = accessGuard;
        this.saleRepository = saleRepository;
        this.documentRepository = documentRepository;
        this.paymentService = paymentService;
        this.interactionService = interactionService;
    }

    @Transactional(readOnly = true)
    public CursorPage<DealRow> list(String status, UUID projectId, UUID staffId, LocalDate from, LocalDate to,
                                     String search, String cursorRaw, int limit) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        assertCanView(tenant);
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        if (status != null && !status.equals("COMPLETED") && !status.equals("CANCELLED")) {
            throw new BadRequestException("status", "STATUS_INVALID", "error.deal.statusInvalid");
        }
        Cursor cursor = Cursor.decode(cursorRaw);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("orgId", tenant.orgId());

        StringBuilder sql = new StringBuilder("""
                SELECT sale.id AS sale_id, COALESCE(sale.cancelled_at::date, sale.purchase_date) AS deal_date,
                       proj.name AS project_name, pl.plot_number, pl.size_sqft, sale.buyer_name, sale.buyer_mobile,
                       sale.deal_value, sale.total_paid, sale.balance_due, sale.status, u.full_name AS handled_by_name
                FROM plot_sale sale
                JOIN plot pl ON pl.id = sale.plot_id
                JOIN project proj ON proj.id = sale.project_id
                LEFT JOIN app_user u ON u.id = sale.handled_by
                WHERE sale.org_id = :orgId AND sale.deleted_at IS NULL
                  AND sale.status IN ('COMPLETED','CANCELLED')
                """);
        if (status != null) {
            // sale.status is a Postgres enum (plot_sale_status); a bound
            // PreparedStatement parameter arrives typed as varchar, which
            // has no plot_sale_status = varchar operator without an
            // explicit cast -- same class of bug as
            // FinancialService.payments()'s mode filter (see its own
            // comment), found the same way: only reachable once a caller
            // actually applies this specific filter.
            sql.append(" AND sale.status = CAST(:status AS plot_sale_status)");
            params.addValue("status", status);
        }
        // B-10 §9: Sales Executive sees only deals they handled.
        if (!tenant.permissions().contains("DATA_VIEW_ALL")) {
            sql.append(" AND sale.handled_by = :selfId");
            params.addValue("selfId", tenant.userId());
        }
        if (staffId != null) {
            sql.append(" AND sale.handled_by = :staffId");
            params.addValue("staffId", staffId);
        }
        if (projectId != null) {
            sql.append(" AND sale.project_id = :projectId");
            params.addValue("projectId", projectId);
        } else if (!tenant.allProjects()) {
            sql.append(" AND sale.project_id = ANY(:scopeIds)");
            params.addValue("scopeIds", tenant.projectScope().toArray(new UUID[0]));
        }
        if (from != null) {
            sql.append(" AND COALESCE(sale.cancelled_at::date, sale.purchase_date) >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND COALESCE(sale.cancelled_at::date, sale.purchase_date) <= :to");
            params.addValue("to", to);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (sale.buyer_name ILIKE :search OR sale.buyer_mobile ILIKE :search OR pl.plot_number ILIKE :search)");
            params.addValue("search", "%" + search + "%");
        }
        if (cursor != null) {
            sql.append(" AND (COALESCE(sale.cancelled_at::date, sale.purchase_date) < :cursorDate OR " +
                    "(COALESCE(sale.cancelled_at::date, sale.purchase_date) = :cursorDate AND sale.id < :cursorId))");
            params.addValue("cursorDate", cursor.createdAt().atZone(com.shardeya.shared.IndianTime.ZONE).toLocalDate())
                    .addValue("cursorId", cursor.id());
        }
        sql.append(" ORDER BY deal_date DESC, sale.id DESC LIMIT :limit");
        params.addValue("limit", limit + 1);

        List<DealRow> rows = jdbc.query(sql.toString(), params, (rs, i) -> new DealRow(
                (UUID) rs.getObject("sale_id"), rs.getDate("deal_date").toLocalDate(), rs.getString("project_name"),
                rs.getString("plot_number"), rs.getBigDecimal("size_sqft"), rs.getString("buyer_name"),
                rs.getString("buyer_mobile"), rs.getBigDecimal("deal_value"), rs.getBigDecimal("total_paid"),
                rs.getBigDecimal("balance_due"), null, null, rs.getString("status"), rs.getString("handled_by_name")));
        return CursorPage.of(rows, limit, r -> new Cursor(r.date().atStartOfDay(com.shardeya.shared.IndianTime.ZONE).toInstant(), r.saleId()));
    }

    // B-10 §9: view is "DATA_VIEW_ALL, plus Sales Executive sees only deals
    // they handled" -- there is no carve-out for a role with NEITHER (e.g.
    // Accounts Staff, who has FINANCIAL_VIEW but no lead-pipeline
    // permission at all). Without this, such a role would silently see an
    // always-empty list/404 instead of an honest 403 -- the same class of
    // gap already fixed for TrackerService.followUps().
    private void assertCanView(TenantContext.Tenant tenant) {
        if (!tenant.permissions().contains("DATA_VIEW_ALL") && !tenant.permissions().contains("DATA_VIEW_OWN")) {
            throw new com.shardeya.platform.ForbiddenException("error.deal.noAccess");
        }
    }

    @Transactional(readOnly = true)
    public DealDetailResponse detail(UUID saleId) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        assertCanView(tenant);
        PlotSale sale = saleRepository.findByIdAndDeletedAtIsNull(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(sale.getProjectId());
        if (sale.getStatus() != PlotSale.Status.COMPLETED && sale.getStatus() != PlotSale.Status.CANCELLED) {
            // Active/booked sales aren't archived yet -- B-10 §7 "Active
            // sales with outstanding balance stay in the live modules" --
            // same 404-not-403 tenant-isolation shape applied to "not in
            // this view yet" rather than "not your org."
            throw new ResourceNotFoundException("error.notFound");
        }
        if (!tenant.permissions().contains("DATA_VIEW_ALL") && !tenant.userId().equals(sale.getHandledBy())) {
            throw new ResourceNotFoundException("error.notFound");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("orgId", tenant.orgId()).addValue("saleId", saleId);
        var row = jdbc.queryForMap("""
                SELECT proj.name AS project_name, pl.plot_number, pl.size_sqft,
                       u.full_name AS handled_by_name, (u.deleted_at IS NOT NULL) AS handled_by_former
                FROM plot_sale sale
                JOIN plot pl ON pl.id = sale.plot_id
                JOIN project proj ON proj.id = sale.project_id
                LEFT JOIN app_user u ON u.id = sale.handled_by
                WHERE sale.org_id = :orgId AND sale.id = :saleId
                """, params);

        boolean canViewFinancial = tenant.permissions().contains("FINANCIAL_VIEW");
        List<InteractionResponse> timeline = sale.getCustomerId() != null
                ? interactionService.list(sale.getCustomerId(), 100)
                : List.of();

        return new DealDetailResponse(
                sale.getId(), sale.getPurchaseDate(), sale.getStatus().name(),
                (String) row.get("project_name"), (String) row.get("plot_number"), (java.math.BigDecimal) row.get("size_sqft"),
                sale.getBuyerName(), sale.getBuyerMobile(), sale.getBuyerEmail(),
                canViewFinancial ? sale.getDealValue() : null,
                canViewFinancial ? sale.getTotalPaid() : null,
                canViewFinancial ? sale.getBalanceDue() : null,
                sale.getCancellationReason(), (String) row.get("handled_by_name"), (boolean) row.get("handled_by_former"),
                canViewFinancial ? paymentService.listForSale(saleId) : List.of(),
                documentRepository.findByPlotSaleIdAndDeletedAtIsNull(saleId).stream()
                        .map(d -> new PlotDocumentResponse(d.getId(), d.getDocType().name(), d.getLabel(), d.getMediaId(), d.isSensitive()))
                        .toList(),
                timeline,
                List.of());
    }

    @Transactional(readOnly = true)
    public DealsSummary summary(LocalDate from, LocalDate to) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("orgId", tenant.orgId());
        StringBuilder sql = new StringBuilder("""
                SELECT sale.status, COUNT(*) AS cnt, COALESCE(SUM(sale.deal_value),0) AS total_value
                FROM plot_sale sale
                WHERE sale.org_id = :orgId AND sale.deleted_at IS NULL AND sale.status IN ('COMPLETED','CANCELLED')
                """);
        if (from != null) {
            sql.append(" AND COALESCE(sale.cancelled_at::date, sale.purchase_date) >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND COALESCE(sale.cancelled_at::date, sale.purchase_date) <= :to");
            params.addValue("to", to);
        }
        if (!tenant.allProjects()) {
            sql.append(" AND sale.project_id = ANY(:scopeIds)");
            params.addValue("scopeIds", tenant.projectScope().toArray(new UUID[0]));
        }
        sql.append(" GROUP BY sale.status");
        long completedCount = 0;
        long cancelledCount = 0;
        java.math.BigDecimal completedValue = java.math.BigDecimal.ZERO;
        for (var m : jdbc.queryForList(sql.toString(), params)) {
            if ("COMPLETED".equals(m.get("status"))) {
                completedCount = ((Number) m.get("cnt")).longValue();
                completedValue = (java.math.BigDecimal) m.get("total_value");
            } else {
                cancelledCount = ((Number) m.get("cnt")).longValue();
            }
        }
        return new DealsSummary(completedCount, cancelledCount, completedValue);
    }

    public record DealsSummary(long completedCount, long cancelledCount, java.math.BigDecimal completedValue) {
    }
}
