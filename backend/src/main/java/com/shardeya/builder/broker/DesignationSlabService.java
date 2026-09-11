package com.shardeya.builder.broker;

import com.shardeya.builder.broker.dto.DesignationSlabResponse;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 06-BROKER-NETWORK-ENGINE.md §5/§6 -- resolves the designation slab
 * matching a given team-sales count. Config-driven, not hardcoded
 * if/else (§47's own explicit requirement) -- adding, removing, or
 * re-ranging a slab is a data change via {@link DesignationSlabRepository},
 * never a code change.
 */
@Service
public class DesignationSlabService {

    private final DesignationSlabRepository repository;
    private final TenantContextBinder tenantContextBinder;

    public DesignationSlabService(DesignationSlabRepository repository, TenantContextBinder tenantContextBinder) {
        this.repository = repository;
        this.tenantContextBinder = tenantContextBinder;
    }

    @Transactional(readOnly = true)
    public List<DesignationSlabResponse> list() {
        UUID orgId = tenantContextBinder.currentOrgId();
        return repository.findAllForOrg(orgId).stream().map(this::toResponse).toList();
    }

    /**
     * The slab a broker with exactly {@code teamSales} completed team
     * sales currently sits in. §6: "the booking uses the broker's rate as
     * it is immediately before that booking" -- callers always pass the
     * broker's CURRENT team-sales count (never a hypothetical
     * post-booking one) to get the currently-effective rate.
     */
    @Transactional(readOnly = true)
    public DesignationSlab resolve(UUID orgId, int teamSales) {
        List<DesignationSlab> matches = repository.findMatchingSlabs(orgId, teamSales);
        if (matches.isEmpty()) {
            // Can only happen if the system-default 8 rows were somehow
            // deleted/deactivated with no org-specific replacement -- a
            // genuine misconfiguration, not a normal runtime state.
            throw new IllegalStateException("No designation_slab row matches team sales count " + teamSales
                    + " for org " + orgId + " -- system defaults may be missing or deactivated.");
        }
        return matches.get(0);
    }

    /**
     * §7's revised same-slab formula: the rate of the slab immediately
     * above {@code currentDesignationId}, or {@code null} if that slab is
     * already the top one (§7's own mandatory ₹0 edge case -- the caller,
     * {@link CommissionCalculationEngine}, turns a null into an explicit
     * zero, never a fabricated rate). Resolved from the same
     * {@code designation_slab} config every other slab lookup in this
     * engine reads -- never a second, hardcoded ladder.
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveNextRate(UUID orgId, UUID currentDesignationId) {
        if (currentDesignationId == null) {
            return null;
        }
        DesignationSlab current = repository.findById(currentDesignationId).orElse(null);
        if (current == null) {
            return null;
        }
        List<DesignationSlab> above = repository.findSlabsAboveSortOrder(orgId, current.getSortOrder());
        return above.isEmpty() ? null : above.get(0).getRatePerSqft();
    }

    private DesignationSlabResponse toResponse(DesignationSlab s) {
        return new DesignationSlabResponse(s.getId(), s.getName(), s.getNameHi(), s.getMinTeamSales(),
                s.getMaxTeamSales(), s.getRatePerSqft(), s.getSortOrder());
    }
}
