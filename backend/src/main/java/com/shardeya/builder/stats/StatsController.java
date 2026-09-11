package com.shardeya.builder.stats;

import com.shardeya.builder.stats.dto.BreakdownSlice;
import com.shardeya.builder.stats.dto.BrokerRanking;
import com.shardeya.builder.stats.dto.CollectionVsTargetPoint;
import com.shardeya.builder.stats.dto.FunnelStage;
import com.shardeya.builder.stats.dto.MonthPoint;
import com.shardeya.builder.stats.dto.StaffPerformanceRow;
import com.shardeya.builder.stats.dto.StatsOverviewResponse;
import com.shardeya.platform.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * B-15 §9: REPORT_VIEW_ALL for the full page. Financial charts additionally
 * need FINANCIAL_VIEW (checked per-endpoint below, not just once) --
 * Accounts Staff (FINANCIAL_VIEW, no REPORT_VIEW_ALL) is deliberately left
 * unable to reach ANY of these endpoints this round (they'd need a
 * REPORT_FINANCIAL-gated subset of charts to get financial-only access,
 * which B-15's nine-chart page doesn't cleanly split into -- a real,
 * narrow scope trim, see CLAUDE.md).
 */
@RestController
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/builder/stats/overview")
    @RequiresPermission("REPORT_VIEW_ALL")
    public StatsOverviewResponse overview(@RequestParam(required = false) UUID projectId,
                                           @RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        return service.overview(projectId, from, to);
    }

    @GetMapping("/api/v1/builder/stats/monthly-sales")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<MonthPoint> monthlySales(@RequestParam(required = false) UUID projectId,
                                          @RequestParam(defaultValue = "12") int months) {
        return service.monthlySales(projectId, clampMonths(months));
    }

    @GetMapping("/api/v1/builder/stats/monthly-revenue")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<MonthPoint> monthlyRevenue(@RequestParam(required = false) UUID projectId,
                                            @RequestParam(defaultValue = "12") int months) {
        return service.monthlyRevenue(projectId, clampMonths(months));
    }

    @GetMapping("/api/v1/builder/stats/plot-status-breakdown")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<BreakdownSlice> plotStatusBreakdown(@RequestParam(required = false) UUID projectId) {
        return service.plotStatusBreakdown(projectId);
    }

    @GetMapping("/api/v1/builder/stats/leads-by-source")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<BreakdownSlice> leadsBySource(@RequestParam(required = false) UUID projectId,
                                               @RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to) {
        return service.leadsBySource(projectId, from, to);
    }

    @GetMapping("/api/v1/builder/stats/conversion-funnel")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<FunnelStage> conversionFunnel(@RequestParam(required = false) UUID projectId,
                                               @RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to) {
        return service.conversionFunnel(projectId, from, to);
    }

    @GetMapping("/api/v1/builder/stats/top-brokers")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<BrokerRanking> topBrokers(@RequestParam(defaultValue = "5") int limit,
                                           @RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        return service.topBrokers(Math.min(Math.max(limit, 3), 20), from, to);
    }

    @GetMapping("/api/v1/builder/stats/revenue-by-project")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<BreakdownSlice> revenueByProject(@RequestParam(required = false) LocalDate from,
                                                  @RequestParam(required = false) LocalDate to) {
        return service.revenueByProject(from, to);
    }

    @GetMapping("/api/v1/builder/stats/collection-vs-target")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<CollectionVsTargetPoint> collectionVsTarget(@RequestParam(required = false) UUID projectId,
                                                              @RequestParam(defaultValue = "12") int months) {
        return service.collectionVsTarget(projectId, clampMonths(months));
    }

    @GetMapping("/api/v1/builder/stats/overdue-trend")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<MonthPoint> overdueTrend(@RequestParam(required = false) UUID projectId,
                                          @RequestParam(defaultValue = "12") int months) {
        return service.overdueTrend(projectId, clampMonths(months));
    }

    @GetMapping("/api/v1/builder/stats/staff-performance")
    @RequiresPermission("REPORT_VIEW_ALL")
    public List<StaffPerformanceRow> staffPerformance(@RequestParam(required = false) LocalDate from,
                                                        @RequestParam(required = false) LocalDate to) {
        return service.staffPerformance(from, to);
    }

    private int clampMonths(int months) {
        return Math.min(Math.max(months, 1), 60);
    }
}
