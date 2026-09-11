package com.shardeya.builder.tracker;

import com.shardeya.foundation.auth.Organization;
import com.shardeya.foundation.auth.OrganizationRepository;
import com.shardeya.foundation.customer.Customer;
import com.shardeya.foundation.customer.CustomerRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * B-13 §12: "Follow-up due today -> in-app + WhatsApp at 09:00 to the
 * assignee." FOLLOWUP_DUE was seeded as a notification type back in M4
 * (V4_008) but nothing ever fired it -- a known, explicitly documented gap
 * (see CLAUDE.md's Milestone 4 notes) left for the Tracker milestone to
 * close as part of its own real purpose, not as a bolt-on. Mirrors
 * OverdueScheduleSweeper's exact per-org bind-then-REQUIRES_NEW pattern --
 * see that class's own javadoc for why the ordering matters.
 */
@Component
public class FollowUpDueSweeper {

    private static final Logger log = LoggerFactory.getLogger(FollowUpDueSweeper.class);
    private static final UUID SYSTEM_ACTOR_ID = new UUID(0, 0);
    private static final List<Customer.Status> TERMINAL_STATUSES = List.of(Customer.Status.DEAL_CLOSED, Customer.Status.LOST);

    private final OrganizationRepository organizationRepository;
    private final CustomerRepository customerRepository;
    private final OutboxService outboxService;
    private final TenantContextBinder tenantContextBinder;
    private final TransactionTemplate perOrgTransaction;

    public FollowUpDueSweeper(OrganizationRepository organizationRepository, CustomerRepository customerRepository,
                              OutboxService outboxService,
                              TenantContextBinder tenantContextBinder, PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.customerRepository = customerRepository;
        this.outboxService = outboxService;
        this.tenantContextBinder = tenantContextBinder;
        this.perOrgTransaction = new TransactionTemplate(transactionManager);
        this.perOrgTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 09:00 IST daily, per B-13 §12's own stated time. Callable directly
    // (sweepAllOrgs()) for tests/manual triggering without waiting for the cron.
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void scheduledSweep() {
        sweepAllOrgs();
    }

    public int sweepAllOrgs() {
        int total = 0;
        for (Organization org : organizationRepository.findAll()) {
            if (org.getType() == Organization.Type.BUILDER) {
                total += sweepOrg(org.getId());
            }
        }
        return total;
    }

    private int sweepOrg(UUID orgId) {
        tenantContextBinder.bindNewOrgContext(orgId, SYSTEM_ACTOR_ID, "SYSTEM", "SYSTEM", Set.of());
        try {
            return perOrgTransaction.execute(status -> {
                List<Customer> dueToday = customerRepository.findDueForFollowUp(orgId, IndianTime.today(), TERMINAL_STATUSES);
                int sent = 0;
                for (Customer customer : dueToday) {
                    // An unassigned lead due for follow-up has nobody to
                    // notify -- the Unassigned tab is how that gets noticed,
                    // not this sweep.
                    if (customer.getAssignedTo() == null) {
                        continue;
                    }
                    // WhatsApp (to the staff ASSIGNEE's own mobile, not the
                    // lead's -- this reminds the salesperson to make the
                    // call, it isn't a message sent to the customer) used to
                    // be a second, hardcoded enqueueWhatsApp call right here.
                    // M-06 second half: channel fan-out for every
                    // notification type is now decided centrally by
                    // NotificationDispatchService, off notification_type's
                    // own default_channels (FOLLOWUP_DUE -> {IN_APP,WHATSAPP},
                    // V7_017) crossed with the recipient's own preference --
                    // hardcoding WhatsApp here would silently bypass a user
                    // who's disabled it, defeating the whole point of a
                    // preference system existing at all.
                    outboxService.enqueueNotificationForUser(orgId, customer.getAssignedTo(), "FOLLOWUP_DUE",
                            "notification.followupDue", null, Map.of("name", customer.getFullName()),
                            "customer", customer.getId());
                    sent++;
                }
                return sent;
            });
        } catch (Exception e) {
            log.warn("Follow-up-due sweep failed for org {}", orgId, e);
            return 0;
        } finally {
            tenantContextBinder.clear();
        }
    }
}
