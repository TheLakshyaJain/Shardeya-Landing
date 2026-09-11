package com.shardeya.foundation.notification;

import com.shardeya.shared.IndianTime;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * M-06 §22 quiet hours: "no WhatsApp/SMS before 08:00 or after 21:00 IST;
 * queued to the next window." Applied once, centrally, inside
 * {@code OutboxService.enqueueWhatsApp}/{@code enqueueSms} rather than
 * re-checked at dispatch time -- an event enqueued outside the window is
 * simply stamped with its next allowed {@code available_at} up front, so
 * the existing {@code findReadyToDispatch(status, now)} query naturally
 * doesn't pick it up early. Email is intentionally exempt (§22 only names
 * WhatsApp/SMS).
 */
public final class QuietHoursPolicy {

    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(21, 0);

    private QuietHoursPolicy() {
    }

    public static Instant nextAllowedInstant(Instant from) {
        ZonedDateTime ist = from.atZone(IndianTime.ZONE);
        LocalTime time = ist.toLocalTime();
        if (!time.isBefore(START) && time.isBefore(END)) {
            return from;
        }
        ZonedDateTime nextWindowStart = time.isBefore(START) ? ist.with(START) : ist.plusDays(1).with(START);
        return nextWindowStart.toInstant();
    }
}
