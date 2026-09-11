package com.shardeya.foundation.calculator;

import com.shardeya.foundation.calculator.dto.BrokerageCalcRequest;
import com.shardeya.foundation.calculator.dto.BrokerageCalcResponse;
import com.shardeya.foundation.calculator.dto.PlotSizeRequest;
import com.shardeya.foundation.calculator.dto.PlotSizeResponse;
import com.shardeya.foundation.calculator.dto.StampDutyCalcRequest;
import com.shardeya.foundation.calculator.dto.StampDutyCalcResponse;
import com.shardeya.foundation.calculator.dto.UnitResponse;
import com.shardeya.platform.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * M-08: all three calculators (Plot Size since M2; Brokerage and Stamp Duty
 * added in M6, now that real broker deals and stamp-duty reference data
 * exist). "Plot-size and brokerage maths also run client-side for instant
 * feedback; the server endpoint exists for consistency, history, and any
 * future audit" (M-08 §4) -- stamp duty is deliberately server-only (§4:
 * "rates must never be stale in a cached bundle"), so there is no
 * client-side fallback for it, unlike the other two.
 */
@RestController
@RequestMapping("/api/v1/calc")
public class CalculatorController {

    private static final BigDecimal GST_PCT = BigDecimal.valueOf(18);

    private final MeasurementUnitRepository unitRepository;
    private final AreaConversionService areaConversionService;
    private final StampDutyRateRepository stampDutyRateRepository;

    public CalculatorController(MeasurementUnitRepository unitRepository, AreaConversionService areaConversionService,
                                 StampDutyRateRepository stampDutyRateRepository) {
        this.unitRepository = unitRepository;
        this.areaConversionService = areaConversionService;
        this.stampDutyRateRepository = stampDutyRateRepository;
    }

    @GetMapping("/units")
    public List<UnitResponse> units(@RequestParam(required = false) String stateCode) {
        List<MeasurementUnit> universal = unitRepository.findByActiveTrueAndStateCodeIsNull();
        List<UnitResponse> result = new java.util.ArrayList<>(universal.stream()
                .map(u -> new UnitResponse(u.getCode(), u.getNameEn(), u.getNameHi(), u.getToSqftFactor())).toList());
        MeasurementUnit bigha = areaConversionService.resolveBigha(stateCode);
        if (bigha != null) {
            result.add(new UnitResponse(bigha.getCode(), bigha.getNameEn(), bigha.getNameHi(), bigha.getToSqftFactor()));
        }
        return result;
    }

    @PostMapping("/plot-size")
    public PlotSizeResponse plotSize(@Valid @RequestBody PlotSizeRequest req) {
        BigDecimal area = req.length().multiply(req.width());
        var sqftMeasure = areaConversionService.toSqft(area, req.unit(), req.stateCode());
        BigDecimal sqft = sqftMeasure.sqft();

        BigDecimal sqm = areaConversionService.fromSqft(sqft, "SQ_M", req.stateCode());
        BigDecimal sqyd = areaConversionService.fromSqft(sqft, "SQ_YD", req.stateCode());
        BigDecimal gunta = areaConversionService.fromSqft(sqft, "GUNTA", req.stateCode());
        BigDecimal dismil = areaConversionService.fromSqft(sqft, "DISMIL", req.stateCode());

        MeasurementUnit bighaUnit = areaConversionService.resolveBigha(req.stateCode());
        BigDecimal bigha = bighaUnit == null ? null : sqft.divide(bighaUnit.getToSqftFactor(), 4, RoundingMode.HALF_UP);
        String bighaState = bighaUnit == null ? null : bighaUnit.getStateCode();

        return new PlotSizeResponse(sqft, sqm, sqyd, bigha, bighaState, gunta, dismil);
    }

    @PostMapping("/brokerage")
    public BrokerageCalcResponse brokerage(@Valid @RequestBody BrokerageCalcRequest req) {
        BigDecimal total = req.dealValue().multiply(req.brokeragePct()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal ownerShare = req.ownerSharePct() == null ? null
                : req.dealValue().multiply(req.ownerSharePct()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal buyerShare = req.buyerSharePct() == null ? null
                : req.dealValue().multiply(req.buyerSharePct()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal gst = total.multiply(GST_PCT).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal netPlusGst = total.add(gst);

        // M-08 §7: "if both split percentages are given they need not sum
        // to brokeragePct ... but if they do sum to something different,
        // show an informational note, not an error."
        boolean mismatch = req.ownerSharePct() != null && req.buyerSharePct() != null
                && req.ownerSharePct().add(req.buyerSharePct()).compareTo(req.brokeragePct()) != 0;

        return new BrokerageCalcResponse(total, ownerShare, buyerShare, gst, netPlusGst, mismatch);
    }

    @PostMapping("/stamp-duty")
    public StampDutyCalcResponse stampDuty(@Valid @RequestBody StampDutyCalcRequest req) {
        StampDutyRate rate = stampDutyRateRepository.findBestMatch(req.stateCode().toUpperCase(), req.propertyType(),
                        req.transactionType(), req.buyerGender(), LocalDate.now())
                // M-08 §10: "State with no rate row -> 'Rates for {state}
                // are being updated. Please check with your local
                // sub-registrar.' Never guess."
                .orElseThrow(() -> new BadRequestException("stateCode", "STAMP_DUTY_RATE_NOT_FOUND", "error.calc.stampDutyRateNotFound"));

        BigDecimal stampDuty = req.value().multiply(rate.getStampDutyPct()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        BigDecimal registration;
        if (rate.getRegistrationFlat() != null) {
            registration = rate.getRegistrationFlat();
        } else if (rate.getRegistrationPct() != null) {
            registration = req.value().multiply(rate.getRegistrationPct()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            if (rate.getRegistrationCap() != null && registration.compareTo(rate.getRegistrationCap()) > 0) {
                registration = rate.getRegistrationCap();
            }
        } else {
            registration = BigDecimal.ZERO;
        }

        return new StampDutyCalcResponse(stampDuty, registration, stampDuty.add(registration),
                rate.getBuyerGender().name(), rate.getEffectiveFrom(), rate.getSourceNote());
    }

    @GetMapping("/stamp-duty/states")
    public List<String> stampDutyStates() {
        return stampDutyRateRepository.findActiveStateCodes();
    }
}
