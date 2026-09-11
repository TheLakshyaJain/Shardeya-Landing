package com.shardeya.builder.payment;

import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.shared.IndianTime;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared by {@link InstalmentDueTodayScheduler} and
 * {@link OverdueScheduleSweeper}'s reminder sweep -- both need the same
 * buyer-name/plot-number/amount enrichment for a real WhatsApp/SMS body
 * (plain "the schedule's own label" text, all that
 * {@code INSTALMENT_OVERDUE}'s original in-app-only notification ever
 * needed, reads as broken/empty in an actual WhatsApp message).
 * {@code plot_sale}/{@code plot} have no JPA relational mapping to
 * {@code payment_schedule} in this codebase (plain UUID FK columns only,
 * per CLAUDE.md's own data-model conventions), so this is two extra
 * lookups per schedule row -- acceptable at this sweep's twice-daily,
 * per-org cadence and this app's realistic data volumes, not a hot path.
 */
@Component
public class InstalmentReminderParams {

    private final PlotSaleRepository saleRepository;
    private final PlotRepository plotRepository;

    public InstalmentReminderParams(PlotSaleRepository saleRepository, PlotRepository plotRepository) {
        this.saleRepository = saleRepository;
        this.plotRepository = plotRepository;
    }

    public Map<String, Object> build(PaymentSchedule schedule) {
        Map<String, Object> params = new HashMap<>();
        params.put("label", schedule.getLabel() == null ? "" : schedule.getLabel());
        BigDecimal allocated = schedule.getAmountAllocated() == null ? BigDecimal.ZERO : schedule.getAmountAllocated();
        BigDecimal due = schedule.getExpectedAmount().subtract(allocated).setScale(0, RoundingMode.HALF_UP);
        params.put("amount", due.toPlainString());
        params.put("daysOverdue", ChronoUnit.DAYS.between(schedule.getDueDate(), IndianTime.today()));

        saleRepository.findByIdAndDeletedAtIsNull(schedule.getPlotSaleId()).ifPresent(sale -> {
            params.put("buyerName", sale.getBuyerName() == null ? "" : sale.getBuyerName());
            Plot plot = plotRepository.findByIdAndDeletedAtIsNull(sale.getPlotId()).orElse(null);
            params.put("plotNumber", plot == null ? "" : plot.getPlotNumber());
        });
        return params;
    }
}
