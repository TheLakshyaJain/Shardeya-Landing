package com.shardeya.builder.document;

import com.shardeya.builder.document.dto.BulkGenerateRequest;
import com.shardeya.builder.document.dto.GenerateDocumentRequest;
import com.shardeya.builder.document.dto.GeneratedDocumentResponse;
import com.shardeya.builder.payment.PaymentRecord;
import com.shardeya.builder.payment.PaymentRecordRepository;
import com.shardeya.builder.payment.PaymentSchedule;
import com.shardeya.builder.payment.PaymentScheduleRepository;
import com.shardeya.builder.sale.PlotSale;
import com.shardeya.builder.sale.PlotSaleRepository;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.foundation.media.MediaService;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.OutboxService;
import com.shardeya.platform.PdfRenderer;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import com.shardeya.shared.IndianTime;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * B-11 §17.2 document generation, numbering, and the snapshot contract:
 * "reprinting a receipt in 2029 reproduces the 2026 figures even if the
 * template changed and the sale was amended" -- rendered_snapshot is
 * written once, at generation time, from the same {@link DocumentContextBuilder}
 * preview uses, and never touched again.
 */
@Service
public class DocumentGenerationService {

    private final DocumentTemplateRepository templateRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final PlotSaleRepository plotSaleRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final DocumentContextBuilder contextBuilder;
    private final TemplateRenderer renderer;
    private final PdfRenderer pdfRenderer;
    private final MediaService mediaService;
    private final DocumentNumberService documentNumberService;
    private final EntitlementService entitlementService;
    private final TenantContextBinder tenantContextBinder;
    private final OutboxService outboxService;
    private final EntityManager entityManager;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final AppUserRepository appUserRepository;

    public DocumentGenerationService(DocumentTemplateRepository templateRepository, GeneratedDocumentRepository documentRepository,
                                      PlotSaleRepository plotSaleRepository, PaymentRecordRepository paymentRecordRepository,
                                      PaymentScheduleRepository scheduleRepository, DocumentContextBuilder contextBuilder,
                                      TemplateRenderer renderer, PdfRenderer pdfRenderer, MediaService mediaService,
                                      DocumentNumberService documentNumberService, EntitlementService entitlementService,
                                      TenantContextBinder tenantContextBinder, OutboxService outboxService, EntityManager entityManager,
                                      com.fasterxml.jackson.databind.ObjectMapper objectMapper, AppUserRepository appUserRepository) {
        this.templateRepository = templateRepository;
        this.documentRepository = documentRepository;
        this.plotSaleRepository = plotSaleRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.scheduleRepository = scheduleRepository;
        this.contextBuilder = contextBuilder;
        this.renderer = renderer;
        this.pdfRenderer = pdfRenderer;
        this.mediaService = mediaService;
        this.documentNumberService = documentNumberService;
        this.entitlementService = entitlementService;
        this.tenantContextBinder = tenantContextBinder;
        this.outboxService = outboxService;
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public GeneratedDocumentResponse generate(GenerateDocumentRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        DocumentTemplate.DocType docType = DocumentTemplateService.parseDocType(req.docType());
        String language = DocumentTemplateService.parseLanguage(req.language());
        entitlementService.assertTierAtLeast(orgId, LegalDocsTier.KEY, LegalDocsTier.ORDERED,
                LegalDocsTier.minimumFor(docType), "error.document.legalDocsNotEnabled");

        assertValidEntityState(docType, req.entityId());

        GeneratedDocument doc = generateInternal(orgId, docType, req.entityId(), req.templateId(), language, tenantContextBinder.current().userId());
        outboxService.enqueueNotification(orgId, "DOCUMENT_GENERATED", "notification.documentGenerated",
                null, Map.of("docType", docType.name(), "number", doc.getDocumentNumber()),
                doc.getEntityType(), doc.getEntityId());
        return toResponse(doc);
    }

    /**
     * B-11 §17.2 "a payment receipt is generated automatically on every
     * payment_record insert via the outbox" -- CLAUDE.md rule #6 (PDF
     * generation is a side effect, never inline) means this is called from
     * {@link com.shardeya.platform.OutboxPoller}, not from PaymentService
     * directly. Silently no-ops (does not throw) when the org's plan has no
     * LEGAL_DOCS entitlement -- an automatic system trigger failing
     * permanently on every single payment for a Free-plan org is not an
     * error condition worth retrying or logging as one.
     */
    @Transactional
    public void autoGenerateReceipt(UUID orgId, UUID paymentRecordId) {
        String tier = entitlementService.tierOf(orgId, LegalDocsTier.KEY, LegalDocsTier.ORDERED);
        if (LegalDocsTier.ORDERED.indexOf(tier) < LegalDocsTier.ORDERED.indexOf(LegalDocsTier.minimumFor(DocumentTemplate.DocType.PAYMENT_RECEIPT))) {
            return;
        }
        PaymentRecord record = paymentRecordRepository.findById(paymentRecordId).orElse(null);
        if (record == null) return;
        // SYSTEM_ACTOR_ID (new UUID(0,0), the placeholder OutboxPoller binds
        // TenantContext with for background jobs) was never meant to be
        // PERSISTED -- every prior use of it (notifications, the overdue
        // sweep) only ever needed it for the RLS-context bind itself, never
        // wrote it into an FK-constrained column. generated_document.generated_by
        // and (transitively, via MediaService.storeGenerated) media_asset.uploaded_by
        // both really do FK to app_user, so using that placeholder here
        // threw a real ConstraintViolationException on every single
        // attempt, retried 5 times, then permanently failed -- caught only
        // by watching the outbox actually dispatch, not from reading the
        // code (this is a "looks the same as the established SYSTEM_ACTOR_ID
        // pattern" mistake, not a novel one). Attributed to the org's real
        // owner account instead -- a real row, and a reasonable "the
        // system generated this on the org's behalf" convention.
        UUID actorId = appUserRepository.findFirstByOrgIdAndOwnerTrueAndDeletedAtIsNull(orgId)
                .map(u -> u.getId()).orElse(record.getReceivedBy());
        GeneratedDocument doc = generateInternal(orgId, DocumentTemplate.DocType.PAYMENT_RECEIPT, paymentRecordId, null, "en", actorId);
        record.setReceiptDocumentId(doc.getId());
        paymentRecordRepository.save(record);
        outboxService.enqueueNotification(orgId, "DOCUMENT_GENERATED", "notification.documentGenerated",
                null, Map.of("docType", "PAYMENT_RECEIPT", "number", doc.getDocumentNumber()),
                doc.getEntityType(), doc.getEntityId());
    }

    @Transactional
    public byte[] bulkGenerateDemandLetters(BulkGenerateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        DocumentTemplate.DocType docType = DocumentTemplateService.parseDocType(req.docType());
        if (docType != DocumentTemplate.DocType.DEMAND_LETTER) {
            throw new BadRequestException("docType", "BULK_ONLY_DEMAND_LETTER", "error.document.bulkOnlyDemandLetter");
        }
        // B-11 §7 "bulk generation" is named explicitly under Premium/Full --
        // Basic (Pro) only covers receipts + allotment letters individually.
        entitlementService.assertTierAtLeast(orgId, LegalDocsTier.KEY, LegalDocsTier.ORDERED, "FULL", "error.document.bulkGenerationRequiresFull");
        String language = DocumentTemplateService.parseLanguage(req.language());

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
                int included = 0;
                for (UUID entityId : req.entityIds()) {
                    // B-11 §11 "demand letters require at least one overdue
                    // instalment" -- a sale with none is skipped rather than
                    // failing the whole batch, matching this codebase's own
                    // established chunked-commit precedent (M2 bulk import:
                    // "some rows succeeded before the failure" is a
                    // meaningful outcome, not something to roll back wholesale).
                    if (!hasOverdueInstalment(entityId)) continue;
                    GeneratedDocument doc = generateInternal(orgId, docType, entityId, null, language, tenantContextBinder.current().userId());
                    byte[] pdfBytes = mediaService.downloadBytes(doc.getMediaId());
                    zip.putNextEntry(new ZipEntry(doc.getDocumentNumber().replace('/', '-') + ".pdf"));
                    zip.write(pdfBytes);
                    zip.closeEntry();
                    included++;
                }
                if (included == 0) {
                    throw new BadRequestException("entityIds", "NO_OVERDUE_ENTITIES", "error.document.noOverdueEntitiesInBatch");
                }
            }
            outboxService.enqueueNotification(orgId, "BULK_GENERATION_COMPLETE", "notification.bulkGenerationComplete",
                    null, Map.of("count", req.entityIds().size()), "PLOT_SALE", null);
            return buffer.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to assemble demand-letter ZIP", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] downloadBytes(UUID id) {
        GeneratedDocument doc = loadOwn(id);
        return mediaService.downloadBytes(doc.getMediaId());
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentResponse get(UUID id) {
        return toResponse(loadOwn(id));
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentResponse> listForEntity(String entityType, UUID entityId) {
        UUID orgId = tenantContextBinder.currentOrgId();
        return documentRepository.findByEntity(orgId, entityType, entityId).stream().map(this::toResponse).toList();
    }

    // --- internals -------------------------------------------------------

    private GeneratedDocument generateInternal(UUID orgId, DocumentTemplate.DocType docType, UUID entityId,
                                                UUID explicitTemplateId, String language, UUID actorId) {
        DocumentTemplate template = resolveTemplate(orgId, docType, language, explicitTemplateId);
        Map<String, Object> context = contextBuilder.buildContext(docType, entityId);

        LocalDate asOf = IndianTime.today();
        String documentNumber;
        if (docType == DocumentTemplate.DocType.PAYMENT_RECEIPT) {
            // Reuses payment_record's own already-gapless receipt_no rather
            // than drawing a second, independent number for the same
            // receipt -- see DocumentNumberService's own javadoc.
            PaymentRecord record = paymentRecordRepository.findById(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
            documentNumber = record.getReceiptNo();
        } else {
            documentNumber = documentNumberService.nextDocumentNumber(orgId, docType, asOf);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> documentCtx = (Map<String, Object>) context.get("document");
        documentCtx.put("number", documentNumber);

        String headerHtml = renderer.render(template.getHeaderHtml(), context);
        String bodyHtml = renderer.render(template.getBodyHtml(), context);
        String footerHtml = renderer.render(template.getFooterHtml(), context);
        String xhtml = wrapXhtml(headerHtml, bodyHtml, footerHtml);
        byte[] pdfBytes = pdfRenderer.renderPdf(xhtml);

        String filename = documentNumber.replace('/', '-') + ".pdf";
        UUID mediaId = mediaService.storeGenerated(pdfBytes, filename, "application/pdf", "documents", actorId);

        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            snapshotJson = "{}";
        }

        GeneratedDocument doc = new GeneratedDocument(UUID.randomUUID(), orgId, docType, template.getId(), template.getVersion(),
                contextBuilder.entityTypeFor(docType), entityId, mediaId, snapshotJson, documentNumber, language, actorId);
        // Manually-assigned @Id + @CreationTimestamp -- the documented
        // (4-times-recurring, CLAUDE.md M2/M4 notes) merge()-vs-persist()
        // bug class. save() alone leaves the RETURNED instance's
        // generatedAt null; flush+refresh is required, not just
        // reassigning the returned reference.
        doc = documentRepository.save(doc);
        entityManager.flush();
        entityManager.refresh(doc);
        return doc;
    }

    private DocumentTemplate resolveTemplate(UUID orgId, DocumentTemplate.DocType docType, String language, UUID explicitTemplateId) {
        if (explicitTemplateId != null) {
            DocumentTemplate t = templateRepository.findByIdAndDeletedAtIsNull(explicitTemplateId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
            if (t.getOrgId() != null && !t.getOrgId().equals(orgId)) throw new ResourceNotFoundException("error.notFound");
            return t;
        }
        // Resolution order: org's own active template, else the system default.
        return templateRepository.findActiveOrgTemplate(orgId, docType, language)
                .or(() -> templateRepository.findActiveSystemTemplate(docType, language))
                .orElseThrow(() -> new ResourceNotFoundException("error.document.noActiveTemplate"));
    }

    private void assertValidEntityState(DocumentTemplate.DocType docType, UUID entityId) {
        if (docType == DocumentTemplate.DocType.ALLOTMENT_LETTER) {
            PlotSale sale = plotSaleRepository.findByIdAndDeletedAtIsNull(entityId)
                    .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
            if (sale.getStatus() == PlotSale.Status.CANCELLED) {
                throw new BadRequestException("entityId", "SALE_CANCELLED", "error.document.saleCancelled");
            }
        } else if (docType == DocumentTemplate.DocType.DEMAND_LETTER) {
            if (!hasOverdueInstalment(entityId)) {
                throw new BadRequestException("entityId", "NO_OVERDUE_INSTALMENT", "error.document.noOverdueInstalment");
            }
        }
    }

    private boolean hasOverdueInstalment(UUID plotSaleId) {
        LocalDate today = IndianTime.today();
        return scheduleRepository.findByPlotSaleIdAndDeletedAtIsNullOrderBySequenceNoAsc(plotSaleId).stream()
                .anyMatch(row -> row.getDueDate().isBefore(today)
                        && (row.getStatus() == PaymentSchedule.Status.PENDING || row.getStatus() == PaymentSchedule.Status.PARTIALLY_PAID || row.getStatus() == PaymentSchedule.Status.OVERDUE));
    }

    private GeneratedDocument loadOwn(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        GeneratedDocument doc = documentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (!doc.getOrgId().equals(orgId)) throw new ResourceNotFoundException("error.notFound");
        return doc;
    }

    // Well-formed XHTML wrapper -- ITextRenderer needs a single root element
    // and self-closing void tags, unlike the loose HTML5 a browser tolerates.
    // Header/body/footer are placed in normal document flow (not CSS
    // position:fixed running headers) -- see CLAUDE.md for why running
    // headers were deliberately trimmed this round.
    private String wrapXhtml(String header, String body, String footer) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/><style type=\"text/css\">"
                + "@page { size: A4; margin: 2cm; } "
                + "body { font-family: '" + PdfRenderer.fontFamilyCss() + "', sans-serif; font-size: 11pt; line-height: 1.5; } "
                + "table { width: 100%; border-collapse: collapse; } "
                + "th, td { border: 1px solid #999; padding: 4px 8px; text-align: left; } "
                + ".footer { margin-top: 24px; font-size: 9pt; color: #555; }"
                + "</style></head><body>"
                + "<div class=\"header\">" + nullToEmpty(header) + "</div>"
                + "<div class=\"body\">" + body + "</div>"
                + "<div class=\"footer\">" + nullToEmpty(footer) + "</div>"
                + "</body></html>";
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private GeneratedDocumentResponse toResponse(GeneratedDocument d) {
        return new GeneratedDocumentResponse(d.getId(), d.getDocType().name(), d.getDocumentNumber(), d.getEntityType(),
                d.getEntityId(), d.getMediaId(), d.getLanguage(), d.getGeneratedBy(), d.getGeneratedAt());
    }
}
