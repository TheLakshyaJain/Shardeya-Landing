package com.shardeya.builder.sale;

import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentScheduleRepository;
import com.shardeya.builder.payment.PaymentService;
import com.shardeya.builder.payment.ScheduleService;
import com.shardeya.builder.payment.dto.ChequeStatusRequest;
import com.shardeya.builder.payment.dto.PaymentCreateRequest;
import com.shardeya.builder.payment.dto.WaiveRequest;
import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.builder.plot.PlotService;
import com.shardeya.builder.plot.dto.PlotUpdateRequest;
import com.shardeya.builder.project.Project;
import com.shardeya.builder.project.ProjectRepository;
import com.shardeya.builder.sale.dto.CancelSaleRequest;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.ScheduleRowRequest;
import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.calendar.CalendarEvent;
import com.shardeya.foundation.calendar.CalendarEventRepository;
import com.shardeya.foundation.notification.WhatsAppOptinRepository;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.TestTenantContext;
import com.shardeya.shared.IndianTime;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers B-04/B-05's core invariants end-to-end through the real services
 * (not raw SQL): atomic sale creation, the unique-active-sale concurrency
 * guard, payment allocation correctly driving plot_sale.total_paid and
 * payment_schedule.status via their DB triggers, cheque-bounce
 * auto-reversal unwinding an allocation, and cancellation restoring the
 * plot to AVAILABLE while retaining payment history.
 */
class PlotSaleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PlotSaleService plotSaleService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PlotRepository plotRepository;
    @Autowired
    private PlotService plotService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private PaymentScheduleRepository scheduleRepository;
    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private CalendarEventRepository calendarEventRepository;
    @Autowired
    private WhatsAppOptinRepository whatsAppOptinRepository;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearTenant() {
        TestTenantContext.clear();
    }

    @Test
    void creatingASaleIsAtomicAndBlocksASecondActiveSaleOnTheSamePlot() {
        UUID plotId = seedOrgProjectAndPlot();

        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));

        assertThat(sale.status()).isEqualTo("ACTIVE");
        assertThat(plotRepository.findByIdAndDeletedAtIsNull(plotId).orElseThrow().getStatus()).isEqualTo(Plot.Status.SOLD);
        assertThat(plotRepository.findByIdAndDeletedAtIsNull(plotId).orElseThrow().getCurrentSaleId()).isEqualTo(sale.id());
        assertThat(scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id())).hasSize(3);

        // Second sale on the same (now-SOLD) plot must fail cleanly, not
        // silently double-sell it -- ux_plot_active_sale is the real
        // backstop, this pre-flight check is the friendly path to the same
        // outcome.
        assertThatThrownBy(() -> plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(1))))
                .isInstanceOf(ConflictException.class);
    }

    // Real bug found live: PlotDetailDrawer's Edit button had no
    // plot.status guard at all, so it stayed visible and functional even
    // after "Mark Complete." PlotService.update() itself had zero
    // corresponding server-side guard either -- CLAUDE.md rule #5 ("UI
    // hiding is cosmetic") means the frontend hide alone was never enough.
    // A plot's deal-defining attributes (number, size, facing, price)
    // should never change once a buyer has actually committed to it.
    @Test
    void updatingAPlotIsBlockedOnceItsSold() {
        UUID plotId = seedOrgProjectAndPlot();
        plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));

        assertThatThrownBy(() -> plotService.update(plotId,
                new PlotUpdateRequest("A-99", null, null, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void paymentAllocationDrivesSaleAndScheduleStatusViaTriggers() {
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));

        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), IndianTime.today(),
                PaymentRecord.Mode.UPI, "UTR1", "Booking payment", null));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.valueOf(3_700_000));

        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(schedule.get(0).getStatus().name()).isEqualTo("PAID");
        assertThat(schedule.get(1).getStatus().name()).isEqualTo("PENDING");
    }

    // Real bug found live: an instalment fully paid today, due tomorrow,
    // kept showing on the calendar. payment_schedule.status flipped to PAID
    // correctly (this test's own assertion above already proves that), but
    // the calendar_event row for it was never removed -- because
    // PaymentAllocationService.allocate() (called from PaymentService.record(),
    // BEFORE ScheduleService.syncAllInstalmentProjections()) already loads
    // these exact PaymentSchedule entities into the SAME persistence
    // context earlier in the SAME transaction. The DB trigger updates
    // status in the database only; Hibernate's identity map kept returning
    // the pre-trigger, still-PENDING cached instance to
    // syncInstalmentProjection() when it re-queried, so it wrongly upserted
    // the event instead of removing it. Only visible by checking the
    // calendar_event row's OWN committed state after record() returns --
    // schedule.getStatus() read from a fresh, later query (as the test
    // above does) was always correct, which is exactly why this stayed
    // hidden until checked from the calendar's own side.
    @Test
    void fullyPayingAnInstalmentRemovesItFromTheCalendarInTheSameTransaction() {
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        UUID bookingScheduleId = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id())
                .get(0).getId();

        assertThat(calendarEventRepository.findBySourceEntityTypeAndSourceEntityIdAndEventTypeAndDeletedAtIsNull(
                CalendarEvent.SourceEntityType.PAYMENT_SCHEDULE, bookingScheduleId, CalendarEvent.EventType.INSTALMENT_DUE))
                .as("the auto-projected calendar event should exist before the instalment is paid")
                .isPresent();

        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), IndianTime.today(),
                PaymentRecord.Mode.CASH, null, null, null));

        assertThat(calendarEventRepository.findBySourceEntityTypeAndSourceEntityIdAndEventTypeAndDeletedAtIsNull(
                CalendarEvent.SourceEntityType.PAYMENT_SCHEDULE, bookingScheduleId, CalendarEvent.EventType.INSTALMENT_DUE))
                .as("a fully-paid instalment's calendar event must be removed, not left showing as still due")
                .isEmpty();
    }

    @Test
    void chequeBounceAutoCreatesAReversalThatUnwindsTheAllocation() {
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));

        var payment = paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000),
                IndianTime.today(), PaymentRecord.Mode.CHEQUE, "CHQ1", null, null));

        paymentService.updateChequeStatus(payment.id(), new ChequeStatusRequest(PaymentRecord.ChequeStatus.BOUNCED));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);

        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(schedule.get(0).getStatus().name()).isEqualTo("PENDING");
        assertThat(schedule.get(0).getAmountAllocated()).isEqualByComparingTo(BigDecimal.ZERO);

        var payments = paymentService.listForSale(sale.id());
        assertThat(payments).hasSize(2); // original + reversal
        assertThat(payments).anyMatch(p -> p.reversesPaymentId() != null);
    }

    @Test
    void waivingTheUncollectedPortionOfAPartiallyPaidInstalmentReducesBalanceDue() {
        // Real bug found by a user: plot_sale.balance_due was
        // GENERATED ALWAYS AS (deal_value - total_paid) with no term at all
        // for waived-but-never-collected amounts, so waiving the remainder
        // of a partially-paid instalment stopped it being chased on the
        // calendar/Tracker but left the sale's own "Balance Remaining"
        // figure counting that same money as still owed -- exactly
        // contradicting what waiving is supposed to mean.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));

        // Booking (500,000, due today) paid in full via auto-allocation.
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), IndianTime.today(),
                PaymentRecord.Mode.UPI, "UTR1", null, null));

        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        UUID onAgreementId = schedule.get(1).getId(); // 1,500,000 expected

        // Only 500,000 of the 1,500,000 "On agreement" instalment ever gets
        // paid -- explicitly allocated so it doesn't spill into the third row.
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), IndianTime.today(),
                PaymentRecord.Mode.CASH, null, null,
                List.of(new com.shardeya.builder.payment.dto.AllocationRequest(onAgreementId, BigDecimal.valueOf(500_000)))));

        // The builder forgives the remaining 1,000,000 shortfall on that row.
        scheduleService.waive(onAgreementId, new WaiveRequest("Goodwill discount agreed with buyer"));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000)); // 500k + 500k, real money only
        assertThat(summary.totalWaived()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000)); // 1,500,000 - 500,000 allocated
        // 4,200,000 - 1,000,000 paid - 1,000,000 waived = 2,200,000, exactly
        // the untouched third ("On registry") row -- not 3,200,000, which is
        // what the bug's old (deal_value - total_paid)-only formula produced.
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.valueOf(2_200_000));
    }

    @Test
    void cancellingASaleRestoresThePlotAndRetainsPaymentHistory() {
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), IndianTime.today(),
                PaymentRecord.Mode.UPI, "UTR1", null, null));

        plotSaleService.cancel(sale.id(), new CancelSaleRequest("Buyer backed out", "Refund pending"));

        Plot plot = plotRepository.findByIdAndDeletedAtIsNull(plotId).orElseThrow();
        assertThat(plot.getStatus()).isEqualTo(Plot.Status.AVAILABLE);
        assertThat(plot.getCurrentSaleId()).isNull();

        assertThat(paymentService.listForSale(sale.id())).hasSize(1); // payment retained, not deleted
        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(schedule.get(0).getStatus().name()).isEqualTo("PAID"); // already-paid row untouched
        assertThat(schedule.get(1).getStatus().name()).isEqualTo("WAIVED"); // unpaid rows waived

        // PlotSaleService.cancel() waives every non-PAID row in full (via the
        // exact same status-flip ScheduleService.waive() itself uses, which
        // is what V5_005/V5_006's total_waived trigger fires on) -- so a
        // cancelled sale's own balance should always land at exactly zero,
        // never a leftover "still owed" figure for a deal that's dead.
        var summary = paymentService.summary(sale.id());
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void waivingARowWithNoPaymentsAtAllRemovesItsFullAmountFromBalanceDue() {
        // The partially-paid case above already covers waiving a row with
        // some real allocation on it -- this covers the other real shape:
        // waiving a row nobody has paid anything toward yet (the far more
        // common "builder just forgives the whole last instalment"
        // scenario the earlier chat conversation used as its own example).
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        UUID onRegistryId = schedule.get(2).getId(); // 2,200,000 expected, zero allocated

        scheduleService.waive(onRegistryId, new WaiveRequest("Forgiving the final instalment as a goodwill gesture"));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.totalWaived()).isEqualByComparingTo(BigDecimal.valueOf(2_200_000));
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000)); // Booking + On agreement, untouched
    }

    @Test
    void aSaleWithItsFinalInstalmentWaivedRatherThanPaidCanStillBeMarkedComplete() {
        // Real bug found by a user: PlotSaleService.complete() gated on
        // "totalPaid >= dealValue" directly -- a check written before
        // total_waived existed (see the M5 section above) and never
        // updated to account for it. A sale with its last instalment
        // waived (not literally paid) had totalPaid permanently short of
        // dealValue, so "Mark Complete" 400'd with SALE_NOT_FULLY_PAID
        // forever, even though balanceDue was already correctly zero.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());

        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), IndianTime.today(),
                PaymentRecord.Mode.UPI, "UTR1", null,
                List.of(new com.shardeya.builder.payment.dto.AllocationRequest(schedule.get(0).getId(), BigDecimal.valueOf(500_000)))));
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(1_500_000), IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT2", null,
                List.of(new com.shardeya.builder.payment.dto.AllocationRequest(schedule.get(1).getId(), BigDecimal.valueOf(1_500_000)))));
        scheduleService.waive(schedule.get(2).getId(), new WaiveRequest("Forgiving the final instalment as a goodwill gesture"));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.ZERO);

        var completed = plotSaleService.complete(sale.id());
        assertThat(completed.status()).isEqualTo("COMPLETED");
    }

    @Test
    void completingASaleThatIsGenuinelyNotFullyPaidIsStillRejected() {
        // The fix above must not turn complete() into a no-op check --
        // a sale with real, un-waived, unpaid balance remaining must
        // still be blocked.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000), IndianTime.today(),
                PaymentRecord.Mode.UPI, "UTR1", null, null));

        assertThatThrownBy(() -> plotSaleService.complete(sale.id()))
                .isInstanceOf(com.shardeya.platform.BadRequestException.class);
    }

    @Test
    void lumpSumSalePaidInFullLandsAtExactlyZeroBalance() {
        LocalDate today = IndianTime.today();
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, new SaleCreateRequest(null, "Anita Sharma", "9876543213",
                null, null, null, null, today, BigDecimal.valueOf(3_750_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(3_750_000), today)), null));

        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(3_750_000), today,
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-1", null, null));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.valueOf(3_750_000));
        assertThat(summary.totalWaived()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.ZERO);

        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).getStatus().name()).isEqualTo("PAID");
    }

    @Test
    void clearingAChequeIsJustAStatusFlipWithNoEffectOnAmounts() {
        // Contrast with BOUNCED (already covered above): CLEARED must never
        // trigger a reversal or touch total_paid/allocations at all.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        var payment = paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000),
                IndianTime.today(), PaymentRecord.Mode.CHEQUE, "CHQ99", null, null));

        var cleared = paymentService.updateChequeStatus(payment.id(),
                new com.shardeya.builder.payment.dto.ChequeStatusRequest(PaymentRecord.ChequeStatus.CLEARED));
        assertThat(cleared.chequeStatus()).isEqualTo("CLEARED");

        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.valueOf(500_000)); // unchanged, no reversal
        assertThat(paymentService.listForSale(sale.id())).hasSize(1); // no reversal record created
    }

    @Test
    void oneMultiRowAllocationSplitsAcrossTwoScheduleRowsCorrectly() {
        // Every other test allocates a payment to a single schedule row --
        // this is the actual split-payment shape a large one-time payment
        // covering more than one instalment at once produces.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        var schedule = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        UUID bookingId = schedule.get(0).getId(); // 500,000
        UUID onAgreementId = schedule.get(1).getId(); // 1,500,000

        paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(2_000_000), IndianTime.today(),
                PaymentRecord.Mode.BANK_TRANSFER, "NEFT-SPLIT", null,
                List.of(new com.shardeya.builder.payment.dto.AllocationRequest(bookingId, BigDecimal.valueOf(500_000)),
                        new com.shardeya.builder.payment.dto.AllocationRequest(onAgreementId, BigDecimal.valueOf(1_500_000)))));

        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
        assertThat(summary.balanceDue()).isEqualByComparingTo(BigDecimal.valueOf(2_200_000)); // "On registry" untouched

        var refreshed = scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(sale.id());
        assertThat(refreshed.get(0).getStatus().name()).isEqualTo("PAID");
        assertThat(refreshed.get(1).getStatus().name()).isEqualTo("PAID");
        assertThat(refreshed.get(2).getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void reversingAnAlreadyReversedPaymentIsRejected() {
        // Found via manual exploration: nothing stopped PaymentService.reverse()
        // from being called twice on the same original payment -- the second
        // call created a second negative payment_record + a second set of
        // negated allocations mirroring the SAME original allocations,
        // double-subtracting from amount_allocated/total_paid instead of
        // being a no-op or an error.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        var payment = paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000),
                IndianTime.today(), PaymentRecord.Mode.UPI, "UTR1", null, null));

        paymentService.reverse(payment.id(), new com.shardeya.builder.payment.dto.ReverseRequest("Recorded against the wrong buyer"));

        assertThatThrownBy(() -> paymentService.reverse(payment.id(),
                new com.shardeya.builder.payment.dto.ReverseRequest("Trying to reverse it again")))
                .isInstanceOf(com.shardeya.platform.BadRequestException.class);

        // Exactly one reversal exists -- the rejected second attempt left no trace.
        assertThat(paymentService.listForSale(sale.id())).hasSize(2); // original + one reversal
        var summary = paymentService.summary(sale.id());
        assertThat(summary.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void markingAReversalRecordsChequeAsBouncedMustNotCreateAReversalOfAReversal() {
        // doReverse() reuses PaymentRecord's own constructor, which defaults
        // chequeStatus to PENDING for ANY mode=CHEQUE row -- including the
        // reversal record itself when the original payment was a cheque.
        // That leaves a reversal entry that itself looks like an actionable
        // pending cheque. updateChequeStatus() never checked whether the
        // payment it was given IS a reversal (reversesPaymentId != null)
        // before calling doReverse() on it again -- so "bouncing" a
        // reversal's cheque status would create a reversal-OF-the-reversal:
        // a brand new POSITIVE payment_record with allocations that
        // re-negate the (already negative) reversal allocations, silently
        // re-crediting money that had legitimately been reversed out.
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        var payment = paymentService.record(sale.id(), new PaymentCreateRequest(BigDecimal.valueOf(500_000),
                IndianTime.today(), PaymentRecord.Mode.CHEQUE, "CHQ1", null, null));

        paymentService.updateChequeStatus(payment.id(),
                new com.shardeya.builder.payment.dto.ChequeStatusRequest(PaymentRecord.ChequeStatus.BOUNCED));
        var afterBounce = paymentService.summary(sale.id());
        assertThat(afterBounce.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);

        var payments = paymentService.listForSale(sale.id());
        var reversalRecord = payments.stream().filter(p -> p.reversesPaymentId() != null).findFirst().orElseThrow();

        assertThatThrownBy(() -> paymentService.updateChequeStatus(reversalRecord.id(),
                new com.shardeya.builder.payment.dto.ChequeStatusRequest(PaymentRecord.ChequeStatus.BOUNCED)))
                .isInstanceOf(com.shardeya.platform.BadRequestException.class);

        // Money must NOT have been silently re-credited.
        var afterAttempt = paymentService.summary(sale.id());
        assertThat(afterAttempt.totalPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(paymentService.listForSale(sale.id())).hasSize(2); // original + one reversal, nothing more
    }

    // Buyers aren't users -- no OTP-confirmed opt-in flow is reachable for
    // them -- so ticking the wizard's consent checkbox is what creates a
    // BUILDER_CAPTURED whatsapp_optin row, distinct in the data from a
    // staff member's own SELF_SERVICE row (see BuyerWhatsAppOptInService's
    // own javadoc).
    @Test
    void tickingBuyerConsentAtSaleCreationRecordsABuilderCapturedOptIn() {
        UUID plotId = seedOrgProjectAndPlot();
        LocalDate today = IndianTime.today();
        SaleCreateRequest req = new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, BigDecimal.valueOf(4_200_000), null, null, null, null,
                PlotSale.PaymentType.LUMP_SUM,
                List.of(new ScheduleRowRequest("Full payment", BigDecimal.valueOf(4_200_000), today)),
                null, true);

        SaleResponse sale = plotSaleService.create(plotId, req);
        assertThat(sale.buyerWhatsappOptedIn()).isTrue();

        // Still bound to this test's own org (seedOrgProjectAndPlot's own
        // TestTenantContext.bind, not cleared until @AfterEach) -- findAll()
        // is therefore already correctly RLS-scoped to just this one org.
        var optin = whatsAppOptinRepository.findAll().stream()
                .filter(o -> o.getMobile().equals("9876543210")).findFirst().orElseThrow();
        assertThat(optin.getSource()).isEqualTo("BUILDER_CAPTURED");
        assertThat(optin.getCapturedBy()).isNotNull();
        assertThat(optin.isActive()).isTrue();
    }

    // The wizard checkbox is only ONE of the two ways consent is captured --
    // a buyer may agree (or withdraw) any time after the sale exists too.
    @Test
    void buyerWhatsappConsentCanBeToggledAfterTheSaleAlreadyExists() {
        UUID plotId = seedOrgProjectAndPlot();
        SaleResponse sale = plotSaleService.create(plotId, saleRequest(BigDecimal.valueOf(4_200_000)));
        assertThat(sale.buyerWhatsappOptedIn()).as("no consent captured at creation this time").isFalse();

        SaleResponse optedIn = plotSaleService.setBuyerWhatsAppOptIn(sale.id(), true);
        assertThat(optedIn.buyerWhatsappOptedIn()).isTrue();

        SaleResponse optedOut = plotSaleService.setBuyerWhatsAppOptIn(sale.id(), false);
        assertThat(optedOut.buyerWhatsappOptedIn()).isFalse();
    }

    private SaleCreateRequest saleRequest(BigDecimal dealValue) {
        // Dates anchored to IndianTime.today() rather than hardcoded literals
        // -- a fixed past date eventually becomes "overdue" relative to
        // whenever the test actually runs, which is exactly what caught a
        // (test-only, not product) bug here: the "On agreement" instalment
        // stayed unpaid past a hardcoded due date and the overdue-detecting
        // trigger correctly flagged it OVERDUE instead of the PENDING this
        // test originally assumed. Booking is due today (paid same-day in
        // every test); the other two are safely in the future so they stay
        // PENDING until a test explicitly pays or reverses them.
        LocalDate today = IndianTime.today();
        return new SaleCreateRequest(null, "Rajesh Kumar", "9876543210", null, null, null, null,
                today, dealValue, null, null, null, null,
                PlotSale.PaymentType.INSTALMENT,
                List.of(new ScheduleRowRequest("Booking", BigDecimal.valueOf(500_000), today),
                        new ScheduleRowRequest("On agreement", BigDecimal.valueOf(1_500_000), today.plusMonths(2)),
                        new ScheduleRowRequest("On registry", dealValue.subtract(BigDecimal.valueOf(2_000_000)), today.plusMonths(5))),
                null);
    }

    private UUID seedOrgProjectAndPlot() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        TestTenantContext.bind(orgId, userId, "BUILDER", "BUILDER_ADMIN", Set.of());

        Organization org = new Organization(orgId, Organization.Type.BUILDER, "Sale Test Org", "Jaipur");
        organizationRepository.saveAndFlush(org);

        // plot_sale.handled_by FKs to app_user -- PlotSaleService.create()
        // defaults it to the bound tenant's own userId when the request
        // doesn't supply one, so that id needs a real row here (unlike
        // EntitlementServiceIntegrationTest, which never touches handled_by
        // and so never needed this). Raw insert against the seeded
        // system BUILDER_ADMIN role, same EntityManager-native-query
        // pattern as EntitlementServiceIntegrationTest uses (a bare
        // JdbcTemplate bean resolves ambiguously against this project's
        // multiple DataSources -- see CLAUDE.md's own @Primary/@Qualifier
        // gotcha from M1).
        // A raw entityManager query (no repository proxy) needs an explicit
        // transaction of its own -- the bind-before-transaction-opens rule
        // is already satisfied (bind happened above, in plain Java), so a
        // plain TransactionTemplate here is enough, same as
        // EntitlementServiceIntegrationTest's own native-query inserts.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UUID roleId = (UUID) entityManager.createNativeQuery(
                            "SELECT id FROM role WHERE code = 'BUILDER_ADMIN' AND org_id IS NULL")
                    .getSingleResult();
            entityManager.createNativeQuery(
                            "INSERT INTO app_user (id, org_id, full_name, mobile, role_id, is_owner) "
                                    + "VALUES (:id, :orgId, 'Sale Test User', :mobile, :roleId, true)")
                    .setParameter("id", userId)
                    .setParameter("orgId", orgId)
                    .setParameter("mobile", "9" + String.valueOf(System.nanoTime()).substring(0, 9))
                    .setParameter("roleId", roleId)
                    .executeUpdate();
        });

        Project project = new Project(projectId, orgId, "Sale Test Project", Project.Type.RESIDENTIAL_PLOT_COLONY,
                "1 Test Rd", "Locality", "Jaipur", "RJ", BigDecimal.TEN, "ACRE", BigDecimal.valueOf(435600), 100);
        projectRepository.saveAndFlush(project);

        Plot plot = new Plot(plotId, orgId, projectId, "A-1", BigDecimal.valueOf(1200), "SQ_FT",
                BigDecimal.valueOf(1200), BigDecimal.valueOf(4_200_000));
        plotRepository.saveAndFlush(plot);

        return plotId;
    }
}
