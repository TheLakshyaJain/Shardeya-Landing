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
 * M-06 §22.3, "Instalment due today" -- In-app + WhatsApp, 09:00 IST.
 * Structurally identical to {@link OverdueScheduleSweeper}'s own per-org
 * bind-then-REQUIRES_NEW pattern (see that class's javadoc for why the
 * ordering matters) -- deliberately its own class rather than a third
 * method bolted onto OverdueScheduleSweeper, since "due today" and
 * "overdue" are genuinely different queries firing at different times for
 * a different purpose (one flips a status, this one never does).
 *
 * <p><b>Dedup, a documented simplification:</b> not Redis-backed (the M-06
 * spec's own suggested {@code (type_code, entity_id, business_date)} Redis
 * key), but the existing {@code payment_schedule.last_reminder_sent_at}
 * column -- the same column {@code TrackerService}'s manual buyer-facing
 * "Send Reminder" feature already writes to. A manual reminder sent the
 * same morning this scheduler would otherwise fire suppresses this one
 * (and vice versa, see {@link OverdueScheduleSweeper}'s own reminder sweep)
 * -- a deliberate, disclosed approximation rather than new Redis
 * infrastructure for a twice-daily cron; the two notions ("someone was
 * just reminded about this instalment") are close enough in practice for
 * this scheduler's own idempotency purpose (this cron only ever runs once
 * per day, so same-day re-entrancy after a crash/restart is the actual
 * thing being guarded against, not cross-feature precision).
 */
@Component
public class InstalmentDueTodayScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstalmentDueTodayScheduler.class);
    private static final UUID SYSTEM_ACTOR_ID = new UUID(0, 0);

    private final OrganizationRepository organizationRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final InstalmentReminderParams reminderParams;
    private final OutboxService outboxService;
    private final TenantContextBinder tenantContextBinder;
    private final TransactionTemplate perOrgTransaction;

    public InstalmentDueTodayScheduler(OrganizationRepository organizationRepository, PaymentScheduleRepository scheduleRepository,
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

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
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
                List<PaymentSchedule> dueToday = scheduleRepository.findDueToday(IndianTime.today(),
                        Set.of(PaymentSchedule.Status.PENDING, PaymentSchedule.Status.PARTIALLY_PAID));
                int notified = 0;
                for (PaymentSchedule schedule : dueToday) {
                    if (!schedule.isReminderEnabled()) {
                        continue;
                    }
                    // Same-day re-entrancy guard (a crash/restart re-running
                    // this cron, or a manual re-trigger) -- findDueToday()
                    // alone has no way to tell "already notified today"
                    // from "never notified", since neither due_date nor
                    // status changes as a side effect of sending this
                    // reminder (unlike the overdue-transition sweep, which
                    // naturally self-dedupes by flipping status).
                    Instant last = schedule.getLastReminderSentAt();
                    if (last != null && !last.isBefore(IndianTime.today().atStartOfDay(IndianTime.ZONE).toInstant())) {
                        continue;
                    }
                    outboxService.enqueueNotification(orgId, "INSTALMENT_DUE_TODAY", "notification.instalmentDueToday", null,
                            reminderParams.build(schedule), "payment_schedule", schedule.getId());
                    schedule.setLastReminderSentAt(Instant.now());
                    scheduleRepository.save(schedule);
                    notified++;
                }
                return notified;
            });
        } catch (Exception e) {
            log.warn("Instalment-due-today sweep failed for org {}", orgId, e);
            return 0;
        } finally {
            tenantContextBinder.clear();
        }
    }
}
