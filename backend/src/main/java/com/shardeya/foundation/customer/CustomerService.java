package com.shardeya.foundation.customer;

import com.shardeya.builder.plot.Plot;
import com.shardeya.builder.plot.PlotRepository;
import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.foundation.calendar.CalendarEvent;
import com.shardeya.foundation.calendar.CalendarService;
import com.shardeya.foundation.customer.dto.CustomerCreateRequest;
import com.shardeya.foundation.customer.dto.CustomerResponse;
import com.shardeya.foundation.customer.dto.CustomerUpdateRequest;
import com.shardeya.foundation.customer.dto.FunnelResponse;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.Cursor;
import com.shardeya.platform.CursorPage;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContext;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M-12 Customer / Lead Core, builder-shaped (B-07). No separate broker-facing
 * service/split exists yet -- there is no broker consumer to share this with
 * in this milestone (broker's own customer manager is BR-04, not built here);
 * split when that milestone actually needs it, rather than build the seam
 * speculatively now.
 */
@Service
public class CustomerService {

    private static final Set<String> NO_LEAD_HANDLING_ROLES = Set.of("ACCOUNTS_STAFF", "VIEW_ONLY");
    private static final List<Customer.Status> TERMINAL_STATUSES = List.of(Customer.Status.DEAL_CLOSED, Customer.Status.LOST);

    private final CustomerRepository repository;
    private final InteractionRepository interactionRepository;
    private final AppUserRepository appUserRepository;
    private final PlotRepository plotRepository;
    private final TenantContextBinder tenantContextBinder;
    private final ProjectAccessGuard accessGuard;
    private final CalendarService calendarService;
    private final OutboxService outboxService;
    private final EntityManager entityManager;

    public CustomerService(CustomerRepository repository, InteractionRepository interactionRepository,
                            AppUserRepository appUserRepository, PlotRepository plotRepository,
                            TenantContextBinder tenantContextBinder, ProjectAccessGuard accessGuard,
                            CalendarService calendarService, OutboxService outboxService, EntityManager entityManager) {
        this.repository = repository;
        this.interactionRepository = interactionRepository;
        this.appUserRepository = appUserRepository;
        this.plotRepository = plotRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.accessGuard = accessGuard;
        this.calendarService = calendarService;
        this.outboxService = outboxService;
        this.entityManager = entityManager;
    }

    @Transactional
    public CustomerResponse create(CustomerCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        TenantContext.Tenant tenant = tenantContextBinder.current();

        if (req.budgetMax().compareTo(req.budgetMin()) < 0) {
            throw new BadRequestException("budgetMax", "BUDGET_RANGE_INVALID", "error.customer.budgetRangeInvalid");
        }
        if (req.interestedProjectId() != null) {
            accessGuard.assertAccess(req.interestedProjectId());
        }
        validatePlotBelongsToProject(req.interestedProjectId(), req.interestedPlotId());

        // M-12 §7 duplicate detection: "A customer with this number already
        // exists" -- Open existing / Create anyway / Merge. Only the first
        // two are implemented; Merge is a documented future-scalability item
        // (M-12 §13 "Merge duplicates UI"), not built here.
        if (!req.allowDuplicate() && repository.existsByOrgIdAndMobileAndDeletedAtIsNull(orgId, req.mobile())) {
            Customer existing = repository.findFirstByOrgIdAndMobileAndDeletedAtIsNull(orgId, req.mobile()).orElseThrow();
            throw new ConflictException("error.customer.duplicateMobile", Map.of(
                    "name", existing.getFullName(), "status", existing.getStatus().name(), "id", existing.getId().toString()));
        }

        UUID id = UUID.randomUUID();
        Customer customer = new Customer(id, orgId, req.fullName(), req.mobile(), req.budgetMin(), req.budgetMax(), req.source());
        customer.setAlternateMobile(req.alternateMobile());
        customer.setEmail(req.email());
        customer.setPreferredPropertyType(req.preferredPropertyType());
        customer.setPreferredLocality(req.preferredLocality());
        customer.setSizeRequirement(req.sizeRequirement());
        customer.setSourceBrokerId(req.sourceBrokerId());
        if (req.status() != null) {
            customer.setStatus(req.status());
        } else if (req.siteVisitDate() != null) {
            // A site visit date on create is a strong enough signal to skip
            // the default INTERESTED stage -- same reasoning as update()'s
            // auto-advance below, just at creation time. An explicit
            // req.status() always wins over this inference.
            customer.setStatus(Customer.Status.SITE_VISIT_SCHEDULED);
        }
        customer.setInterestedProjectId(req.interestedProjectId());
        customer.setInterestedPlotId(req.interestedPlotId());
        // B-07 §8: "auto-assigned to the creating exec" when no explicit assignee given.
        customer.setAssignedTo(req.assignedTo() != null ? req.assignedTo() : tenant.userId());
        customer.setFollowUpDate(req.followUpDate());
        customer.setSiteVisitDate(req.siteVisitDate());
        customer.setRemarks(req.remarks());
        customer.setCreatedBy(tenant.userId());

        customer = repository.save(customer);
        // Customer.id is assigned in Java (no @GeneratedValue), so save()
        // routes through merge() -- the returned reference's @CreationTimestamp
        // (createdAt) isn't reliably populated without an explicit
        // flush+refresh. Same bug class already found in InteractionService
        // (there it crashed outright via a NullPointerException; here it
        // would have just silently shipped createdAt: null in the API
        // response and corrupted cursor pagination for this row).
        entityManager.flush();
        entityManager.refresh(customer);
        syncFollowUpProjection(customer);
        syncSiteVisitProjection(customer);

        if (customer.getAssignedTo() != null) {
            notifyAssigned(customer);
        }
        outboxService.enqueueNotification(orgId, "LEAD_CREATED", "notification.leadCreated", null,
                Map.of("name", customer.getFullName()), "customer", customer.getId());

        return CustomerResponse.from(customer);
    }

    public CustomerResponse get(UUID id) {
        Customer customer = requireVisible(id);
        return CustomerResponse.from(customer);
    }

    public CursorPage<CustomerResponse> list(String cursorRaw, int limit, UUID projectId, UUID plotId, UUID assignedTo,
                                              Customer.Source source, UUID brokerId, Customer.Status status,
                                              Boolean important, String followUpRange, String search) {
        UUID orgId = tenantContextBinder.currentOrgId();
        TenantContext.Tenant tenant = tenantContextBinder.current();
        assertCanViewAnyLeads(tenant);

        Cursor cursor = Cursor.decode(cursorRaw);
        int fetchSize = Math.min(Math.max(limit, 1), 100) + 1;

        Specification<Customer> spec = Specification
                .where(CustomerSpecifications.orgId(orgId))
                .and(CustomerSpecifications.notDeleted())
                .and(CustomerSpecifications.projectId(projectId))
                .and(CustomerSpecifications.plotId(plotId))
                .and(CustomerSpecifications.assignedTo(assignedTo))
                .and(CustomerSpecifications.source(source))
                .and(CustomerSpecifications.sourceBrokerId(brokerId))
                .and(CustomerSpecifications.status(status))
                .and(CustomerSpecifications.important(important))
                .and(CustomerSpecifications.followUpRange(followUpRange, IndianTime.today()))
                .and(CustomerSpecifications.searchText(search));

        if (!tenant.permissions().contains("DATA_VIEW_ALL")) {
            spec = spec.and(CustomerSpecifications.ownedBy(tenant.userId()));
        }
        if (!tenant.allProjects()) {
            spec = spec.and(CustomerSpecifications.inProjectScope(tenant.projectScope()));
        }
        if (cursor != null) {
            spec = spec.and(CustomerSpecifications.afterCursor(cursor.createdAt(), cursor.id()));
        }

        List<Customer> fetched = repository.findAll(spec,
                PageRequest.of(0, fetchSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).getContent();

        CursorPage<Customer> page = CursorPage.of(fetched, Math.min(Math.max(limit, 1), 100),
                c -> new Cursor(c.getCreatedAt(), c.getId()));
        return new CursorPage<>(page.items().stream().map(CustomerResponse::from).toList(), page.nextCursor(), page.hasMore());
    }

    @Transactional
    public CustomerResponse update(UUID id, CustomerUpdateRequest req) {
        Customer customer = requireEditable(id);

        if (req.interestedProjectId() != null) {
            accessGuard.assertAccess(req.interestedProjectId());
        }
        UUID effectiveProject = req.interestedProjectId() != null ? req.interestedProjectId() : customer.getInterestedProjectId();
        if (req.interestedPlotId() != null) {
            validatePlotBelongsToProject(effectiveProject, req.interestedPlotId());
        }

        if (req.fullName() != null) customer.setFullName(req.fullName());
        if (req.mobile() != null) customer.setMobile(req.mobile());
        if (req.alternateMobile() != null) customer.setAlternateMobile(req.alternateMobile());
        if (req.email() != null) customer.setEmail(req.email());
        if (req.budgetMin() != null) customer.setBudgetMin(req.budgetMin());
        if (req.budgetMax() != null) customer.setBudgetMax(req.budgetMax());
        if (customer.getBudgetMax().compareTo(customer.getBudgetMin()) < 0) {
            throw new BadRequestException("budgetMax", "BUDGET_RANGE_INVALID", "error.customer.budgetRangeInvalid");
        }
        if (req.preferredPropertyType() != null) customer.setPreferredPropertyType(req.preferredPropertyType());
        if (req.preferredLocality() != null) customer.setPreferredLocality(req.preferredLocality());
        if (req.sizeRequirement() != null) customer.setSizeRequirement(req.sizeRequirement());
        if (req.source() != null) customer.setSource(req.source());
        if (req.sourceBrokerId() != null) customer.setSourceBrokerId(req.sourceBrokerId());
        if (req.interestedProjectId() != null) customer.setInterestedProjectId(req.interestedProjectId());
        if (req.interestedPlotId() != null) customer.setInterestedPlotId(req.interestedPlotId());
        if (req.noFurtherFollowUp() != null) customer.setNoFurtherFollowUp(req.noFurtherFollowUp());
        if (req.remarks() != null) customer.setRemarks(req.remarks());

        boolean followUpChanged = req.followUpDate() != null && !req.followUpDate().equals(customer.getFollowUpDate());
        if (req.followUpDate() != null) {
            customer.setFollowUpDate(req.followUpDate());
        }
        boolean siteVisitChanged = req.siteVisitDate() != null && !req.siteVisitDate().equals(customer.getSiteVisitDate());
        if (req.siteVisitDate() != null) {
            customer.setSiteVisitDate(req.siteVisitDate());
        }
        // Setting/changing the site visit date is a strong enough signal on
        // its own to advance the funnel stage -- same reasoning as
        // updateStatus() deriving closedAt as a side effect of a different
        // field. Skipped for terminal statuses so backfilling a historical
        // visit date can't silently reopen a closed/lost lead, and skipped
        // if already SITE_VISIT_SCHEDULED (nothing to advance).
        boolean autoAdvanced = siteVisitChanged && !TERMINAL_STATUSES.contains(customer.getStatus())
                && customer.getStatus() != Customer.Status.SITE_VISIT_SCHEDULED;
        if (autoAdvanced) {
            customer.setStatus(Customer.Status.SITE_VISIT_SCHEDULED);
        }

        customer = repository.save(customer);
        if (followUpChanged || Boolean.TRUE.equals(req.noFurtherFollowUp())) {
            syncFollowUpProjection(customer);
        }
        if (siteVisitChanged) {
            syncSiteVisitProjection(customer);
        }
        if (autoAdvanced) {
            logSystemInteraction(customer, "Status auto-changed to Site Visit Scheduled (site visit date set)");
        }
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void delete(UUID id) {
        Customer customer = requireEditable(id);
        customer.setDeletedAt(Instant.now());
        repository.save(customer);
        calendarService.removeAutoEvent(CalendarEvent.SourceEntityType.CUSTOMER, customer.getId(), CalendarEvent.EventType.FOLLOW_UP);
        calendarService.removeAutoEvent(CalendarEvent.SourceEntityType.CUSTOMER, customer.getId(), CalendarEvent.EventType.SITE_VISIT);
    }

    @Transactional
    public void restore(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        Customer customer = repository.findById(id).filter(c -> c.getOrgId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        customer.setDeletedAt(null);
        repository.save(customer);
        syncFollowUpProjection(customer);
        syncSiteVisitProjection(customer);
    }

    @Transactional
    public CustomerResponse setImportant(UUID id, boolean important) {
        Customer customer = requireEditable(id);
        customer.setImportant(important);
        repository.save(customer);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse updateStatus(UUID id, Customer.Status status, String note) {
        Customer customer = requireEditable(id);
        Customer.Status previous = customer.getStatus();
        customer.setStatus(status);
        if (status == Customer.Status.DEAL_CLOSED || status == Customer.Status.LOST) {
            customer.setClosedAt(IndianTime.today());
        } else {
            customer.setClosedAt(null);
        }
        customer = repository.save(customer);
        // A lead moving into (or out of) a terminal status changes whether its
        // FOLLOW_UP calendar projection should exist at all -- syncFollowUpProjection
        // (which already treats a terminal status as "no further follow-up",
        // same as the customer's own noFurtherFollowUp flag) is the single place
        // that decides that, so any status change must re-run it.
        syncFollowUpProjection(customer);
        syncSiteVisitProjection(customer);

        // M-12 §7: "We record every transition in the interaction log rather
        // than blocking transitions."
        String remarks = "Status changed from " + previous + " to " + status + (note != null && !note.isBlank() ? ": " + note : "");
        logSystemInteraction(customer, remarks);

        if (status == Customer.Status.DEAL_CLOSED) {
            outboxService.enqueueNotification(customer.getOrgId(), "LEAD_DEAL_CLOSED", "notification.dealClosed",
                    null, Map.of("name", customer.getFullName()), "customer", customer.getId());
        }
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse assign(UUID id, UUID userId) {
        Customer customer = requireEditable(id);
        assignInternal(customer, userId);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void bulkAssign(List<UUID> customerIds, UUID userId) {
        for (UUID customerId : customerIds) {
            Customer customer = requireVisible(customerId);
            assignInternal(customer, userId);
        }
    }

    private void assignInternal(Customer customer, UUID userId) {
        UUID orgId = customer.getOrgId();
        AppUser assignee = appUserRepository.findByIdAndOrgIdAndDeletedAtIsNull(userId, orgId)
                .orElseThrow(() -> new BadRequestException("userId", "ASSIGNEE_INVALID", "error.customer.assigneeInvalid"));
        if (assignee.getStatus() != AppUser.Status.ACTIVE || NO_LEAD_HANDLING_ROLES.contains(assignee.getRole().getCode())) {
            throw new BadRequestException("userId", "ASSIGNEE_INVALID", "error.customer.assigneeInvalid");
        }

        UUID previousAssignee = customer.getAssignedTo();
        customer.setAssignedTo(userId);
        repository.save(customer);
        // The FOLLOW_UP calendar event's own assignedTo is a denormalised
        // copy (see upsertAutoEvent) -- reassigning the lead without
        // resyncing it left the event visible to whoever it was PREVIOUSLY
        // assigned to (or, if it started unassigned, visible to everyone),
        // even after the lead itself moved to someone else. Same root cause
        // as updateStatus() needing the same call.
        syncFollowUpProjection(customer);
        syncSiteVisitProjection(customer);

        String remarks = previousAssignee == null ? "Assigned to " + assignee.getFullName()
                : "Reassigned to " + assignee.getFullName();
        logSystemInteraction(customer, remarks);
        notifyAssigned(customer);
    }

    public List<CustomerResponse> unassigned(int limit) {
        assertCanViewAnyLeads(tenantContextBinder.current());
        UUID orgId = tenantContextBinder.currentOrgId();
        return repository.findUnassigned(orgId, TERMINAL_STATUSES, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream().map(CustomerResponse::from).toList();
    }

    public FunnelResponse funnel(UUID projectId) {
        assertCanViewAnyLeads(tenantContextBinder.current());
        UUID orgId = tenantContextBinder.currentOrgId();
        if (projectId != null) {
            accessGuard.assertAccess(projectId);
        }
        return new FunnelResponse(
                repository.countByStatus(orgId, projectId, Customer.Status.INTERESTED),
                repository.countByStatus(orgId, projectId, Customer.Status.SITE_VISIT_SCHEDULED),
                repository.countByStatus(orgId, projectId, Customer.Status.SITE_VISIT_DONE),
                repository.countByStatus(orgId, projectId, Customer.Status.FOLLOWING_UP),
                repository.countByStatus(orgId, projectId, Customer.Status.DEAL_CLOSED),
                repository.countByStatus(orgId, projectId, Customer.Status.LOST));
    }

    public List<CustomerResponse> followUps(String range) {
        UUID orgId = tenantContextBinder.currentOrgId();
        TenantContext.Tenant tenant = tenantContextBinder.current();
        assertCanViewAnyLeads(tenant);
        LocalDate today = IndianTime.today();
        Specification<Customer> spec = Specification
                .where(CustomerSpecifications.orgId(orgId))
                .and(CustomerSpecifications.notDeleted())
                .and(CustomerSpecifications.followUpRange(range, today));
        if (!tenant.permissions().contains("DATA_VIEW_ALL")) {
            spec = spec.and(CustomerSpecifications.ownedBy(tenant.userId()));
        }
        if (!tenant.allProjects()) {
            spec = spec.and(CustomerSpecifications.inProjectScope(tenant.projectScope()));
        }
        return repository.findAll(spec).stream().map(CustomerResponse::from).toList();
    }

    // ------------------------------------------------------------- helpers

    /** Package-visible so InteractionService can reuse the exact same visibility rule. */
    Customer requireVisible(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        Customer customer = repository.findByIdAndOrgIdAndDeletedAtIsNull(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        assertVisible(customer);
        return customer;
    }

    private Customer requireEditable(UUID id) {
        Customer customer = requireVisible(id);
        TenantContext.Tenant tenant = tenantContextBinder.current();
        boolean editAll = tenant.permissions().contains("DATA_EDIT_ALL");
        boolean editOwn = tenant.permissions().contains("DATA_EDIT_OWN")
                && (customer.getAssignedTo() != null && customer.getAssignedTo().equals(tenant.userId())
                    || tenant.userId().equals(customer.getCreatedBy()));
        if (!editAll && !editOwn) {
            // Foreign-org-shaped 404, not 403 -- CLAUDE.md rule #1 extended to
            // "wrong scope, same org" the same way M-02 §10 already does for
            // a reassigned lead ("it vanishes from their list").
            throw new ResourceNotFoundException("error.notFound");
        }
        return customer;
    }

    private void assertVisible(Customer customer) {
        TenantContext.Tenant tenant = tenantContextBinder.current();
        boolean viewAll = tenant.permissions().contains("DATA_VIEW_ALL");
        boolean viewOwn = tenant.permissions().contains("DATA_VIEW_OWN")
                && (customer.getAssignedTo() != null && customer.getAssignedTo().equals(tenant.userId())
                    || tenant.userId().equals(customer.getCreatedBy()));
        if (!viewAll && !viewOwn) {
            throw new ResourceNotFoundException("error.notFound");
        }
        if (customer.getInterestedProjectId() != null && !tenant.allProjects()
                && !tenant.projectScope().contains(customer.getInterestedProjectId())) {
            throw new ResourceNotFoundException("error.notFound");
        }
    }

    /** Accounts Staff holds neither DATA_VIEW_ALL nor DATA_VIEW_OWN (B-07 §9: "no lead access at all") -- rejected outright, not silently emptied. */
    private void assertCanViewAnyLeads(TenantContext.Tenant tenant) {
        if (!tenant.permissions().contains("DATA_VIEW_ALL") && !tenant.permissions().contains("DATA_VIEW_OWN")) {
            throw new ForbiddenException("error.customer.noLeadAccess");
        }
    }

    private void validatePlotBelongsToProject(UUID projectId, UUID plotId) {
        if (plotId == null) {
            return;
        }
        Plot plot = plotRepository.findByIdAndDeletedAtIsNull(plotId)
                .orElseThrow(() -> new BadRequestException("interestedPlotId", "PLOT_NOT_FOUND", "error.customer.plotNotFound"));
        if (projectId == null || !plot.getProjectId().equals(projectId)) {
            throw new BadRequestException("interestedPlotId", "PLOT_PROJECT_MISMATCH", "error.customer.plotProjectMismatch");
        }
    }

    /**
     * Shared by InteractionService and TeamService too -- the calendar
     * projection follows whichever record most recently set the date or the
     * assignee, always upserted, never duplicated (M-11 §7). A lead in a
     * terminal status (DEAL_CLOSED/LOST) is treated the same as
     * noFurtherFollowUp=true -- there is no reason to keep nagging a staff
     * member about a closed lead's calendar event. Public because
     * TeamService.handleReassignment() mutates Customer.assignedTo directly
     * (staff offboarding reassignment) from a different package and needs to
     * trigger the same resync InteractionService/assignInternal() do.
     */
    public void syncFollowUpProjection(Customer customer) {
        boolean terminal = TERMINAL_STATUSES.contains(customer.getStatus());
        LocalDate date = (customer.isNoFurtherFollowUp() || terminal) ? null : customer.getFollowUpDate();
        calendarService.upsertAutoEvent(customer.getOrgId(), CalendarEvent.SourceEntityType.CUSTOMER, customer.getId(),
                CalendarEvent.EventType.FOLLOW_UP, "Follow-up: " + customer.getFullName(), date,
                customer.getId(), customer.getInterestedProjectId(), customer.getInterestedPlotId(), customer.getAssignedTo());
    }

    /**
     * SITE_VISIT was declared in CalendarEvent.EventType, styled in the
     * frontend legend, and given an i18n label, but nothing ever created one
     * -- there was no date field on Customer to source it from at all. Mirrors
     * syncFollowUpProjection exactly (same terminal-status suppression, same
     * call sites), sourced from the new siteVisitDate column instead. Also
     * suppressed on SITE_VISIT_DONE specifically -- once the visit has
     * actually happened there's no reason to keep it on the calendar as an
     * upcoming event, unlike FOLLOW_UP (which stays live through every
     * non-terminal status, since a lead can always need another follow-up).
     */
    public void syncSiteVisitProjection(Customer customer) {
        boolean suppress = TERMINAL_STATUSES.contains(customer.getStatus())
                || customer.getStatus() == Customer.Status.SITE_VISIT_DONE;
        LocalDate date = suppress ? null : customer.getSiteVisitDate();
        calendarService.upsertAutoEvent(customer.getOrgId(), CalendarEvent.SourceEntityType.CUSTOMER, customer.getId(),
                CalendarEvent.EventType.SITE_VISIT, "Site Visit: " + customer.getFullName(), date,
                customer.getId(), customer.getInterestedProjectId(), customer.getInterestedPlotId(), customer.getAssignedTo());
    }

    private void logSystemInteraction(Customer customer, String remarks) {
        UUID actorId = tenantContextBinder.current().userId();
        Interaction interaction = new Interaction(UUID.randomUUID(), customer.getOrgId(), customer.getId(),
                IndianTime.today(), Interaction.Type.NOTE, remarks, actorId);
        interaction.setCreatedBy(actorId);
        interactionRepository.save(interaction);
        customer.setLastInteractionAt(Instant.now());
        repository.save(customer);
    }

    private void notifyAssigned(Customer customer) {
        outboxService.enqueueNotificationForUser(customer.getOrgId(), customer.getAssignedTo(), "LEAD_ASSIGNED",
                "notification.leadAssigned", null,
                Map.of("name", customer.getFullName()), "customer", customer.getId());
    }
}
