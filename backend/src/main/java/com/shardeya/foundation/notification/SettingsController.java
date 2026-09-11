package com.shardeya.foundation.notification;

import com.shardeya.foundation.notification.dto.NotificationPreferenceRow;
import com.shardeya.foundation.notification.dto.NotificationPreferenceUpdateRequest;
import com.shardeya.foundation.notification.dto.WhatsAppOptInChallengeResponse;
import com.shardeya.foundation.notification.dto.WhatsAppOptInConfirmRequest;
import com.shardeya.foundation.notification.dto.WhatsAppOptInStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** M-06 §22 settings endpoints -- M-04 §6's NotificationPreferenceMatrix + WhatsAppOptInCard. */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final NotificationPreferenceService preferenceService;
    private final WhatsAppOptInService whatsAppOptInService;

    public SettingsController(NotificationPreferenceService preferenceService, WhatsAppOptInService whatsAppOptInService) {
        this.preferenceService = preferenceService;
        this.whatsAppOptInService = whatsAppOptInService;
    }

    @GetMapping("/notifications")
    public List<NotificationPreferenceRow> matrix() {
        return preferenceService.matrix();
    }

    @PutMapping("/notifications")
    public ResponseEntity<Void> updateMatrix(@Valid @RequestBody List<NotificationPreferenceUpdateRequest> updates) {
        preferenceService.update(updates);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/whatsapp/opt-in/start")
    public WhatsAppOptInChallengeResponse startOptIn(HttpServletRequest http) {
        return whatsAppOptInService.start(http.getRemoteAddr());
    }

    @PostMapping("/whatsapp/opt-in/confirm")
    public ResponseEntity<Void> confirmOptIn(@Valid @RequestBody WhatsAppOptInConfirmRequest request) {
        whatsAppOptInService.confirm(request.challengeId(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/whatsapp/opt-out")
    public ResponseEntity<Void> optOut() {
        whatsAppOptInService.optOut();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/whatsapp/opt-in-status")
    public WhatsAppOptInStatusResponse optInStatus() {
        return whatsAppOptInService.status();
    }
}
