package com.shardeya.platform;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset-pagination cursor (createdAt, id) — CLAUDE.md API Convention
 * "Lists: cursor pagination", 00-ARCHITECTURE.md §6 "LIMIT 51 cursor lists
 * (fetch n+1 to know hasMore without COUNT(*))". {@code createdAt DESC, id DESC}
 * is the sort every list endpoint in this milestone uses, so one cursor shape
 * covers all of them.
 */
public record Cursor(Instant createdAt, UUID id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt.toString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new BadRequestException("cursor", "CURSOR_INVALID", "error.cursor.invalid");
        }
    }
}
