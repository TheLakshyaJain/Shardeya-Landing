package com.shardeya.builder.deal;

import com.shardeya.builder.deal.dto.DealDetailResponse;
import com.shardeya.builder.deal.dto.DealRow;
import com.shardeya.platform.CursorPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * B-10 §9: view is DATA_VIEW_ALL (Admin, Manager, View Only); Sales
 * Executive sees only deals they handled (enforced in the service, not the
 * annotation -- no single permission code expresses "all OR own", same
 * recurring class of gap this project's RBAC has hit before). Financial
 * columns require FINANCIAL_VIEW and are omitted (null), not blanked, when
 * absent -- also enforced in the service since it varies per-field, not
 * per-endpoint.
 */
@RestController
public class DealsHistoryController {

    private final DealsHistoryService service;

    public DealsHistoryController(DealsHistoryService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/builder/deals")
    public CursorPage<DealRow> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) UUID projectId,
                                     @RequestParam(required = false) UUID staffId,
                                     @RequestParam(required = false) LocalDate from,
                                     @RequestParam(required = false) LocalDate to,
                                     @RequestParam(required = false) String search,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "25") int limit) {
        return service.list(status, projectId, staffId, from, to, search, cursor, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping("/api/v1/builder/deals/{saleId}")
    public DealDetailResponse detail(@PathVariable UUID saleId) {
        return service.detail(saleId);
    }

    @GetMapping("/api/v1/builder/deals/summary")
    public DealsHistoryService.DealsSummary summary(@RequestParam(required = false) LocalDate from,
                                                      @RequestParam(required = false) LocalDate to) {
        return service.summary(from, to);
    }
}
