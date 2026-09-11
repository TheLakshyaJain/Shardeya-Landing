package com.shardeya.builder.broker;

import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §8, build-order step 6 -- called from
 * PaymentService (record() and, via doReverse(), reverse()/the
 * cheque-bounce path) after every customer payment_record insert. No-op
 * for any sale with no booking_commission rows (a PERCENTAGE/FIXED broker
 * sale, or no broker at all) -- exactly the same "check for rows, no-op if
 * none" shape CommissionLedgerService.cancelForSale() already established.
 *
 * <p>See commission_release's own migration comment (V65_007) for why this
 * inserts a DELTA against the authoritative cumulative-released figure,
 * not a flat per-payment fraction of total_amount -- it's what makes a
 * reversal (itself just another, negative-amount payment_record) correctly
 * "un-release" the right proportional slice with no special-case reversal
 * logic of its own, and what keeps the release total converging exactly to
 * total_amount once a sale is fully paid, with no paise-level drift no
 * matter how many small instalments it's split across.
 */
@Service
public class CommissionReleaseService {

    private static final int MONEY_SCALE = 2;

    private final BookingCommissionRepository bookingCommissionRepository;
    private final CommissionReleaseRepository releaseRepository;
    private final TenantContextBinder tenantContextBinder;

    public CommissionReleaseService(BookingCommissionRepository bookingCommissionRepository,
                                     CommissionReleaseRepository releaseRepository, TenantContextBinder tenantContextBinder) {
        this.bookingCommissionRepository = bookingCommissionRepository;
        this.releaseRepository = releaseRepository;
        this.tenantContextBinder = tenantContextBinder;
    }

    /**
     * §8: "customer pays ¼ of plot value -> ¼ of each commission (seller +
     * every upline + every bonus) is released." {@code sale} must already
     * reflect this payment's effect on {@code totalPaid} (i.e. refreshed
     * after the triggering payment_record insert was flushed) -- the
     * fraction is deliberately computed off the sale's own authoritative
     * cumulative total_paid/deal_value, never off this one payment's amount
     * in isolation, so an unallocated "advance" (PaymentAllocationService's
     * own documented case) still correctly drives releases the same way it
     * already drives plot_sale.total_paid/balance_due.
     */
    @Transactional
    public void releaseForPayment(PlotSale sale, PaymentRecord triggeringPayment) {
        // Locked (PESSIMISTIC_WRITE), and as this transaction's FIRST touch
        // to these rows -- a second, concurrent call for the same sale
        // (two payments racing, or a payment racing its own reversal)
        // blocks here until this transaction fully commits, so its own
        // later sumReleasedFor() read below is guaranteed to see whatever
        // this transaction actually inserted, never a stale zero. See the
        // repository method's own javadoc for the exact bug this prevents
        // (a real CI-only race that inserted two full-amount releases
        // instead of one, converging to double the correct total).
        List<BookingCommission> rows = bookingCommissionRepository.lockByPlotSaleId(sale.getId());
        if (rows.isEmpty()) {
            return;
        }
        if (sale.getDealValue() == null || sale.getDealValue().signum() <= 0) {
            return;
        }

        BigDecimal totalPaid = sale.getTotalPaid() == null ? BigDecimal.ZERO : sale.getTotalPaid();
        BigDecimal fraction = totalPaid.divide(sale.getDealValue(), 6, RoundingMode.HALF_UP);
        if (fraction.compareTo(BigDecimal.ZERO) < 0) {
            fraction = BigDecimal.ZERO;
        } else if (fraction.compareTo(BigDecimal.ONE) > 0) {
            fraction = BigDecimal.ONE;
        }

        UUID createdBy = tenantContextBinder.current().userId();
        for (BookingCommission row : rows) {
            BigDecimal targetReleased = row.getTotalAmount().multiply(fraction).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal releasedSoFar = releaseRepository.sumReleasedFor(row.getId());
            BigDecimal delta = targetReleased.subtract(releasedSoFar);
            if (delta.signum() == 0) {
                // commission_release.amount has a CHECK (amount <> 0) --
                // this is not just an optimisation, a zero delta must never
                // be inserted at all (nothing changed for this beneficiary
                // on this payment, e.g. a zero-amount capped-at-zero row).
                continue;
            }
            CommissionRelease release = new CommissionRelease(UUID.randomUUID(), row.getOrgId(), row.getId(),
                    triggeringPayment.getId(), delta, triggeringPayment.getPaidOn());
            release.setCreatedBy(createdBy);
            releaseRepository.save(release);
        }
    }
}
