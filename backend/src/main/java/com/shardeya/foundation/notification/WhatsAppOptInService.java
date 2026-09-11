package com.shardeya.foundation.notification;

import com.shardeya.foundation.auth.AppUser;
import com.shardeya.foundation.auth.AppUserRepository;
import com.shardeya.foundation.auth.OtpService;
import com.shardeya.foundation.notification.dto.WhatsAppOptInChallengeResponse;
import com.shardeya.foundation.notification.dto.WhatsAppOptInStatusResponse;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * M-06 §22, {@code POST /api/v1/settings/whatsapp/opt-in}. Scoped to the
 * CURRENT USER'S OWN registered mobile ({@link AppUser#getMobile()}), not
 * an arbitrary caller-supplied number the way the M-06 spec's own
 * {@code {mobile}} parameter loosely suggests -- deliberately narrower, and
 * for a real reason: {@link NotificationDispatchService} only ever checks
 * {@code whatsapp_optin} for {@code user.getMobile()} when deciding whether
 * to send a builder-facing WhatsApp notification, so an opt-in row for any
 * other number would silently never be consulted by anything. Buyer-facing
 * opt-in (§22.4, a genuinely different, out-of-scope-for-this-build
 * concept -- see CLAUDE.md's M-06-second-half notes) is a different
 * consumer this table already supports via its {@code source} column
 * ("BUILDER_CAPTURED"), just not wired to any endpoint yet.
 *
 * <p>Two-step, OTP-confirmed (mirrors every other M1-era OTP flow in this
 * codebase -- {@link OtpService#create}/{@link OtpService#verify}): a
 * WhatsApp number consenting to receive messages has to actually prove it
 * can receive the code, the same way a phone number proves it during
 * signup.
 */
@Service
public class WhatsAppOptInService {

    public static final String PURPOSE_WHATSAPP_OPTIN = "WHATSAPP_OPTIN";

    private final OtpService otpService;
    private final WhatsAppOptinRepository optinRepository;
    private final AppUserRepository appUserRepository;
    private final TenantContextBinder tenantContextBinder;

    public WhatsAppOptInService(OtpService otpService, WhatsAppOptinRepository optinRepository,
                                 AppUserRepository appUserRepository, TenantContextBinder tenantContextBinder) {
        this.otpService = otpService;
        this.optinRepository = optinRepository;
        this.appUserRepository = appUserRepository;
        this.tenantContextBinder = tenantContextBinder;
    }

    public WhatsAppOptInChallengeResponse start(String ip) {
        String mobile = currentUserMobile();
        // Explicitly SMS -- WhatsApp opt-in inherently verifies a real,
        // WhatsApp-reachable phone number, unrelated to the Email OTP fix
        // that moved signup/login-OTP to email delivery.
        OtpService.ChallengeResult challenge = otpService.create(PURPOSE_WHATSAPP_OPTIN, OtpService.Channel.SMS, mobile, ip, null);
        return new WhatsAppOptInChallengeResponse(challenge.challengeId(), challenge.maskedRecipient(), challenge.resendAfterSeconds());
    }

    @Transactional
    public void confirm(String challengeId, String code) {
        OtpService.VerifyResult verified = otpService.verify(challengeId, code);
        if (!PURPOSE_WHATSAPP_OPTIN.equals(verified.purpose())) {
            throw new BadRequestException("code", "OTP_PURPOSE_MISMATCH", "error.otp.invalid");
        }
        UUID orgId = tenantContextBinder.currentOrgId();
        WhatsAppOptin optin = optinRepository.findByOrgIdAndMobile(orgId, verified.recipient()).orElse(null);
        if (optin == null) {
            optinRepository.save(new WhatsAppOptin(UUID.randomUUID(), orgId, verified.recipient(), "SELF_SERVICE"));
        } else {
            optin.reOptIn();
            optinRepository.save(optin);
        }
    }

    @Transactional
    public void optOut() {
        UUID orgId = tenantContextBinder.currentOrgId();
        String mobile = currentUserMobile();
        optinRepository.findByOrgIdAndMobile(orgId, mobile).ifPresent(optin -> {
            optin.optOut();
            optinRepository.save(optin);
        });
    }

    @Transactional(readOnly = true)
    public WhatsAppOptInStatusResponse status() {
        UUID orgId = tenantContextBinder.currentOrgId();
        String mobile = currentUserMobile();
        boolean optedIn = optinRepository.findByOrgIdAndMobile(orgId, mobile).map(WhatsAppOptin::isActive).orElse(false);
        return new WhatsAppOptInStatusResponse(mobile, optedIn);
    }

    private String currentUserMobile() {
        UUID orgId = tenantContextBinder.currentOrgId();
        UUID userId = tenantContextBinder.current().userId();
        AppUser user = appUserRepository.findByIdAndOrgIdAndDeletedAtIsNull(userId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (user.getMobile() == null || user.getMobile().isBlank()) {
            throw new BadRequestException("mobile", "NO_MOBILE_ON_FILE", "error.whatsappOptin.noMobile");
        }
        return user.getMobile();
    }
}
