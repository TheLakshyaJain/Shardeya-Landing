package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.BrokerInteractionCreateRequest;
import com.shardeya.builder.broker.dto.BrokerInteractionResponse;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** B-14 §20.6 "follow-up history and notes" -- same shape as foundation.customer's Interaction, append-style (no edit/delete endpoints in the API spec). */
@Service
public class BrokerInteractionService {

    private final BrokerInteractionRepository repository;
    private final BrokerPartnerRepository brokerRepository;
    private final TenantContextBinder tenantContextBinder;
    private final EntityManager entityManager;

    public BrokerInteractionService(BrokerInteractionRepository repository, BrokerPartnerRepository brokerRepository,
                                     TenantContextBinder tenantContextBinder, EntityManager entityManager) {
        this.repository = repository;
        this.brokerRepository = brokerRepository;
        this.tenantContextBinder = tenantContextBinder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<BrokerInteractionResponse> list(UUID brokerId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        return repository.findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByOccurredOnDesc(orgId, brokerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public BrokerInteractionResponse create(UUID brokerId, BrokerInteractionCreateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(brokerId, orgId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));

        UUID id = UUID.randomUUID();
        BrokerInteraction interaction = new BrokerInteraction(id, orgId, brokerId, req.occurredOn(), req.type(),
                req.remarks(), req.nextFollowUpDate(), tenantContextBinder.current().userId());
        interaction = repository.save(interaction);
        // Manually-assigned @Id means save() takes the merge() path, not
        // persist() -- the returned reference is a different, managed
        // instance (fixed by the reassignment above), but @CreationTimestamp
        // createdAt still isn't populated on it until a real DB round trip.
        // Same four-times-recurring bug class M2/M4's notes already document
        // for exactly this entity shape.
        entityManager.flush();
        entityManager.refresh(interaction);
        return toResponse(interaction);
    }

    private BrokerInteractionResponse toResponse(BrokerInteraction i) {
        return new BrokerInteractionResponse(i.getId(), i.getOccurredOn(), i.getType(), i.getRemarks(),
                i.getNextFollowUpDate(), i.getConductedBy(), i.getCreatedAt());
    }
}
