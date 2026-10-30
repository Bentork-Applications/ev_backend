package com.bentork.ev_system.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTargetResponse {

    private Long id;
    private int year;
    private int month;
    private String period; // "2026-10"
    private String monthName; // "October"

    // Revenue metrics
    private Double targetRevenue = 0.0;
    private Double achievedRevenue = 0.0;
    private Double revenueAchievementRate = 0.0; // Percentage, e.g. 85.5%
    private Double remainingRevenue = 0.0;

    // Lead metrics
    private Integer targetLeads = 0;
    private Long achievedLeads = 0L;
    private Double leadsAchievementRate = 0.0;
    private Long remainingLeads = 0L;

    // Deal / Opportunity metrics
    private Integer targetDeals = 0;
    private Long achievedDeals = 0L;
    private Double dealsAchievementRate = 0.0;
    private Long remainingDeals = 0L;

    // Unit metrics (battery packs/products sold)
    private Integer targetUnits = 0;
    private Integer achievedUnits = 0;

    // Progress status: "ACHIEVED", "ON_TRACK", "IN_PROGRESS", "BEHIND"
    private String status;

    // Sales Rep Details (null for company-wide target)
    private Long adminId;
    private String adminName;

    // Active pipeline info for the period
    private Double pipelineValue = 0.0;
    private Double pipelineCoverage = 0.0; // e.g. 1.5x of target

    private String notes;
}
