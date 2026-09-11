package com.shardeya.builder.broker;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BookingCommissionRepository extends JpaRepository<BookingCommission, UUID> {

    /** Every beneficiary row frozen for one booking -- the exact set PaymentService's release hook walks on every customer payment. */
    List<BookingCommission> findByPlotSaleId(UUID plotSaleId);

    // CommissionReleaseService.releaseForPayment()'s own locked read --
    // PESSIMISTIC_WRITE (SELECT ... FOR UPDATE), acquired as this
    // transaction's FIRST touch to these rows, same discipline as
    // lockForPayoutOldestFirst below. Without this, two concurrent
    // payments on the same sale (or a payment racing a reversal) each read
    // CommissionReleaseRepository.sumReleasedFor() as 0 before either has
    // committed its own commission_release insert, both compute the full
    // target as their own delta, and both insert it -- releasedAmount
    // converges to DOUBLE the correct figure, a genuine duplicate release
    // rather than the lost-update shape V65_012's trigger fix already
    // covers (that fix correctly sums whatever commission_release rows
    // actually exist; it can't stop two genuinely distinct, individually
    // valid rows from being inserted in the first place). Found via a real
    // CI failure (GitHub Actions' more resource-constrained runner exposed
    // the race far more reliably than this project's own local
    // Colima-backed Testcontainers setup ever did) in
    // CommissionReleaseConcurrencyIntegrationTest, which already existed
    // specifically to catch exactly this shape of bug but wasn't exercised
    // in the direction that would have caught it until CI's own timing did.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingCommission b WHERE b.plotSaleId = :plotSaleId")
    List<BookingCommission> lockByPlotSaleId(@Param("plotSaleId") UUID plotSaleId);

    /** A broker's own commission-tree rows across every booking they're a beneficiary of (seller or upline), newest first -- the Ledger-tab-equivalent view for DESIGNATION brokers. */
    List<BookingCommission> findByOrgIdAndBeneficiaryBrokerIdOrderByCreatedAtDesc(UUID orgId, UUID beneficiaryBrokerId);

    // 06-BROKER-NETWORK-ENGINE.md §8a -- BrokerCommissionPaymentService's
    // own oldest-first payout allocation. PESSIMISTIC_WRITE (SELECT ...
    // FOR UPDATE) acquired as this transaction's FIRST touch to these
    // rows -- never preceded by any other read of the same
    // booking_commission rows in the same transaction, which is what
    // guarantees Hibernate issues a genuinely fresh SELECT here rather
    // than returning a stale already-loaded instance (the same
    // same-transaction-staleness class documented repeatedly elsewhere in
    // this codebase, e.g. ScheduleService.syncAllInstalmentProjections()).
    // A second, concurrent payout attempt against the same broker blocks
    // here until this transaction fully commits (including the
    // paid_amount trigger's own write), then sees the correctly-reduced
    // due figures -- this is what makes the hard payout cap itself
    // concurrency-safe, not just the aggregate column (that's the
    // trigger's own job, V65_017).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingCommission b WHERE b.beneficiaryBrokerId = :brokerId AND b.status <> :cancelledStatus ORDER BY b.createdAt ASC")
    List<BookingCommission> lockForPayoutOldestFirst(@Param("brokerId") UUID brokerId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    // 06-BROKER-NETWORK-ENGINE.md §26/§29, build-order step 10's own
    // dashboard aggregates. cancelledStatus bound as a real @Param, never
    // inlined -- the documented, recurring "enum literal inside a JPQL
    // string renders as ::Status using the Java simple name, not the real
    // Postgres type" gotcha (OutboxEventRepository, PlotSaleRepository,
    // PaymentScheduleRepository, CustomerRepository -- twice). "Earned"
    // deliberately EXCLUDES CANCELLED rows, mirroring
    // fn_broker_partner_commission_earned_trigger's (V6_015) own identical
    // exclusion for PERCENTAGE brokers' total_commission_earned.
    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM BookingCommission b "
            + "WHERE b.beneficiaryBrokerId = :brokerId AND b.uplineLevel = 0 AND b.status <> :cancelledStatus")
    BigDecimal sumPersonalEarnedFor(@Param("brokerId") UUID brokerId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM BookingCommission b "
            + "WHERE b.beneficiaryBrokerId = :brokerId AND b.status <> :cancelledStatus")
    BigDecimal sumTotalEarnedFor(@Param("brokerId") UUID brokerId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM BookingCommission b "
            + "WHERE b.beneficiaryBrokerId = :brokerId AND b.commissionType = :type AND b.status <> :cancelledStatus")
    BigDecimal sumEarnedByTypeFor(@Param("brokerId") UUID brokerId, @Param("type") BookingCommission.LineType type,
                                   @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    @Query("SELECT COALESCE(SUM(b.releasedAmount), 0) FROM BookingCommission b "
            + "WHERE b.beneficiaryBrokerId = :brokerId AND b.status <> :cancelledStatus")
    BigDecimal sumReleasedFor(@Param("brokerId") UUID brokerId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    // §8a: SUM(b.pendingAmount) -- Commission Due (Released - Paid), now
    // that paidAmount finally has a real write path
    // (BrokerCommissionPaymentService/V65_017). This is the same figure
    // BrokerCommissionPaymentService.record()'s own hard-cap check sums
    // directly off the locked entities it holds, so this query and that
    // check can never disagree -- used here purely for the read-side
    // dashboard/broker-card aggregates.
    @Query("SELECT COALESCE(SUM(b.pendingAmount), 0) FROM BookingCommission b "
            + "WHERE b.beneficiaryBrokerId = :brokerId AND b.status <> :cancelledStatus")
    BigDecimal sumDueFor(@Param("brokerId") UUID brokerId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    // Deliberately NOT b.pendingAmount, despite the name similarity to
    // sumDueFor above -- this is the commission NOT YET RELEASED at all
    // (total_amount - released_amount), a genuinely different question
    // from Due ("released but not yet paid out"). Both are real, distinct,
    // useful figures now that paidAmount is real: this one answers "how
    // much more could still release as the customer pays more," Due
    // answers "how much can I pay the broker right now." See
    // BookingCommissionResponse's own javadoc for the same distinction at
    // the per-entry level (outstandingAmount vs dueAmount).
    @Query("SELECT COALESCE(SUM(b.totalAmount - b.releasedAmount), 0) FROM BookingCommission b "
            + "WHERE b.beneficiaryBrokerId = :brokerId AND b.status <> :cancelledStatus")
    BigDecimal sumPendingFor(@Param("brokerId") UUID brokerId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    // Org-wide equivalents, for the §29 builder/admin overview's network-wide totals.
    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM BookingCommission b "
            + "WHERE b.orgId = :orgId AND b.status <> :cancelledStatus")
    BigDecimal sumTotalEarnedForOrg(@Param("orgId") UUID orgId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM BookingCommission b "
            + "WHERE b.orgId = :orgId AND b.commissionType = :type AND b.status <> :cancelledStatus")
    BigDecimal sumEarnedByTypeForOrg(@Param("orgId") UUID orgId, @Param("type") BookingCommission.LineType type,
                                      @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    @Query("SELECT COALESCE(SUM(b.releasedAmount), 0) FROM BookingCommission b "
            + "WHERE b.orgId = :orgId AND b.status <> :cancelledStatus")
    BigDecimal sumReleasedForOrg(@Param("orgId") UUID orgId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    @Query("SELECT COALESCE(SUM(b.totalAmount - b.releasedAmount), 0) FROM BookingCommission b "
            + "WHERE b.orgId = :orgId AND b.status <> :cancelledStatus")
    BigDecimal sumPendingForOrg(@Param("orgId") UUID orgId, @Param("cancelledStatus") BookingCommission.Status cancelledStatus);

    @Query("SELECT COUNT(DISTINCT b.beneficiaryBrokerId) FROM BookingCommission b WHERE b.orgId = :orgId")
    long countDistinctBeneficiariesForOrg(@Param("orgId") UUID orgId);
}
