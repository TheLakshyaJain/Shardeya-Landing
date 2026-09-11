package com.shardeya.builder.financial;

import com.shardeya.builder.financial.dto.FinancialPaymentRow;
import com.shardeya.builder.financial.dto.FinancialSummaryResponse;
import com.shardeya.builder.financial.dto.PendingInstalmentRow;
import com.shardeya.builder.financial.dto.RevenueTrendPoint;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * B-08 §9: FINANCIAL_VIEW required for the whole module -- Admin, Manager,
 * Accounts Staff. Sales Executive and View Only cannot access it at all.
 */
@RestController
public class FinancialController {

    private final FinancialService service;

    public FinancialController(FinancialService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/builder/financials/summary")
    @RequiresPermission("FINANCIAL_VIEW")
    public FinancialSummaryResponse summary(@RequestParam(required = false) UUID projectId,
                                             @RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to) {
        return service.summary(projectId, from, to);
    }

    @GetMapping("/api/v1/builder/financials/payments")
    @RequiresPermission("FINANCIAL_VIEW")
    public CursorPage<FinancialPaymentRow> payments(@RequestParam(required = false) UUID projectId,
                                                     @RequestParam(required = false) LocalDate from,
                                                     @RequestParam(required = false) LocalDate to,
                                                     @RequestParam(required = false) String mode,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "25") int limit) {
        return service.payments(projectId, from, to, mode, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/api/v1/builder/financials/pending")
    @RequiresPermission("FINANCIAL_VIEW")
    public CursorPage<PendingInstalmentRow> pending(@RequestParam(required = false) UUID projectId,
                                                     @RequestParam(defaultValue = "false") boolean overdueOnly,
                                                     @RequestParam(required = false) String cursor,
                                                     @RequestParam(defaultValue = "25") int limit) {
        return service.pending(projectId, overdueOnly, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/api/v1/builder/financials/revenue-trend")
    @RequiresPermission("FINANCIAL_VIEW")
    public List<RevenueTrendPoint> revenueTrend(@RequestParam(required = false) UUID projectId,
                                                 @RequestParam(defaultValue = "12") int months) {
        return service.revenueTrend(projectId, months);
    }

    // B-14 Broker Management doesn't exist yet (no commission_payment table)
    // -- same documented boundary every milestone since M3 has noted for
    // broker_partner_id. Returns a genuinely empty list rather than a stub
    // value, since there is truthfully nothing to report yet.
    @GetMapping("/api/v1/builder/financials/commission-paid")
    @RequiresPermission("FINANCIAL_VIEW")
    public List<Object> commissionPaid() {
        return List.of();
    }
}
