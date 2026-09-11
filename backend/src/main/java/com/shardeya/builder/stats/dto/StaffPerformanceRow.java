package com.shardeya.builder.stats.dto;

import java.util.UUID;

public record StaffPerformanceRow(UUID userId, String staffName, long leadsHandled, long dealsClosed, long followUpsLogged, long paymentsRecorded) {
}
