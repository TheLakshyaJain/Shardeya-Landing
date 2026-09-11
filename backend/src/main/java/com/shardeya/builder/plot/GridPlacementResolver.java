package com.shardeya.builder.plot;

import com.shardeya.builder.project.Project;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared auto-placement logic: parses a block/number plot-number pattern
 * (e.g. {@code A-12} -> block A, index 12) into a candidate grid position.
 * Extracted out of {@code ImportService} (where it originated, B-06 Path B)
 * so B-06 Path A (Quick Range Create) uses the exact same rule rather than a
 * second implementation that could silently drift from it.
 */
public final class GridPlacementResolver {

    private static final Pattern BLOCK_PATTERN = Pattern.compile("^([A-Za-z]+)[-\\s/]?(\\d+)$");

    private GridPlacementResolver() {
    }

    public record Position(int row, int col) {
    }

    /** Null if {@code plotNumber} doesn't match the block/number pattern at all. */
    public static Position parseBlockPattern(String plotNumber) {
        Matcher m = BLOCK_PATTERN.matcher(plotNumber.trim());
        if (!m.matches()) {
            return null;
        }
        int blockIndex = 0;
        for (char c : m.group(1).toUpperCase().toCharArray()) {
            blockIndex = blockIndex * 26 + (c - 'A' + 1);
        }
        int row = blockIndex - 1;
        int col = Integer.parseInt(m.group(2)) - 1;
        return new Position(row, col);
    }

    public static boolean isOutOfBounds(Project project, int row, int col) {
        return (project.getGridRows() != null && row >= project.getGridRows())
                || (project.getGridCols() != null && col >= project.getGridCols()) || row < 0 || col < 0;
    }
}
