package com.bentork.ev_system.dto.response;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadRevenueTrendResponse {

    private String period; // "monthly", "quarterly", "weekly", "yearly"
    private int year;
    private Long adminId;

    private List<LeadRevenueTrendPoint> trendPoints = new ArrayList<>();
    private TrendSummary summary = new TrendSummary();

    @Getter
    @Setter
    public static class TrendSummary {
        private long totalLeads = 0L;
        private long totalConvertedLeads = 0L;
        private double overallConversionRate = 0.0;

        private double totalRevenue = 0.0;
        private long totalWonDeals = 0L;

        private double averageMonthlyLeads = 0.0;
        private double averageMonthlyRevenue = 0.0;
        private double averageDealSize = 0.0;

        // Month-over-month growth compared to previous period
        private Double leadGrowthRate = 0.0; // Percentage change
        private Double revenueGrowthRate = 0.0; // Percentage change

        private String highestRevenuePeriod;
        private String highestLeadPeriod;
    }
}
