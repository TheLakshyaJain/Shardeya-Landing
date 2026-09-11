package com.shardeya.builder.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentScheduleRepository extends JpaRepository<PaymentSchedule, UUID> {

    Optional<PaymentSchedule> findByIdAndDeletedAtIsNull(UUID id);

    List<PaymentSchedule> findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(UUID plotSaleId);

    @Query("SELECT COALESCE(MAX(s.sequenceNo), 0) FROM PaymentSchedule s WHERE s.plotSaleId = :plotSaleId")
    short findMaxSequenceNo(@Param("plotSaleId") UUID plotSaleId);

    // Overdue sweep (B-05 §7: "nightly job flips PENDING/PARTIALLY_PAID to
    // OVERDUE when due_date < today"). WAIVED/PAID/already-OVERDUE rows are
    // excluded by the status filter itself. Statuses bound as a real
    // parameter, NOT inlined JPQL enum literals -- a literal written
    // directly in the query string makes Hibernate generate a bare
    // "IN ('PENDING','PARTIALLY_PAID')::Status" cast using the Java enum's
    // simple class name instead of the actual Postgres native enum type
    // ("payment_schedule_status"), failing at runtime with "type Status
    // does not exist" -- the exact documented gotcha in OutboxEventRepository's
    // own comment (and the same mistake PlotSaleRepository.findActiveByPlotId
    // originally made too, caught via a real sale-creation smoke test).
    @Query("SELECT s FROM PaymentSchedule s WHERE s.dueDate < :today AND s.status IN :statuses AND s.deletedAt IS NULL")
    List<PaymentSchedule> findNewlyOverdue(@Param("today") LocalDate today, @Param("statuses") Collection<PaymentSchedule.Status> statuses);

    // M-06 second half, "Instalment due today" (§22.3, 09:00 IST). Deliberately
    // NOT restricted to today-only-and-not-yet-overdue via a stricter date
    // equality trick -- due_date = :today is already exact, PENDING/PARTIALLY_PAID
    // already excludes anything the nightly sweep above has since flipped to
    // OVERDUE (which can't happen same-day anyway, that sweep runs on
    // due_date < today, strictly before).
    @Query("SELECT s FROM PaymentSchedule s WHERE s.dueDate = :today AND s.status IN :statuses AND s.deletedAt IS NULL")
    List<PaymentSchedule> findDueToday(@Param("today") LocalDate today, @Param("statuses") Collection<PaymentSchedule.Status> statuses);

    // M-06 second half, "Instalment overdue 3+ days" reminder cadence
    // (§22.3: day 3, then weekly) -- distinct from findNewlyOverdue above,
    // which only fires once, at the PENDING/PARTIALLY_PAID -> OVERDUE
    // transition itself (day 1). status bound as a single @Param, not an
    // inlined enum literal -- see findNewlyOverdue's own comment for why a
    // literal Status constant embedded directly in the query string breaks.
    @Query("SELECT s FROM PaymentSchedule s WHERE s.status = :status AND s.dueDate <= :cutoff AND s.deletedAt IS NULL")
    List<PaymentSchedule> findOverdueDueOnOrBefore(@Param("cutoff") LocalDate cutoff, @Param("status") PaymentSchedule.Status status);
}
