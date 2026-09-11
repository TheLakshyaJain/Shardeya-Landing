package com.shardeya.builder.stats.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BrokerRanking(UUID brokerPartnerId, String brokerName, long dealsClosed, BigDecimal revenueGenerated) {
}
