package com.shardeya.builder.sale;

import com.shardeya.builder.sale.dto.PlotDocumentAttachRequest;
import com.shardeya.builder.sale.dto.PlotDocumentResponse;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ProjectAccessGuard;
import com.shardeya.platform.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * B-04 §7/§12.3.3: four typed slots (Sale Agreement, Registry/Deed, Plot Map,
 * Buyer ID Proof) plus multi-file Other with a required label. ID Proof is
 * auto-classified sensitive and routed to the sensitive bucket by whichever
 * upload-intent call the client made (sensitive=true) -- this service trusts
 * that flag rather than re-deriving it from the media asset, since doc_type
 * alone already determines it unambiguously.
 */
@Service
public class PlotDocumentService {

    private final PlotDocumentRepository repository;
    private final PlotSaleRepository saleRepository;
    private final ProjectAccessGuard accessGuard;

    public PlotDocumentService(PlotDocumentRepository repository, PlotSaleRepository saleRepository, ProjectAccessGuard accessGuard) {
        this.repository = repository;
        this.saleRepository = saleRepository;
        this.accessGuard = accessGuard;
    }

    public List<PlotDocumentResponse> list(UUID saleId) {
        loadSale(saleId);
        return repository.findByPlotSaleIdAndDeletedAtIsNull(saleId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PlotDocumentResponse attach(UUID saleId, PlotDocumentAttachRequest req) {
        PlotSale sale = loadSale(saleId);
        if (req.docType() == PlotDocument.DocType.OTHER && (req.label() == null || req.label().isBlank())) {
            throw new BadRequestException("label", "LABEL_REQUIRED_FOR_OTHER", "error.document.labelRequiredForOther");
        }
        boolean sensitive = req.docType() == PlotDocument.DocType.BUYER_ID_PROOF;
        PlotDocument doc = new PlotDocument(UUID.randomUUID(), sale.getOrgId(), sale.getPlotId(), saleId,
                req.docType(), req.label(), req.mediaId(), sensitive);
        repository.save(doc);
        return toResponse(doc);
    }

    @Transactional
    public void delete(UUID documentId) {
        PlotDocument doc = repository.findByIdAndDeletedAtIsNull(documentId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        loadSale(doc.getPlotSaleId());
        doc.setDeletedAt(Instant.now());
        repository.save(doc);
    }

    private PlotSale loadSale(UUID saleId) {
        PlotSale sale = saleRepository.findByIdAndDeletedAtIsNull(saleId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        accessGuard.assertAccess(sale.getProjectId());
        return sale;
    }

    private PlotDocumentResponse toResponse(PlotDocument d) {
        return new PlotDocumentResponse(d.getId(), d.getDocType().name(), d.getLabel(), d.getMediaId(), d.isSensitive());
    }
}
