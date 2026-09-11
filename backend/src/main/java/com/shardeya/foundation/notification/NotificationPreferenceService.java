package com.shardeya.foundation.notification;

import com.shardeya.foundation.notification.dto.NotificationPreferenceRow;
import com.shardeya.foundation.notification.dto.NotificationPreferenceUpdateRequest;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M-06 §22, {@code GET/PUT /api/v1/settings/notifications}. Backs
 * {@code NotificationPreferenceMatrix} (M-04 §6: "rows = event types
 * grouped by category, columns = 4 channel toggles, mandatory ones
 * locked"). {@link NotificationDispatchService} reads the exact same
 * {@link NotificationType}/{@link NotificationPreference} data this
 * service exposes -- the matrix a user edits here IS the thing that
 * decides what actually sends, not a separate settings-only view of it.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationTypeRepository typeRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final TenantContextBinder tenantContextBinder;

    public NotificationPreferenceService(NotificationTypeRepository typeRepository,
                                          NotificationPreferenceRepository preferenceRepository,
                                          TenantContextBinder tenantContextBinder) {
        this.typeRepository = typeRepository;
        this.preferenceRepository = preferenceRepository;
        this.tenantContextBinder = tenantContextBinder;
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceRow> matrix() {
        UUID userId = tenantContextBinder.current().userId();
        Map<String, NotificationPreference> overrides = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getTypeCode, p -> p));
        return typeRepository.findAll().stream()
                .map(type -> toRow(type, overrides.get(type.getCode())))
                .toList();
    }

    /**
     * Whole-row upserts, matching {@link NotificationPreference}'s own
     * javadoc ("once it exists every field is a complete, authoritative
     * snapshot, not a partial override") -- every update carries all four
     * channel values, never a single-field PATCH. A mandatory type's
     * {@code inApp} is force-set true regardless of what's requested here
     * (mirrors {@link NotificationDispatchService}'s own dispatch-time
     * enforcement -- CLAUDE.md rule #5's "server-side, not cosmetic"
     * extends to this business rule too, not just permissions).
     */
    @Transactional
    public void update(List<NotificationPreferenceUpdateRequest> updates) {
        UUID orgId = tenantContextBinder.currentOrgId();
        UUID userId = tenantContextBinder.current().userId();
        Map<String, NotificationPreference> existing = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getTypeCode, p -> p));
        Map<String, NotificationType> types = typeRepository.findAll().stream()
                .collect(Collectors.toMap(NotificationType::getCode, t -> t));

        for (NotificationPreferenceUpdateRequest req : updates) {
            NotificationType type = types.get(req.typeCode());
            // notification_preference.type_code has a real FK to
            // notification_type(code) (V7_014) -- an unknown typeCode was
            // previously left to hit that constraint at INSERT time,
            // surfacing as an unhandled DataIntegrityViolationException
            // (a genuine 500, caught live via the new /admin/errors view
            // during its own verification pass -- see CLAUDE.md "Post-M7 --
            // Minimal Ops Visibility"). The real client (GET /settings/notifications'
            // own response) never sends anything but a real catalogue code,
            // so this rejects malformed/stale requests cleanly instead.
            if (type == null) {
                throw new BadRequestException("typeCode", "UNKNOWN_NOTIFICATION_TYPE", "error.notification.unknownTypeCode");
            }
            boolean mandatory = type.isMandatory();
            NotificationPreference pref = existing.get(req.typeCode());
            if (pref == null) {
                pref = new NotificationPreference(UUID.randomUUID(), orgId, userId, req.typeCode());
            }
            pref.setInApp(mandatory || req.inApp());
            pref.setWhatsapp(req.whatsapp());
            pref.setSms(req.sms());
            pref.setEmail(req.email());
            preferenceRepository.save(pref);
        }
    }

    private NotificationPreferenceRow toRow(NotificationType type, NotificationPreference pref) {
        List<String> defaults = type.getDefaultChannels();
        if (pref != null) {
            return new NotificationPreferenceRow(type.getCode(), type.getCategory(), type.isMandatory(),
                    pref.isInApp(), pref.isWhatsapp(), pref.isSms(), pref.isEmail());
        }
        return new NotificationPreferenceRow(type.getCode(), type.getCategory(), type.isMandatory(),
                type.isMandatory() || defaults.contains("IN_APP"), defaults.contains("WHATSAPP"),
                defaults.contains("SMS"), defaults.contains("EMAIL"));
    }
}
