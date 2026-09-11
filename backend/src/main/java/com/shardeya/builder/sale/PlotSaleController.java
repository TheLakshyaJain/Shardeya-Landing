package com.shardeya.builder.sale;

import com.shardeya.builder.sale.dto.BuyerWhatsAppOptInRequest;
import com.shardeya.builder.sale.dto.CancelSaleRequest;
import com.shardeya.builder.sale.dto.GovIdRevealResponse;
import com.shardeya.builder.sale.dto.SaleCreateRequest;
import com.shardeya.builder.sale.dto.SaleResponse;
import com.shardeya.builder.sale.dto.SaleUpdateRequest;
import com.shardeya.platform.IdempotencyService;
import com.shardeya.platform.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Permission mapping note: B-04 §9 specifies some actions as requiring
 * MULTIPLE permissions together (e.g. create/edit sale: "DATA_EDIT_ALL +
 * FINANCIAL_VIEW") or role-specific gates ("Cancel: Admin only") that don't
 * exist as a distinct permission in today's catalogue. PermissionAspect only
 * supports a single @RequiresPermission per method (no @Repeatable support),
 * and every role that exists before M4 (BUILDER_ADMIN, BROKER_OWNER) already
 * holds every permission in the catalogue anyway, so this doesn't change
 * observable behaviour yet -- each endpoint below is gated on the single
 * most-relevant permission. Revisit once M4 adds a Manager/Sales
 * Executive/Accounts Staff role that actually needs the finer distinction,
 * and consider extending PermissionAspect to accept multiple values then.
 */
@RestController
public class PlotSaleController {

    private final PlotSaleService service;
    private final IdempotencyService idempotencyService;

    public PlotSaleController(PlotSaleService service, IdempotencyService idempotencyService) {
        this.service = service;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/api/v1/plots/{plotId}/sale")
    @RequiresPermission("DATA_EDIT_ALL")
    public ResponseEntity<SaleResponse> create(@PathVariable UUID plotId, @Valid @RequestBody SaleCreateRequest request,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        SaleResponse response = idempotencyService.withIdempotency("plot-sale-create", idempotencyKey, SaleResponse.class,
                () -> service.create(plotId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // M4: these two reads carried NO permission check at all before this
    // milestone -- any authenticated org user (any role) could read
    // deal_value/buyer/payment-status details regardless of role. That's
    // exactly the gap CLAUDE.md's M4 brief called out by name ("a Sales
    // Executive must not be able to see financials from B-05/B-04 via any
    // existing API"). Gated on FINANCIAL_VIEW now, same as the rest of B-04/
    // B-05's financial surface -- Sales Executive and View Only hold neither.
    @GetMapping("/api/v1/plots/{plotId}/sale")
    @RequiresPermission("FINANCIAL_VIEW")
    public SaleResponse getByPlot(@PathVariable UUID plotId) {
        return service.getByPlotId(plotId);
    }

    @GetMapping("/api/v1/sales/{id}")
    @RequiresPermission("FINANCIAL_VIEW")
    public SaleResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/api/v1/sales/{id}")
    @RequiresPermission("DATA_EDIT_ALL")
    public SaleResponse update(@PathVariable UUID id, @RequestBody SaleUpdateRequest request) {
        return service.update(id, request);
    }

    // Same DATA_EDIT_ALL gate as update() above -- toggling a buyer's
    // WhatsApp consent is an edit to the sale's own contact-preference
    // state, not a financial action.
    @PostMapping("/api/v1/sales/{id}/buyer-whatsapp-optin")
    @RequiresPermission("DATA_EDIT_ALL")
    public SaleResponse setBuyerWhatsAppOptIn(@PathVariable UUID id, @Valid @RequestBody BuyerWhatsAppOptInRequest request) {
        return service.setBuyerWhatsAppOptIn(id, request.optedIn());
    }

    @PostMapping("/api/v1/sales/{id}/cancel")
    @RequiresPermission("DATA_EDIT_ALL")
    public SaleResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelSaleRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/api/v1/sales/{id}/complete")
    @RequiresPermission("DATA_EDIT_ALL")
    public SaleResponse complete(@PathVariable UUID id) {
        return service.complete(id);
    }

    // GET per B-04's own API list -- unusual for something that writes an
    // audit-log side effect, but matches the documented contract exactly;
    // "reason" travels as a query param since a GET has no conventional body.
    @GetMapping("/api/v1/sales/{id}/gov-id")
    @RequiresPermission("SENSITIVE_VIEW")
    public GovIdRevealResponse revealGovId(@PathVariable UUID id,
                                            @org.springframework.web.bind.annotation.RequestParam(required = false) String reason) {
        return service.revealGovId(id, reason);
    }
}
