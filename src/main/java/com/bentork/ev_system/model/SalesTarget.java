package com.bentork.ev_system.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents monthly sales targets for the sales team or individual sales executives.
 */
@Entity
@Table(name = "sales_targets", indexes = {
        @Index(name = "idx_sales_target_year_month", columnList = "year, month"),
        @Index(name = "idx_sales_target_admin", columnList = "adminId"),
        @Index(name = "idx_sales_target_period", columnList = "period")
})
@Getter
@Setter
public class SalesTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private int month; // 1 to 12

    @Column(nullable = false)
    private String period; // e.g. "2026-10"

    private String monthName; // e.g. "October"

    // Revenue target in INR (₹)
    @Column(nullable = false)
    private Double targetRevenue = 0.0;

    // Target count of leads to acquire
    @Column(nullable = false)
    private Integer targetLeads = 0;

    // Target count of won opportunities / deals
    @Column(nullable = false)
    private Integer targetDeals = 0;

    // Optional target units / battery packs sold
    private Integer targetUnits = 0;

    // If null, this is an overall company-wide target.
    // If set, this is a personalized target for a specific sales rep.
    private Long adminId;

    private String adminName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private String createdByAdminEmail;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.period == null && this.year > 0 && this.month > 0) {
            this.period = String.format("%04d-%02d", this.year, this.month);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
