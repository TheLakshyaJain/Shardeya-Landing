package com.shardeya.builder.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * B-01 §3's write-through dashboard aggregate -- see V5_002's triggers for
 * how every column here stays current without ever running COUNT(*) on
 * render (CLAUDE.md's own explicitly-flagged pitfall #1). Read-only from the
 * application's point of view; every column is maintained exclusively by
 * database triggers, never written to directly from Java.
 */
@Entity
@Table(name = "org_metrics")
public class OrgMetrics {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "total_projects", nullable = false)
    private int totalProjects;

    @Column(name = "total_plots", nullable = false)
    private int totalPlots;

    @Column(name = "available_plots", nullable = false)
    private int availablePlots;

    @Column(name = "sold_plots", nullable = false)
    private int soldPlots;

    @Column(name = "reserved_plots", nullable = false)
    private int reservedPlots;

    @Column(name = "active_leads", nullable = false)
    private int activeLeads;

    @Column(name = "total_revenue", nullable = false)
    private BigDecimal totalRevenue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrgMetrics() {
    }

    public UUID getOrgId() {
        return orgId;
    }

    public int getTotalProjects() {
        return totalProjects;
    }

    public int getTotalPlots() {
        return totalPlots;
    }

    public int getAvailablePlots() {
        return availablePlots;
    }

    public int getSoldPlots() {
        return soldPlots;
    }

    public int getReservedPlots() {
        return reservedPlots;
    }

    public int getActiveLeads() {
        return activeLeads;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
