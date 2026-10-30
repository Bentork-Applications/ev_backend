package com.bentork.ev_system.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.bentork.ev_system.dto.response.LeadRevenueTrendPoint;
import com.bentork.ev_system.dto.response.LeadRevenueTrendResponse;
import com.bentork.ev_system.dto.response.LeadRevenueTrendResponse.TrendSummary;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.Opportunity;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.OpportunityRepository;
import com.bentork.ev_system.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service providing historical analytics, trends, and conversion metrics for Sales CRM.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrmAnalyticsService {

    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final OrderRepository orderRepository;

    /**
     * Generate historical lead and revenue trends for monthly, quarterly, or custom period.
     *
     * @param period      Period type ("monthly", "quarterly", "yearly")
     * @param year        Target calendar year (default current year if not specified)
     * @param limitMonths Number of trailing months if year is not provided (default 12)
     * @param adminId     Optional filter for specific sales executive
     * @return Trend data points with summary metrics
     */
    public LeadRevenueTrendResponse getLeadRevenueTrend(String period, Integer year, Integer limitMonths, Long adminId) {
        String normalizedPeriod = (period != null && !period.isBlank()) ? period.toLowerCase().trim() : "monthly";
        int targetYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        int monthsCount = (limitMonths != null && limitMonths > 0) ? Math.min(limitMonths, 24) : 12;

        LeadRevenueTrendResponse response = new LeadRevenueTrendResponse();
        response.setPeriod(normalizedPeriod);
        response.setYear(targetYear);
        response.setAdminId(adminId);

        List<LeadRevenueTrendPoint> points = new ArrayList<>();

        if ("quarterly".equals(normalizedPeriod)) {
            points = computeQuarterlyTrends(targetYear, adminId);
        } else {
            // Default: monthly trend points
            points = computeMonthlyTrends(targetYear, monthsCount, year != null, adminId);
        }

        response.setTrendPoints(points);
        response.setSummary(computeSummary(points));

        return response;
    }

    /**
     * Compute monthly trend points.
     */
    private List<LeadRevenueTrendPoint> computeMonthlyTrends(int year, int monthsCount, boolean fullCalendarYear, Long adminId) {
        List<LeadRevenueTrendPoint> points = new ArrayList<>();

        if (fullCalendarYear) {
            // All 12 months of the given year
            for (int m = 1; m <= 12; m++) {
                LocalDateTime start = LocalDateTime.of(year, m, 1, 0, 0, 0);
                LocalDateTime end = start.plusMonths(1);
                points.add(buildPointForPeriod(start, end, year, m, adminId));
            }
        } else {
            // Trailing N months up to current month
            LocalDate now = LocalDate.now();
            LocalDate startMonth = now.minusMonths(monthsCount - 1).withDayOfMonth(1);

            for (int i = 0; i < monthsCount; i++) {
                LocalDate cur = startMonth.plusMonths(i);
                LocalDateTime start = cur.atStartOfDay();
                LocalDateTime end = start.plusMonths(1);
                points.add(buildPointForPeriod(start, end, cur.getYear(), cur.getMonthValue(), adminId));
            }
        }

        return points;
    }

    /**
     * Compute quarterly trend points.
     */
    private List<LeadRevenueTrendPoint> computeQuarterlyTrends(int year, Long adminId) {
        List<LeadRevenueTrendPoint> points = new ArrayList<>();

        for (int q = 1; q <= 4; q++) {
            int startMonth = (q - 1) * 3 + 1;
            LocalDateTime start = LocalDateTime.of(year, startMonth, 1, 0, 0, 0);
            LocalDateTime end = start.plusMonths(3);

            LeadRevenueTrendPoint point = buildPointForPeriod(start, end, year, startMonth, adminId);
            point.setPeriod(String.format("%04d-Q%d", year, q));
            point.setPeriodLabel(String.format("Q%d %d", q, year));
            point.setMonthName(String.format("Quarter %d", q));
            points.add(point);
        }

        return points;
    }

    /**
     * Build a single trend point for a given date window.
     */
    private LeadRevenueTrendPoint buildPointForPeriod(LocalDateTime start, LocalDateTime end, int year, int month, Long adminId) {
        LeadRevenueTrendPoint point = new LeadRevenueTrendPoint();
        point.setYear(year);
        point.setMonth(month);
        point.setPeriod(String.format("%04d-%02d", year, month));
        Month mEnum = Month.of(month);
        point.setMonthName(mEnum.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        point.setPeriodLabel(mEnum.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year);

        // 1. Leads in this window
        List<Lead> leads = (adminId == null)
                ? leadRepository.findByCreatedAtBetween(start, end)
                : leadRepository.findByOwnerAdminIdAndCreatedAtBetween(adminId, start, end);

        long totalLeads = leads.size();
        long newLeads = 0L;
        long contactedLeads = 0L;
        long qualifiedLeads = 0L;
        long convertedLeads = 0L;
        long lostLeads = 0L;

        for (Lead l : leads) {
            String status = l.getStatus() != null ? l.getStatus().toLowerCase() : "";
            switch (status) {
                case "new" -> newLeads++;
                case "contacted" -> contactedLeads++;
                case "qualified" -> qualifiedLeads++;
                case "converted" -> convertedLeads++;
                case "lost" -> lostLeads++;
            }
            if (l.getConvertedToOpportunityId() != null && !status.equals("converted")) {
                convertedLeads++;
            }
        }

        point.setTotalLeads(totalLeads);
        point.setNewLeads(newLeads);
        point.setContactedLeads(contactedLeads);
        point.setQualifiedLeads(qualifiedLeads);
        point.setConvertedLeads(convertedLeads);
        point.setLostLeads(lostLeads);

        double conversionRate = (totalLeads > 0) ? round(((double) convertedLeads / totalLeads) * 100.0) : 0.0;
        point.setLeadConversionRate(conversionRate);

        // 2. Won Deals & Revenue in this window
        List<Opportunity> wonOpps = (adminId == null)
                ? opportunityRepository.findWonOpportunitiesInPeriod("won", start, end)
                : opportunityRepository.findWonOpportunitiesInPeriodForAdmin(adminId, "won", start, end);

        long wonDeals = wonOpps.size();
        double wonRevenue = 0.0;
        for (Opportunity opp : wonOpps) {
            if (opp.getValue() != null) {
                wonRevenue += opp.getValue();
            }
        }

        // If company-wide and no opportunity revenue, check order revenue
        if (adminId == null && wonRevenue == 0.0) {
            Double orderRev = orderRepository.sumTotalInvoiceAmountInPeriod(start, end);
            if (orderRev != null && orderRev > 0) {
                wonRevenue = orderRev;
            }
        }

        point.setWonDeals(wonDeals);
        point.setTotalRevenue(round(wonRevenue));
        point.setAverageDealSize(wonDeals > 0 ? round(wonRevenue / wonDeals) : 0.0);

        // 3. Pipeline in this window
        List<Opportunity> createdOpps = opportunityRepository.findByCreatedAtBetween(start, end);
        point.setTotalOpportunities(createdOpps.size());

        Double pipelineVal = opportunityRepository.sumActivePipelineValueInPeriod(start, end);
        point.setPipelineValue(pipelineVal != null ? round(pipelineVal) : 0.0);
        point.setExpectedRevenue(point.getPipelineValue() * 0.5); // Weighted estimate

        return point;
    }

    /**
     * Compute aggregated trend summary and growth rates.
     */
    private TrendSummary computeSummary(List<LeadRevenueTrendPoint> points) {
        TrendSummary summary = new TrendSummary();
        if (points == null || points.isEmpty()) {
            return summary;
        }

        long totalLeads = 0L;
        long totalConverted = 0L;
        double totalRev = 0.0;
        long totalDeals = 0L;

        double maxRev = -1.0;
        String highestRevPeriod = "";
        long maxLeads = -1L;
        String highestLeadPeriod = "";

        for (LeadRevenueTrendPoint p : points) {
            totalLeads += p.getTotalLeads();
            totalConverted += p.getConvertedLeads();
            totalRev += p.getTotalRevenue();
            totalDeals += p.getWonDeals();

            if (p.getTotalRevenue() > maxRev) {
                maxRev = p.getTotalRevenue();
                highestRevPeriod = p.getPeriodLabel();
            }

            if (p.getTotalLeads() > maxLeads) {
                maxLeads = p.getTotalLeads();
                highestLeadPeriod = p.getPeriodLabel();
            }
        }

        int count = points.size();
        summary.setTotalLeads(totalLeads);
        summary.setTotalConvertedLeads(totalConverted);
        summary.setOverallConversionRate(totalLeads > 0 ? round(((double) totalConverted / totalLeads) * 100.0) : 0.0);
        summary.setTotalRevenue(round(totalRev));
        summary.setTotalWonDeals(totalDeals);
        summary.setAverageMonthlyLeads(round((double) totalLeads / count));
        summary.setAverageMonthlyRevenue(round(totalRev / count));
        summary.setAverageDealSize(totalDeals > 0 ? round(totalRev / totalDeals) : 0.0);
        summary.setHighestRevenuePeriod(highestRevPeriod);
        summary.setHighestLeadPeriod(highestLeadPeriod);

        // Growth rate: compare last point with second to last point
        if (count >= 2) {
            LeadRevenueTrendPoint latest = points.get(count - 1);
            LeadRevenueTrendPoint prev = points.get(count - 2);

            double leadGrowth = (prev.getTotalLeads() > 0)
                    ? ((double) (latest.getTotalLeads() - prev.getTotalLeads()) / prev.getTotalLeads()) * 100.0
                    : (latest.getTotalLeads() > 0 ? 100.0 : 0.0);
            summary.setLeadGrowthRate(round(leadGrowth));

            double revGrowth = (prev.getTotalRevenue() > 0)
                    ? ((latest.getTotalRevenue() - prev.getTotalRevenue()) / prev.getTotalRevenue()) * 100.0
                    : (latest.getTotalRevenue() > 0 ? 100.0 : 0.0);
            summary.setRevenueGrowthRate(round(revGrowth));
        }

        return summary;
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
