package com.shardeya.foundation.importexport;

import java.util.List;

/**
 * B-06 §7 template columns. Sold-row/buyer columns (buyer_name, buyer_mobile,
 * deal_value, etc.) are deliberately NOT included this milestone: a SOLD
 * plot can only exist as a side effect of creating a plot_sale, which
 * doesn't exist until M3 (same rule PlotService enforces for manual create/
 * edit — see its own comment). Importing a SOLD row is a clear validation
 * error rather than a silently-ignored column, directing the user to import
 * as AVAILABLE/RESERVED for now and mark plots sold once that flow lands.
 */
public final class PlotImportColumns {

    public static final String TEMPLATE_VERSION = "plot-v1";

    public static final List<String> HEADERS = List.of(
            "plot_number", "status", "reserved_for", "size_value", "size_unit",
            "facing", "price", "is_garden", "is_corner", "is_hot", "remarks", "grid_row", "grid_col"
    );

    public static final List<String> STATUS_VALUES = List.of("AVAILABLE", "RESERVED");
    public static final List<String> FACING_VALUES = List.of("N", "S", "E", "W", "NE", "NW", "SE", "SW");

    private PlotImportColumns() {
    }
}
