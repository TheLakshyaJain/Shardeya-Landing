package com.shardeya.platform;

import java.util.List;
import java.util.function.Function;

public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    /** Trims the fetched n+1 rows down to n and builds the next cursor from the last kept row. */
    public static <T> CursorPage<T> of(List<T> fetched, int limit, Function<T, Cursor> cursorOf) {
        boolean hasMore = fetched.size() > limit;
        List<T> page = hasMore ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasMore ? cursorOf.apply(page.get(page.size() - 1)).encode() : null;
        return new CursorPage<>(page, nextCursor, hasMore);
    }
}
