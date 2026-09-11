package com.shardeya.foundation.notification;

import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.platform.OutboxService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The single place that decides, per recipient and per channel, whether a
 * notification actually goes out -- replacing OutboxPoller's old direct
 * {@code notificationService.createForOrg/createForUser} calls (M-06
 * second half). Called from {@code OutboxPoller.sendNotification()}, inside
 * the same per-org REQUIRES_NEW transaction/tenant-context bind that method
 * already establishes -- this class does no transaction/tenant work of its
 * own.
 *
 * <p><b>Preference resolution:</b> a {@link NotificationPreference} row is
 * sparse at the per-type level (one row per (user, type) that the user has
 * ever touched via the settings screen), but once it exists every one of
 * its four boolean fields is a complete, authoritative snapshot -- not a
 * partial override merged field-by-field with the type's defaults. A
 * missing row falls back entirely to {@link NotificationType#getDefaultChannels()}.
 * {@link NotificationType#isMandatory()} only ever forces the in-app
 * channel on (M-06 §22's own wording: "still stored in-app... cannot be
 * silenced") -- a mandatory type's WhatsApp/SMS/email channels are still
 * whatever the user's own preference/defaults say.
 *
 * <p><b>WhatsApp opt-in + critical-type SMS fallback (§22.1):</b> WhatsApp
 * only ever sends to a mobile with an active {@link WhatsAppOptin} row.
 * When it's wanted but the recipient isn't opted in, {@link #CRITICAL_TYPES}
 * (money/security-adjacent: a payment recorded, an instalment due or
 * overdue, a bounced cheque) fall back to SMS automatically -- a safety
 * net, not something gated behind the recipient's own {@code sms}
 * preference toggle (which defaults to {@code false} and isn't in any
 * type's default channels today; gating the fallback on it would make the
 * fallback permanently unreachable in practice). Non-critical types simply
 * don't send when WhatsApp isn't available -- CLAUDE.md's own "no WhatsApp
 * outside quiet hours" instinct extends here to "don't substitute a paid
 * channel for an unavailable free one unless the notification is actually
 * important."
 *
 * <p><b>WhatsApp is a hard allowlist, owner-only (product decision, not the
 * original M-06 design):</b> {@link #WHATSAPP_ELIGIBLE_TYPES} is the
 * COMPLETE set of types that may ever reach the WhatsApp channel through
 * this dispatch path -- everything else is blocked regardless of what
 * {@link NotificationType#getDefaultChannels()} or a per-user
 * {@link NotificationPreference} row says (closing a real, if incidental,
 * gap: before this, ANY type could be made to send WhatsApp simply by
 * toggling it on in the settings matrix, since nothing there was tied to
 * the type's own defaults). The recipient is narrower still: WhatsApp
 * (and, as a direct consequence, its own SMS fallback above, since that
 * fallback only ever fires from inside the same {@code wantWhatsApp}
 * branch) only ever reaches {@link AppUser#isOwner()} -- every other user
 * in the org keeps getting the in-app copy exactly as before, just never
 * WhatsApp/SMS for these events. This does NOT apply to
 * {@code TrackerService}'s own manual, buyer-facing "Send Reminder" --
 * that path never goes through this class at all.
 *
 * <p>Quiet hours and per-tick rate limiting are NOT this class's concern --
 * quiet hours is applied once, centrally, inside {@link OutboxService#enqueueWhatsApp}/
 * {@link OutboxService#enqueueSms} (every caller gets it for free), and rate
 * limiting is a dispatch-time concern inside {@code OutboxPoller.dispatchReady()}.
 */
@Service
public class NotificationDispatchService {

    private static final Set<String> CRITICAL_TYPES = Set.of(
            "PAYMENT_RECORDED", "INSTALMENT_OVERDUE", "INSTALMENT_DUE_TODAY", "CHEQUE_BOUNCED");

    private static final Set<String> WHATSAPP_ELIGIBLE_TYPES = Set.of(
            "INSTALMENT_DUE_TODAY", "INSTALMENT_OVERDUE", "COMMISSION_DUE");

    private final NotificationService notificationService;
    private final NotificationTypeRepository typeRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final WhatsAppOptinRepository optinRepository;
    private final AppUserRepository appUserRepository;
    private final OutboxService outboxService;

    public NotificationDispatchService(NotificationService notificationService, NotificationTypeRepository typeRepository,
                                        NotificationPreferenceRepository preferenceRepository,
                                        WhatsAppOptinRepository optinRepository, AppUserRepository appUserRepository,
                                        OutboxService outboxService) {
        this.notificationService = notificationService;
        this.typeRepository = typeRepository;
        this.preferenceRepository = preferenceRepository;
        this.optinRepository = optinRepository;
        this.appUserRepository = appUserRepository;
        this.outboxService = outboxService;
    }

    public void dispatch(UUID orgId, UUID recipientUserId, String typeCode, String titleKey, String bodyKey,
                          Map<String, Object> params, String entityType, UUID entityId) {
        List<AppUser> recipients = recipientUserId != null
                ? appUserRepository.findByIdAndOrgIdAndDeletedAtIsNull(recipientUserId, orgId).map(List::of).orElse(List.of())
                : appUserRepository.findByOrgIdAndDeletedAtIsNull(orgId);
        NotificationType type = typeRepository.findById(typeCode).orElse(null);
        for (AppUser user : recipients) {
            dispatchToUser(orgId, user, type, typeCode, titleKey, bodyKey, params, entityType, entityId);
        }
    }

    private void dispatchToUser(UUID orgId, AppUser user, NotificationType type, String typeCode, String titleKey,
                                 String bodyKey, Map<String, Object> params, String entityType, UUID entityId) {
        NotificationPreference pref = preferenceRepository.findByUserIdAndTypeCode(user.getId(), typeCode).orElse(null);
        boolean mandatory = type != null && type.isMandatory();
        List<String> defaults = type != null ? type.getDefaultChannels() : List.of("IN_APP");

        boolean wantInApp = mandatory || (pref != null ? pref.isInApp() : defaults.contains("IN_APP"));
        boolean wantWhatsApp = user.isOwner() && WHATSAPP_ELIGIBLE_TYPES.contains(typeCode)
                && (pref != null ? pref.isWhatsapp() : defaults.contains("WHATSAPP"));
        boolean wantSms = pref != null ? pref.isSms() : defaults.contains("SMS");
        boolean wantEmail = pref != null ? pref.isEmail() : defaults.contains("EMAIL");

        if (wantInApp) {
            notificationService.createForUser(orgId, user.getId(), typeCode, titleKey, bodyKey, params, entityType, entityId);
        }

        boolean smsQueued = false;
        String mobile = user.getMobile();
        if (wantWhatsApp && mobile != null && !mobile.isBlank()) {
            boolean optedIn = optinRepository.findByOrgIdAndMobile(orgId, mobile).map(WhatsAppOptin::isActive).orElse(false);
            if (optedIn) {
                NotificationMessageRenderer.RenderedMessage msg = NotificationMessageRenderer.render(typeCode, params, user.getLanguage());
                outboxService.enqueueWhatsApp(orgId, entityType, entityId, mobile, msg.body(), typeCode);
            } else if (CRITICAL_TYPES.contains(typeCode)) {
                NotificationMessageRenderer.RenderedMessage msg = NotificationMessageRenderer.render(typeCode, params, user.getLanguage());
                outboxService.enqueueSms(orgId, entityType, entityId, mobile, msg.body(), typeCode);
                smsQueued = true;
            }
        }

        if (wantSms && !smsQueued && mobile != null && !mobile.isBlank()) {
            NotificationMessageRenderer.RenderedMessage msg = NotificationMessageRenderer.render(typeCode, params, user.getLanguage());
            outboxService.enqueueSms(orgId, entityType, entityId, mobile, msg.body(), typeCode);
        }

        String email = user.getEmail();
        if (wantEmail && email != null && !email.isBlank()) {
            NotificationMessageRenderer.RenderedMessage msg = NotificationMessageRenderer.render(typeCode, params, user.getLanguage());
            String subject = msg.subject() != null ? msg.subject() : "Shardeya notification";
            outboxService.enqueueEmail(orgId, entityType, entityId, email, subject, msg.body());
        }
    }
}
