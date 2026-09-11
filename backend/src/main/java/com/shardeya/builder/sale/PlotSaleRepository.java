package com.shardeya.builder.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlotSaleRepository extends JpaRepository<PlotSale, UUID> {

    Optional<PlotSale> findByIdAndDeletedAtIsNull(UUID id);

    // B-14 §20.6 "GET /brokers/{id}/deals" -- every sale ever attributed to
    // this broker, regardless of status (ACTIVE/COMPLETED/CANCELLED all
    // shown, not just closed ones).
    List<PlotSale> findByOrgIdAndBrokerPartnerIdAndDeletedAtIsNullOrderByPurchaseDateDesc(UUID orgId, UUID brokerPartnerId);

    // Status bound as a real parameter, NOT an inlined JPQL enum literal --
    // a literal (e.g. "... <> PlotSale.Status.CANCELLED" written directly in
    // the query string) makes Hibernate generate a bare "<>'CANCELLED'::Status"
    // cast using the Java enum's simple class name instead of the actual
    // Postgres native enum type ("plot_sale_status"), failing at runtime with
    // "type Status does not exist" -- the exact documented gotcha in
    // OutboxEventRepository's own comment, reproduced here before being
    // caught by an actual API call.
    @Query("SELECT s FROM PlotSale s WHERE s.plotId = :plotId AND s.status <> :cancelled AND s.deletedAt IS NULL")
    Optional<PlotSale> findActiveByPlotId(@Param("plotId") UUID plotId, @Param("cancelled") PlotSale.Status cancelled);
}
