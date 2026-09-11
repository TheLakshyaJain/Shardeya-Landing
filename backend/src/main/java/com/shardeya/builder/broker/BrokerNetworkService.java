package com.shardeya.builder.broker;

import com.shardeya.platform.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §10 -- network integrity, enforced in code
 * (not just UI), and closure-table maintenance. Re-parenting an existing
 * broker is NOT built yet (§10 itself hedges "if allowed at all in v1");
 * {@link #assertCanSetUpline} is written generally enough (an optional
 * "broker being moved" id) to support it later without changing this
 * validation's shape, but no re-parent endpoint exists in this round --
 * only the create-time path calls it.
 */
@Service
public class BrokerNetworkService {

    private final BrokerNetworkRepository repository;
    private final BrokerPartnerRepository brokerRepository;

    public BrokerNetworkService(BrokerNetworkRepository repository, BrokerPartnerRepository brokerRepository) {
        this.repository = repository;
        this.brokerRepository = brokerRepository;
    }

    /**
     * Validates a proposed upline assignment before anything is written.
     * {@code brokerId} is null when validating a brand-new broker (self-
     * upline and cycle checks against it are then structurally impossible
     * and skipped); non-null when validating a re-parent of an existing
     * broker.
     */
    @Transactional(readOnly = true)
    public void assertCanSetUpline(UUID orgId, UUID brokerId, UUID uplineBrokerId) {
        if (uplineBrokerId == null) {
            return; // top-level broker, nothing to validate
        }
        // §10: no self-upline.
        if (uplineBrokerId.equals(brokerId)) {
            throw new BadRequestException("uplineBrokerId", "BROKER_SELF_UPLINE", "error.broker.selfUpline");
        }
        BrokerPartner upline = brokerRepository.findByIdAndOrgIdAndDeletedAtIsNull(uplineBrokerId, orgId)
                .orElseThrow(() -> new BadRequestException("uplineBrokerId", "BROKER_UPLINE_NOT_FOUND", "error.broker.uplineNotFound"));
        // §0: PERCENTAGE brokers don't participate in the hierarchy at all -- can't be anyone's upline.
        if (upline.getCommissionType() != BrokerPartner.CommissionType.DESIGNATION) {
            throw new BadRequestException("uplineBrokerId", "BROKER_UPLINE_NOT_DESIGNATION", "error.broker.uplineNotDesignation");
        }
        // §10: cannot move a broker under its own descendant -- only meaningful for an
        // existing broker (brokerId != null); a brand-new broker has no descendants yet.
        if (brokerId != null && repository.existsById_AncestorBrokerIdAndId_DescendantBrokerId(brokerId, uplineBrokerId)) {
            throw new BadRequestException("uplineBrokerId", "BROKER_UPLINE_CYCLE", "error.broker.uplineWouldCreateCycle");
        }
    }

    /**
     * Inserts the closure-table rows for a brand-new broker: its own
     * depth-0 self-row, plus (for every real ancestor of its upline,
     * upline included) a row one level deeper than that ancestor already
     * sits relative to the upline. Call only after the broker itself has
     * been persisted and {@link #assertCanSetUpline} has already passed.
     */
    @Transactional
    public void attachNewBroker(UUID orgId, UUID newBrokerId, UUID uplineBrokerId) {
        repository.save(new BrokerNetwork(newBrokerId, newBrokerId, 0, orgId));
        if (uplineBrokerId == null) {
            return;
        }
        List<BrokerNetwork> uplineAncestors = repository.findAncestorsOf(orgId, uplineBrokerId);
        for (BrokerNetwork ancestorRow : uplineAncestors) {
            repository.save(new BrokerNetwork(ancestorRow.getAncestorBrokerId(), newBrokerId, ancestorRow.getDepth() + 1, orgId));
        }
    }

    /** The full ancestor chain of {@code brokerId}, nearest (depth 1) first, self-row excluded -- the exact input shape the commission engine's chain-walk needs (§7). */
    @Transactional(readOnly = true)
    public List<BrokerNetwork> ancestorsExcludingSelf(UUID orgId, UUID brokerId) {
        return repository.findAncestorsOf(orgId, brokerId).stream().filter(n -> n.getDepth() > 0).toList();
    }
}
