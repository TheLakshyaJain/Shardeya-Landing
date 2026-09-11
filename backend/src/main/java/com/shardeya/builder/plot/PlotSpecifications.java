package com.shardeya.builder.plot;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.UUID;

/** Dynamic list/filter predicates (B-03 §4 list query) — a Specification per optional filter, combined with `and`. */
public final class PlotSpecifications {

    private PlotSpecifications() {
    }

    public static Specification<Plot> forProject(UUID projectId) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("projectId"), projectId), cb.isNull(root.get("deletedAt")));
    }

    public static Specification<Plot> status(Plot.Status status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Plot> facing(Plot.Facing facing) {
        return facing == null ? null : (root, query, cb) -> cb.equal(root.get("facing"), facing);
    }

    public static Specification<Plot> sizeMin(BigDecimal min) {
        return min == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("sizeSqft"), min);
    }

    public static Specification<Plot> sizeMax(BigDecimal max) {
        return max == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("sizeSqft"), max);
    }

    public static Specification<Plot> priceMin(BigDecimal min) {
        return min == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Plot> priceMax(BigDecimal max) {
        return max == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Plot> isHot(Boolean hot) {
        return hot == null ? null : (root, query, cb) -> cb.equal(root.get("hot"), hot);
    }

    public static Specification<Plot> isCorner(Boolean corner) {
        return corner == null ? null : (root, query, cb) -> cb.equal(root.get("corner"), corner);
    }

    public static Specification<Plot> isGarden(Boolean garden) {
        return garden == null ? null : (root, query, cb) -> cb.equal(root.get("garden"), garden);
    }

    // ILIKE with a leading wildcard — pg_trgm's GIN index on plot_number
    // (ix_plot_num_trgm) is what makes this fast rather than a full scan.
    public static Specification<Plot> search(String term) {
        if (term == null || term.isBlank()) return null;
        String pattern = "%" + term.trim() + "%";
        return (root, query, cb) -> cb.like(cb.upper(root.get("plotNumber")), pattern.toUpperCase());
    }

    public static Specification<Plot> combine(Specification<Plot>... specs) {
        Specification<Plot> result = Specification.where(null);
        for (Specification<Plot> spec : specs) {
            if (spec != null) {
                result = result.and(spec);
            }
        }
        return result;
    }
}
