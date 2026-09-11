-- B-11 §12. In-app only via the existing OutboxService.enqueueNotification
-- bell pipeline (live since M3) -- NOT the WhatsApp/SMS/email channel
-- adapters, which are M-06's own scope for the second half of this
-- milestone and deliberately untouched here (see CLAUDE.md).
INSERT INTO notification_type (code, category, template_key_en, template_key_hi, description) VALUES
    ('DOCUMENT_GENERATED', 'DOCUMENT', 'notification.documentGenerated', 'notification.documentGenerated', 'A legal document PDF was generated and is ready for download'),
    ('BULK_GENERATION_COMPLETE', 'DOCUMENT', 'notification.bulkGenerationComplete', 'notification.bulkGenerationComplete', 'A bulk document generation job finished'),
    ('DOCUMENT_GENERATION_FAILED', 'DOCUMENT', 'notification.documentGenerationFailed', 'notification.documentGenerationFailed', 'Document generation failed'),
    ('TEMPLATE_ACTIVATED', 'DOCUMENT', 'notification.templateActivated', 'notification.templateActivated', 'A document template was activated')
ON CONFLICT (code) DO NOTHING;
