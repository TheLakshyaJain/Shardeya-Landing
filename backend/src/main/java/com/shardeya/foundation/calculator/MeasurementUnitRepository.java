package com.shardeya.foundation.calculator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeasurementUnitRepository extends JpaRepository<MeasurementUnit, UUID> {

    // State-specific row (if any) preferred over the universal one — ORDER BY
    // puts the matching-state row first when both exist (only BIGHA has both).
    // JPQL property paths use the entity's actual field name ("active"), not
    // the JavaBean getter name ("isActive") — Hibernate's HQL resolver is
    // lenient enough to accept either here, but Spring Data's *derived*
    // query-method-name parser below is not (see that method's own comment).
    // Written the strict/correct way in both places rather than relying on
    // undocumented leniency in one of them.
    @Query("""
            SELECT m FROM MeasurementUnit m WHERE m.code = :code AND m.active = true
              AND (m.stateCode IS NULL OR m.stateCode = :stateCode)
            ORDER BY CASE WHEN m.stateCode = :stateCode THEN 0 ELSE 1 END
            """)
    List<MeasurementUnit> findApplicable(@Param("code") String code, @Param("stateCode") String stateCode);

    default Optional<MeasurementUnit> findBestMatch(String code, String stateCode) {
        List<MeasurementUnit> matches = findApplicable(code, stateCode);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    // Must be "findByActiveTrue...", not "findByIsActiveTrue..." — Spring
    // Data's derived-query parser resolves property paths against the JPA
    // metamodel's actual attribute name (the entity field is `active`, the
    // getter is merely `isActive()` per JavaBean boolean convention). Using
    // the getter-style name here failed at context startup with
    // "No property 'isActive' found for type 'MeasurementUnit'" — only
    // caught by actually starting the Spring context (a compile-time check
    // can't see this; it's a runtime reflection-based binding).
    List<MeasurementUnit> findByActiveTrueAndStateCodeIsNull();
}
