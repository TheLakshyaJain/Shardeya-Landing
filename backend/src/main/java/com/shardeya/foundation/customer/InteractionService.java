package com.shardeya.foundation.customer;

import com.shardeya.foundation.customer.dto.InteractionAmendRequest;
import com.shardeya.foundation.customer.dto.InteractionCreateRequest;
import com.shardeya.foundation.customer.dto.InteractionResponse;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M-12 §7/§9 the append-only follow-up log. Deletion is blocked at the DB
 * level (V4_004's RULE) -- this class never even attempts a delete; the only
 * correction path is {@link #amend}, gated to a 15-minute grace window.
 */
@Service
public class InteractionService {

    private static final Duration AMENDMENT_WINDOW = Duration.ofMinutes(15);

    private final InteractionRepository repository;
    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final TenantContextBinder tenantContextBinder;
    private final ProjectAccessGuard accessGuard;
    private final EntityManager entityManager;

    public InteractionService(InteractionRepository repository, CustomerService customerService,
                               CustomerRepository customerRepository, TenantContextBinder tenantContextBinder,
                               ProjectAccessGuard accessGuard, EntityManager entityManager) {
        this.repository = repository;
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.accessGuard = accessGuard;
        this.entityManager = entityManager;
    }

    @Transactional
    public InteractionResponse create(UUID customerId, InteractionCreateRequest req) {
        Customer customer = customerService.requireVisible(customerId);
        if (req.projectId() != null) {
            accessGuard.assertAccess(req.projectId());
        }
        // Bean Validation can't compare against "today" in IST (CLAUDE.md
        // rule #11 -- @PastOrPresent validates against the JVM's default
        // zone, not guaranteed IST), same reason SaleCreateRequest's
        // purchaseDate is range-checked in the service instead.
        if (req.occurredOn().isAfter(com.shardeya.shared.IndianTime.today())) {
            throw new BadRequestException("occurredOn", "OCCURRED_ON_FUTURE", "error.interaction.occurredOnFuture");
        }

        UUID actorId = tenantContextBinder.current().userId();
        Interaction interaction = new Interaction(UUID.randomUUID(), customer.getOrgId(), customerId, req.occurredOn(),
                req.type(), req.remarks(), actorId);
        interaction.setPropertyId(req.propertyId());
        interaction.setProjectId(req.projectId());
        interaction.setPlotId(req.plotId());
        interaction.setNextFollowUpDate(req.nextFollowUpDate());
        interaction.setResult(req.result());
        interaction.setCreatedBy(actorId);
        interaction = repository.save(interaction);
        // Interaction.id is assigned in Java (UUID.randomUUID(), no
        // @GeneratedValue), so Spring Data's isNew() check always sees a
        // non-null id and routes save() through merge() instead of
        // persist() -- the exact bug class CLAUDE.md's M2 notes already
        // document for Plot/PlotSale ("the object passed in as the argument
        // stays detached forever... entity = repository.save(entity)"). That
        // fix alone (reassigning the returned reference, already done above)
        // is necessary but not sufficient here: @CreationTimestamp's value
        // isn't reliably visible on the object merge() returns without an
        // explicit flush+refresh, so createdAt was still null the moment
        // isAmendable() read it a few lines down -- a NullPointerException
        // inside Duration.between(), not a Hibernate error, which is what
        // made this one slower to place than the identical-shaped bugs
        // elsewhere in this codebase.
        entityManager.flush();
        entityManager.refresh(interaction);

        // M-12 §7: "Adding an interaction with a nextFollowUpDate writes back
        // to customer.follow_up_date and upserts the calendar projection."
        //
        // Real bug, found live: "Mark Done" (TrackerService.markDone) sends
        // result=NO_FURTHER with nextFollowUpDate=null -- there's no date to
        // set when a follow-up is genuinely finished. The noFurtherFollowUp
        // flip used to be nested INSIDE the `nextFollowUpDate() != null`
        // branch, so it never ran for exactly this shape: the interaction
        // logged correctly, but customer.no_further_follow_up stayed false
        // and follow_up_date stayed unchanged, so the same lead just
        // reappeared in the follow-ups list immediately after the mutation's
        // own refetch -- indistinguishable from the button silently doing
        // nothing. The two writes are independent conditions now: a new date
        // is set when one was given; noFurtherFollowUp flips whenever the
        // result says so, regardless of whether a date came with it.
        customer.setLastInteractionAt(Instant.now());
        boolean followUpChanged = false;
        if (req.nextFollowUpDate() != null && !req.nextFollowUpDate().equals(customer.getFollowUpDate())) {
            customer.setFollowUpDate(req.nextFollowUpDate());
            followUpChanged = true;
        }
        if (req.result() == Interaction.Result.NO_FURTHER && !customer.isNoFurtherFollowUp()) {
            customer.setNoFurtherFollowUp(true);
            followUpChanged = true;
        }
        customerRepository.save(customer);
        if (followUpChanged) {
            customerService.syncFollowUpProjection(customer);
        }

        return InteractionResponse.from(interaction, isAmendable(interaction));
    }

    public List<InteractionResponse> list(UUID customerId, int limit) {
        customerService.requireVisible(customerId);
        return repository.findByCustomerIdOrderByOccurredOnDescCreatedAtDesc(customerId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream().map(i -> InteractionResponse.from(i, isAmendable(i))).toList();
    }

    @Transactional
    public InteractionResponse amend(UUID interactionId, InteractionAmendRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        Interaction interaction = repository.findByIdAndOrgId(interactionId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        customerService.requireVisible(interaction.getCustomerId());

        if (!isAmendable(interaction)) {
            throw new ForbiddenException("error.interaction.amendmentWindowExpired");
        }

        TenantContext.Tenant tenant = tenantContextBinder.current();
        boolean isConductor = tenant.userId().equals(interaction.getConductedBy());
        boolean canEditAll = tenant.permissions().contains("DATA_EDIT_ALL");
        if (!isConductor && !canEditAll) {
            throw new ForbiddenException("error.interaction.amendmentNotOwner");
        }

        // Only the FIRST amendment captures the original -- a second amendment
        // within the same window must not overwrite that trail with an
        // already-amended value.
        if (interaction.getOriginalRemarks() == null) {
            interaction.setOriginalRemarks(interaction.getRemarks());
        }
        interaction.setRemarks(req.remarks());
        interaction.setAmendedAt(Instant.now());
        interaction.setAmendedBy(tenant.userId());
        repository.save(interaction);
        return InteractionResponse.from(interaction, true);
    }

    private boolean isAmendable(Interaction interaction) {
        return Duration.between(interaction.getCreatedAt(), Instant.now()).compareTo(AMENDMENT_WINDOW) <= 0;
    }
}
