package com.shardeya.shared;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * CLAUDE.md rule #11: "Never hardcode 'today' as UTC. Use IST (Asia/Kolkata)
 * for all business dates." Bare {@code LocalDate.now()} uses the JVM's
 * default zone, which is only IST by coincidence of server configuration,
 * not by guarantee -- every "is this overdue / is this date in the future"
 * business check (payment due dates, purchase dates, overdue detection)
 * must go through this instead.
 */
public final class IndianTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private IndianTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
