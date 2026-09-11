package com.shardeya.builder.broker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface CommissionReleaseRepository extends JpaRepository<CommissionRelease, UUID> {

    /**
     * The authoritative "released so far" figure for one booking_commission
     * row, read directly from the source rows rather than the (also
     * correct, but only trigger-refreshed after commit-visible-to-this-
     * connection) booking_commission.released_amount column -- used to
     * compute the next delta within the same transaction that's about to
     * insert it, matching plot_sale.total_paid's own "recompute from the
     * real rows, never trust a possibly-stale cached read" pattern.
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM CommissionRelease r WHERE r.bookingCommissionId = :bookingCommissionId")
    BigDecimal sumReleasedFor(@Param("bookingCommissionId") UUID bookingCommissionId);
}
