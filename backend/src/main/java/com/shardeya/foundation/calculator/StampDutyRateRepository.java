package com.shardeya.foundation.calculator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StampDutyRateRepository extends JpaRepository<StampDutyRate, UUID> {

    // M-08 §7 lookup path: "(state, propertyType, transactionType, gender)
    // effective today, falling back to gender=ANY" -- ordered so an exact
    // gender match wins over the ANY fallback, same CASE-ordering pattern
    // MeasurementUnitRepository already uses for its own stateCode fallback.
    // buyerGender/genderFallback are bound as real @Param values, never
    // inlined -- the documented (M3/M4) JPQL-enum-literal bug class.
    @Query("SELECT r FROM StampDutyRate r WHERE r.stateCode = :stateCode AND r.propertyType = :propertyType "
            + "AND r.transactionType = :transactionType AND (r.buyerGender = :buyerGender OR r.buyerGender = :genderFallback) "
            + "AND r.effectiveFrom <= :onDate AND (r.effectiveTo IS NULL OR r.effectiveTo >= :onDate) "
            + "ORDER BY CASE WHEN r.buyerGender = :buyerGender THEN 0 ELSE 1 END, r.effectiveFrom DESC")
    List<StampDutyRate> findApplicable(@Param("stateCode") String stateCode, @Param("propertyType") StampDutyRate.PropertyType propertyType,
                                        @Param("transactionType") StampDutyRate.TransactionType transactionType,
                                        @Param("buyerGender") StampDutyRate.BuyerGender buyerGender,
                                        @Param("genderFallback") StampDutyRate.BuyerGender genderFallback,
                                        @Param("onDate") LocalDate onDate);

    default Optional<StampDutyRate> findBestMatch(String stateCode, StampDutyRate.PropertyType propertyType,
                                                    StampDutyRate.TransactionType transactionType, StampDutyRate.BuyerGender buyerGender,
                                                    LocalDate onDate) {
        List<StampDutyRate> matches = findApplicable(stateCode, propertyType, transactionType, buyerGender, StampDutyRate.BuyerGender.ANY, onDate);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    // M-08 §4 "GET /calc/stamp-duty/states -> states with current rates + last updated".
    @Query("SELECT DISTINCT r.stateCode FROM StampDutyRate r WHERE r.effectiveFrom <= CURRENT_DATE AND (r.effectiveTo IS NULL OR r.effectiveTo >= CURRENT_DATE) ORDER BY r.stateCode")
    List<String> findActiveStateCodes();
}
