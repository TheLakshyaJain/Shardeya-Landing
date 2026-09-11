package com.shardeya.builder.document;

import com.shardeya.builder.document.dto.CloneTemplateRequest;
import com.shardeya.builder.document.dto.DocumentTemplateResponse;
import com.shardeya.builder.document.dto.DocumentTemplateUpsertRequest;
import com.shardeya.builder.document.dto.TemplatePreviewRequest;
import com.shardeya.builder.document.dto.TemplatePreviewResponse;
import com.shardeya.builder.document.dto.VariablePaletteResponse;
import com.shardeya.foundation.subscription.EntitlementService;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ConflictException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * B-11 §17.2. CRUD + activation for org-owned document templates; system
 * defaults (org_id NULL, seeded V7_010/V7_011) are read-only from this
 * service's point of view -- {@link #clone_} is the only way an org gets
 * something it can actually edit, matching the API spec's own
 * {@code POST /documents/templates -> clone a system default for editing}.
 */
@Service
public class DocumentTemplateService {

    private final DocumentTemplateRepository repository;
    private final TenantContextBinder tenantContextBinder;
    private final TemplateRenderer renderer;
    private final DocumentContextBuilder contextBuilder;
    private final EntityManager entityManager;
    private final EntitlementService entitlementService;

    public DocumentTemplateService(DocumentTemplateRepository repository, TenantContextBinder tenantContextBinder,
                                    TemplateRenderer renderer, DocumentContextBuilder contextBuilder, EntityManager entityManager,
                                    EntitlementService entitlementService) {
        this.repository = repository;
        this.tenantContextBinder = tenantContextBinder;
        this.renderer = renderer;
        this.contextBuilder = contextBuilder;
        this.entityManager = entityManager;
        this.entitlementService = entitlementService;
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateResponse> list() {
        UUID orgId = tenantContextBinder.currentOrgId();
        return repository.findAllVisibleToOrg(orgId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DocumentTemplateResponse get(UUID id) {
        return toResponse(loadVisible(id));
    }

    @Transactional
    public DocumentTemplateResponse clone_(CloneTemplateRequest req) {
        UUID orgId = tenantContextBinder.currentOrgId();
        entitlementService.assertTierAtLeast(orgId, LegalDocsTier.KEY, LegalDocsTier.ORDERED, "FULL", "error.documentTemplate.customTemplatesRequireFull");
        DocumentTemplate.DocType docType = parseDocType(req.docType());
        String language = parseLanguage(req.language());

        DocumentTemplate systemDefault = repository.findActiveSystemTemplate(docType, language)
                .orElseThrow(() -> new ResourceNotFoundException("error.documentTemplate.noSystemDefault"));

        String name = systemDefault.getName() + " (Custom)";
        DocumentTemplate clone = new DocumentTemplate(UUID.randomUUID(), orgId, docType, name, language,
                systemDefault.getBodyHtml(), tenantContextBinder.current().userId());
        clone.setHeaderHtml(systemDefault.getHeaderHtml());
        clone.setFooterHtml(systemDefault.getFooterHtml());
        clone.setVariables(systemDefault.getVariables());
        // Cloned copies start inactive -- the org's own template only takes
        // over generation once explicitly activated (B-11 §8 user flow:
        // edit -> preview -> "looks right" -> Activate), never silently on clone.
        clone = repository.save(clone);
        entityManager.flush();
        entityManager.refresh(clone);
        return toResponse(clone);
    }

    @Transactional
    public DocumentTemplateResponse update(UUID id, DocumentTemplateUpsertRequest req) {
        DocumentTemplate template = loadOwnMutable(id);
        template.setName(req.name());
        template.setBodyHtml(req.bodyHtml());
        template.setHeaderHtml(req.headerHtml());
        template.setFooterHtml(req.footerHtml());
        template.setVariables(writeVariablesJson(renderer.distinctTopLevelPaths(req.bodyHtml(), req.headerHtml(), req.footerHtml())));
        // Editing an already-active template does NOT retroactively touch any
        // generated_document -- B-11 §11 "template edited after documents were
        // generated -> old documents unchanged (snapshot + template version)".
        // The @Version bump this save() triggers IS the new template_version
        // the next generation will snapshot; nothing here needs to re-validate
        // the allowlist -- that only happens at activate().
        repository.save(template);
        return toResponse(template);
    }

    @Transactional
    public DocumentTemplateResponse activate(UUID id) {
        DocumentTemplate template = loadOwnMutable(id);
        Optional<String> offending = renderer.firstUnresolvableVariable(template.getDocType(),
                template.getBodyHtml(), template.getHeaderHtml(), template.getFooterHtml());
        if (offending.isPresent()) {
            // B-11 §11: "activation blocked with the offending variable
            // named; never generate 'Dear {{buyer.name}}'" -- the whole
            // point of validating here rather than at generation time.
            throw new BadRequestException(List.of(new com.shardeya.platform.ApiError("body", "UNKNOWN_TEMPLATE_VARIABLE",
                    "error.documentTemplate.unknownVariable", Map.of("variable", offending.get()))));
        }
        UUID orgId = tenantContextBinder.currentOrgId();
        for (DocumentTemplate other : repository.findActiveInSlot(orgId, template.getDocType(), template.getLanguage())) {
            if (!other.getId().equals(template.getId())) {
                other.setActive(false);
                repository.save(other);
            }
        }
        template.setActive(true);
        repository.save(template);
        return toResponse(template);
    }

    @Transactional(readOnly = true)
    public TemplatePreviewResponse preview(UUID id, TemplatePreviewRequest req) {
        DocumentTemplate template = loadVisible(id);
        Map<String, Object> context = contextBuilder.buildContext(template.getDocType(), req.sampleEntityId());
        // No real document has been generated yet at preview time --
        // {{document.number}} shows a placeholder rather than allocating a
        // real gapless number just for a preview render (which would burn a
        // sequence value on every keystroke-driven preview call).
        @SuppressWarnings("unchecked")
        Map<String, Object> documentCtx = (Map<String, Object>) context.get("document");
        documentCtx.put("number", "PREVIEW");
        String header = renderer.render(template.getHeaderHtml(), context);
        String body = renderer.render(template.getBodyHtml(), context);
        String footer = renderer.render(template.getFooterHtml(), context);
        return new TemplatePreviewResponse(header + body + footer);
    }

    @Transactional(readOnly = true)
    public VariablePaletteResponse variablePalette(String docTypeStr) {
        DocumentTemplate.DocType docType = parseDocType(docTypeStr);
        List<String> topLevel = new ArrayList<>(DocumentVariableAllowlist.topLevelVariables(docType));
        Map<String, List<String>> collections = DocumentVariableAllowlist.collections(docType).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));
        return new VariablePaletteResponse(topLevel, collections);
    }

    // Package-visible for DocumentGenerationService's own resolution.
    DocumentTemplate loadVisible(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        DocumentTemplate template = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (template.getOrgId() != null && !template.getOrgId().equals(orgId)) {
            throw new ResourceNotFoundException("error.notFound");
        }
        return template;
    }

    private DocumentTemplate loadOwnMutable(UUID id) {
        UUID orgId = tenantContextBinder.currentOrgId();
        DocumentTemplate template = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (template.getOrgId() == null) {
            // System defaults are never directly editable -- clone first.
            throw new ConflictException("error.documentTemplate.systemDefaultReadOnly", Map.of());
        }
        if (!template.getOrgId().equals(orgId)) {
            throw new ResourceNotFoundException("error.notFound");
        }
        return template;
    }

    private String writeVariablesJson(java.util.Set<String> paths) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(paths);
        } catch (Exception e) {
            return "[]";
        }
    }

    static DocumentTemplate.DocType parseDocType(String value) {
        try {
            return DocumentTemplate.DocType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("docType", "INVALID_DOC_TYPE", "error.documentTemplate.invalidDocType");
        }
    }

    static String parseLanguage(String value) {
        if (!"en".equals(value) && !"hi".equals(value)) {
            throw new BadRequestException("language", "INVALID_LANGUAGE", "error.documentTemplate.invalidLanguage");
        }
        return value;
    }

    private DocumentTemplateResponse toResponse(DocumentTemplate t) {
        List<String> variables;
        try {
            variables = List.of(new com.fasterxml.jackson.databind.ObjectMapper().readValue(t.getVariables(), String[].class));
        } catch (Exception e) {
            variables = List.of();
        }
        return new DocumentTemplateResponse(t.getId(), t.getOrgId(), t.getOrgId() == null, t.getDocType().name(),
                t.getName(), t.getLanguage(), t.getBodyHtml(), t.getHeaderHtml(), t.getFooterHtml(), variables,
                t.getVersion(), t.isActive(), null);
    }
}
