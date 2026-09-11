package com.shardeya.builder.sale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlotDocumentRepository extends JpaRepository<PlotDocument, UUID> {

    Optional<PlotDocument> findByIdAndDeletedAtIsNull(UUID id);

    List<PlotDocument> findByPlotSaleIdAndDeletedAtIsNull(UUID plotSaleId);
}
