package com.shardeya.foundation.calculator;

import com.shardeya.foundation.calculator.dto.BrokerageCalcRequest;
import com.shardeya.foundation.calculator.dto.BrokerageCalcResponse;
import com.shardeya.foundation.calculator.dto.StampDutyCalcRequest;
import com.shardeya.foundation.calculator.dto.StampDutyCalcResponse;
import com.shardeya.platform.BadRequestException;
import com.shardeya.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M-08 §7 -- Brokerage (GST, share split, mismatch note) and Stamp Duty
 * (gender-specific lookup, gender=ANY fallback, flat/pct/cap registration
 * semantics) added this milestone. Called directly against the controller
 * (no HTTP/auth layer) since these endpoints carry no org/tenant scoping at
 * all -- same "call the Spring-managed bean directly" pattern this suite
 * already uses for PlotSaleService et al.
 */
class CalculatorControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CalculatorController controller;

    @Test
    void brokerageComputesTotalGstAndFlagsAMismatchedSplit() {
        BrokerageCalcResponse resp = controller.brokerage(new BrokerageCalcRequest(
                BigDecimal.valueOf(4_200_000), BigDecimal.valueOf(2), BigDecimal.valueOf(1), BigDecimal.valueOf(1.5)));

        assertThat(resp.total()).isEqualByComparingTo(BigDecimal.valueOf(84_000)); // 2% of 4,200,000
        assertThat(resp.ownerShare()).isEqualByComparingTo(BigDecimal.valueOf(42_000)); // 1%
        assertThat(resp.buyerShare()).isEqualByComparingTo(BigDecimal.valueOf(63_000)); // 1.5%
        assertThat(resp.gst()).isEqualByComparingTo(BigDecimal.valueOf(15_120)); // 18% of 84,000
        assertThat(resp.netPlusGst()).isEqualByComparingTo(BigDecimal.valueOf(99_120));
        assertThat(resp.sharesSumMismatch()).isTrue(); // 1 + 1.5 = 2.5, not the 2% brokeragePct
    }

    @Test
    void brokerageWithMatchingSplitHasNoMismatchFlag() {
        BrokerageCalcResponse resp = controller.brokerage(new BrokerageCalcRequest(
                BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(2), BigDecimal.valueOf(1), BigDecimal.valueOf(1)));
        assertThat(resp.sharesSumMismatch()).isFalse();
    }

    @Test
    void brokerageWithNoSharesSuppliedIsNotFlaggedAsMismatched() {
        BrokerageCalcResponse resp = controller.brokerage(new BrokerageCalcRequest(
                BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(2), null, null));
        assertThat(resp.sharesSumMismatch()).isFalse();
        assertThat(resp.ownerShare()).isNull();
        assertThat(resp.buyerShare()).isNull();
    }

    @Test
    void stampDutyResolvesTheGenderSpecificRateWhenOneExists() {
        // UP RESIDENTIAL SALE FEMALE = 6.0%, distinct from MALE's 7.0% and JOINT's 6.5%.
        StampDutyCalcResponse resp = controller.stampDuty(new StampDutyCalcRequest(
                "UP", StampDutyRate.PropertyType.RESIDENTIAL, StampDutyRate.TransactionType.SALE,
                BigDecimal.valueOf(5_000_000), StampDutyRate.BuyerGender.FEMALE));

        assertThat(resp.stampDuty()).isEqualByComparingTo(BigDecimal.valueOf(300_000)); // 6% of 50L
        assertThat(resp.appliedGender()).isEqualTo("FEMALE"); // exact match, not the ANY fallback
        assertThat(resp.registrationCharges()).isEqualByComparingTo(BigDecimal.valueOf(50_000)); // 1% of 50L, no cap in UP
    }

    @Test
    void stampDutyFallsBackToAnyGenderWhenNoGenderSpecificRowExists() {
        // Madhya Pradesh is seeded ANY-only (no MALE/FEMALE/JOINT rows) --
        // a MALE lookup must still resolve, via the fallback, not 400.
        StampDutyCalcResponse resp = controller.stampDuty(new StampDutyCalcRequest(
                "MP", StampDutyRate.PropertyType.RESIDENTIAL, StampDutyRate.TransactionType.SALE,
                BigDecimal.valueOf(2_000_000), StampDutyRate.BuyerGender.MALE));

        assertThat(resp.stampDuty()).isEqualByComparingTo(BigDecimal.valueOf(150_000)); // 7.5% of 20L
        assertThat(resp.appliedGender()).isEqualTo("ANY"); // confirms the fallback path actually fired
    }

    @Test
    void stampDutyRegistrationIsCappedWhenTheStateSeedsACap() {
        // Maharashtra: registration_pct=1%, registration_cap=30,000 -- on a
        // deal value where 1% would exceed 30,000, the cap must win.
        StampDutyCalcResponse resp = controller.stampDuty(new StampDutyCalcRequest(
                "MH", StampDutyRate.PropertyType.RESIDENTIAL, StampDutyRate.TransactionType.SALE,
                BigDecimal.valueOf(10_000_000), StampDutyRate.BuyerGender.MALE)); // 1% of 1Cr = 1,00,000, way over the 30k cap

        assertThat(resp.registrationCharges()).isEqualByComparingTo(BigDecimal.valueOf(30_000));
        assertThat(resp.stampDuty()).isEqualByComparingTo(BigDecimal.valueOf(600_000)); // 6% of 1Cr, uncapped
        assertThat(resp.totalGovernmentCharges()).isEqualByComparingTo(BigDecimal.valueOf(630_000));
    }

    @Test
    void stampDutyForAStateWithNoRateRowIsRejectedRatherThanGuessed() {
        // M-08 §10: "State with no rate row -> ... Never guess."
        assertThatThrownBy(() -> controller.stampDuty(new StampDutyCalcRequest(
                "ZZ", StampDutyRate.PropertyType.RESIDENTIAL, StampDutyRate.TransactionType.SALE,
                BigDecimal.valueOf(1_000_000), StampDutyRate.BuyerGender.MALE)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void stampDutyStatesReturnsEveryCurrentlySeededState() {
        List<String> states = controller.stampDutyStates();
        assertThat(states).contains("UP", "RJ", "MH", "DL", "MP", "KA");
    }
}
