package com.bentork.ev_system.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadRevenueTrendPoint {

    private String period; // e.g. "2026-10"
    private String periodLabel; // e.g. "Oct 2026"
    private int year;
    private int month;
    private String monthName; // e.g. "October"

    // Lead metrics
    private long totalLeads = 0L;
    private long newLeads = 0L;
    private long contactedLeads = 0L;
    private long qualifiedLeads = 0L;
    private long convertedLeads = 0L;
    private long lostLeads = 0L;
    private double leadConversionRate = 0.0; // Percentage

    // Revenue metrics
    private double totalRevenue = 0.0; // Closed / Won deal revenue in ₹
    private double expectedRevenue = 0.0; // Weighted pipeline revenue
    private double pipelineValue = 0.0; // Total active pipeline value

    // Deal metrics
    private long wonDeals = 0L;
    private long totalOpportunities = 0L;
    private double averageDealSize = 0.0;
}
