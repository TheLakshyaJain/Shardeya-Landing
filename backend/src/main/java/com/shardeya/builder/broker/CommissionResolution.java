package com.shardeya.builder.broker;

import java.math.BigDecimal;

/**
 * B-14 §7 resolve() algorithm's result -- carries both the numbers AND the
 * pre-built config_snapshot JSON string, so PlotSaleService (which actually
 * writes the commission_ledger_entry row inside the sale transaction) never
 * has to re-derive or re-serialize anything CommissionConfigService already
 * computed. configSnapshotJson is the exact JSONB blob CLAUDE.md's own
 * "rate changes are never retroactive" rule depends on: self-contained
 * proof of exactly which rule produced this number, independent of whether
 * the source commission_config row is later edited or deleted.
 */
public record CommissionResolution(
        BigDecimal baseCommission, BigDecimal tierBonus, BigDecimal totalCommission,
        String appliedScope, String appliedCommissionType, BigDecimal appliedRateValue,
        String tierName, boolean usedBrokerDefault, String configSnapshotJson
) {
}
