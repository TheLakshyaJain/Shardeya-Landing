package com.shardeya.builder.payment;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * B-05 §7: "A nightly job flips PENDING/PARTIALLY_PAID to OVERDUE when
 * due_date &lt; today." {@code organization} has no org_id/RLS of its own
 * (it IS the tenant boundary), so it's the one table this can safely list
 * across every tenant without first binding any context; payment_schedule
 * itself is RLS-protected, so each org's own sweep still needs its own
 * bound context and its own transaction -- same per-org bind-then-
 * REQUIRES_NEW pattern as OutboxPoller's notification dispatch, see that
 * class's own comment for why the ordering matters.
 *
 * <p><b>M-06 second half</b> adds a second, separate cron
 * ({@link #scheduledReminderSweep()}) implementing §22.3's own reminder
 * cadence for an instalment that's ALREADY overdue -- "day 3, then
 * weekly" -- distinct from the status-transition sweep above, which only
 * ever notifies once, on the day a schedule first becomes OVERDUE (day 1).
 * Reuses {@code payment_schedule.last_reminder_sent_at} for idempotency,
 * same documented simplification as {@link InstalmentDueTodayScheduler}'s
 * own javadoc explains (shared with the manual buyer-reminder feature and
 * with that scheduler -- not Redis-backed).
 */
@Component
public class OverdueScheduleSweeper {

    private static final Logger log = LoggerFactory.getLogger(OverdueScheduleSweeper.class);
    private static final UUID SYSTEM_ACTOR_ID = new UUID(0, 0);
    private static final int REMINDER_START_DAYS = 3;
    private static final int REMINDER_REPEAT_DAYS = 7;

    private final OrganizationRepository organizationRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final InstalmentReminderParams reminderParams;
    private final OutboxService outboxService;
    private final TenantContextBinder tenantContextBinder;
    private final TransactionTemplate perOrgTransaction;

    public OverdueScheduleSweeper(OrganizationRepository organizationRepository, PaymentScheduleRepository scheduleRepository,
                                   InstalmentReminderParams reminderParams, OutboxService outboxService,
                                   TenantContextBinder tenantContextBinder, PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.scheduleRepository = scheduleRepository;
        this.reminderParams = reminderParams;
        this.outboxService = outboxService;
        this.tenantContextBinder = tenantContextBinder;
        this.perOrgTransaction = new TransactionTemplate(transactionManager);
        this.perOrgTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 01:00 IST daily -- matches the spec's "nightly job" framing. Callable
    // directly (sweepAllOrgs()) for tests/manual triggering without waiting
    // for the cron.
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
    public void scheduledSweep() {
        sweepAllOrgs();
    }

    public int sweepAllOrgs() {
        int total = 0;
        for (Organization org : organizationRepository.findAll()) {
            total += sweepOrg(org.getId());
        }
        return total;
    }

    private int sweepOrg(UUID orgId) {
        tenantContextBinder.bindNewOrgContext(orgId, SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            return perOrgTransaction.execute(status -> {
                List<PaymentSchedule> newlyOverdue = scheduleRepository.findNewlyOverdue(IndianTime.today(),
                        Set.of(PaymentSchedule.Status.PENDING, PaymentSchedule.Status.PARTIALLY_PAID));
                for (PaymentSchedule schedule : newlyOverdue) {
                    schedule.setStatus(PaymentSchedule.Status.OVERDUE);
                    scheduleRepository.save(schedule);
                    outboxService.enqueueNotification(orgId, "INSTALMENT_OVERDUE", "notification.instalmentOverdue", null,
                            reminderParams.build(schedule), "payment_schedule", schedule.getId());
                }
                return newlyOverdue.size();
            });
        } catch (Exception e) {
            log.warn("Overdue sweep failed for org {}", orgId, e);
            return 0;
        } finally {
            tenantContextBinder.clear();
        }
    }

    // 09:00 IST daily, same slot as InstalmentDueTodayScheduler -- the
    // status-transition sweep above stays at 01:00 (it needs to run before
    // the day's business starts so OVERDUE is accurate all day); this one
    // is a human-facing reminder, so it belongs in the same 09:00 window
    // §22.3 names for every other builder-facing reminder.
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void scheduledReminderSweep() {
        remindOverdueAllOrgs();
    }

    public int remindOverdueAllOrgs() {
        int total = 0;
        for (Organization org : organizationRepository.findAll()) {
            total += remindOverdueOrg(org.getId());
        }
        return total;
    }

    private int remindOverdueOrg(UUID orgId) {
        tenantContextBinder.bindNewOrgContext(orgId, SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            return perOrgTransaction.execute(status -> {
                Instant weeklyCutoff = Instant.now().minusSeconds(REMINDER_REPEAT_DAYS * 24L * 3600);
                List<PaymentSchedule> candidates = scheduleRepository.findOverdueDueOnOrBefore(
                        IndianTime.today().minusDays(REMINDER_START_DAYS), PaymentSchedule.Status.OVERDUE);
                int reminded = 0;
                for (PaymentSchedule schedule : candidates) {
                    if (!schedule.isReminderEnabled()) {
                        continue;
                    }
                    Instant last = schedule.getLastReminderSentAt();
                    if (last != null && last.isAfter(weeklyCutoff)) {
                        continue;
                    }
                    outboxService.enqueueNotification(orgId, "INSTALMENT_OVERDUE", "notification.instalmentOverdue", null,
                            reminderParams.build(schedule), "payment_schedule", schedule.getId());
                    schedule.setLastReminderSentAt(Instant.now());
                    scheduleRepository.save(schedule);
                    reminded++;
                }
                return reminded;
            });
        } catch (Exception e) {
            log.warn("Overdue-reminder sweep failed for org {}", orgId, e);
            return 0;
        } finally {
            tenantContextBinder.clear();
        }
    }
}
