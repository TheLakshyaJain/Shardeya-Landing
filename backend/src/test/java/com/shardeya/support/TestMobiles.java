package com.shardeya.support;

import java.util.UUID;

/**
 * Generates a plausible 10-digit mobile number guaranteed unique enough for
 * test fixtures -- {@code app_user.mobile}/{@code broker_partner.mobile}
 * both carry real unique constraints, and this is what backs them.
 *
 * <p>Neither {@code System.nanoTime()} (its high-order digits barely move
 * between calls microseconds apart -- collides within a tight seeding loop
 * in one test method) nor a per-test-class {@code AtomicInteger} counter
 * (each class's own counter restarts at 0, so two different test classes
 * seeding brokers in the same JVM/DB run can independently generate the
 * identical mobile number -- caught for real: {@code DesignationPromotionIntegrationTest}
 * and {@code DesignationPromotionConcurrencyIntegrationTest} both started
 * their own counter at 0 and collided the moment the full suite ran them
 * together) hold up. A fresh {@link UUID#randomUUID()} per call has 128
 * bits of real entropy and no shared counter state to collide across
 * classes, methods, or threads.
 */
public final class TestMobiles {

    private TestMobiles() {
    }

    public static String next() {
        long bits = UUID.randomUUID().getMostSignificantBits();
        long digits = Math.abs(bits) % 1_000_000_000L;
        return "9" + String.format("%09d", digits);
    }
}
