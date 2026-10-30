package com.bentork.ev_system.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.dto.request.SalesTargetDTO;
import com.bentork.ev_system.dto.response.SalesTargetResponse;
import com.bentork.ev_system.dto.response.SalesTargetsOverviewResponse;
import com.bentork.ev_system.dto.response.SalesTargetsOverviewResponse.YearSummary;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.SalesTarget;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.OpportunityRepository;
import com.bentork.ev_system.repository.OrderRepository;
import com.bentork.ev_system.repository.SalesTargetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrmTargetService {

    private final SalesTargetRepository salesTargetRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final OrderRepository orderRepository;
    private final AdminRepository adminRepository;

    // Default baseline targets if none explicitly set in DB
    private static final double DEFAULT_MONTHLY_REVENUE_TARGET = 5000000.0; // ₹50 Lakhs
    private static final int DEFAULT_MONTHLY_LEADS_TARGET = 50;
    private static final int DEFAULT_MONTHLY_DEALS_TARGET = 10;
    private static final int DEFAULT_MONTHLY_UNITS_TARGET = 20;

    /**
     * Get monthly sales targets and target achievements for a given year and optional month/admin.
     */
    public SalesTargetsOverviewResponse getSalesTargetsOverview(Integer year, Integer month, Long adminId) {
        int targetYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        int activeMonth = (month != null && month >= 1 && month <= 12) ? month : LocalDate.now().getMonthValue();

        SalesTargetsOverviewResponse response = new SalesTargetsOverviewResponse();
        response.setYear(targetYear);
        response.setSelectedMonth(activeMonth);

        List<SalesTargetResponse> monthlyResponses = new ArrayList<>();
        YearSummary yearSummary = new YearSummary();

        double totalTargetRev = 0.0;
        double totalAchievedRev = 0.0;
        int totalTargetLeads = 0;
        long totalAchievedLeads = 0L;
        int totalTargetDeals = 0;
        long totalAchievedDeals = 0L;

        // Build 12 monthly targets and achievements
        for (int m = 1; m <= 12; m++) {
            SalesTargetResponse monthTarget = computeMonthlyTargetAndAchievement(targetYear, m, adminId);
            monthlyResponses.add(monthTarget);

            totalTargetRev += monthTarget.getTargetRevenue();
            totalAchievedRev += monthTarget.getAchievedRevenue();
            totalTargetLeads += monthTarget.getTargetLeads();
            totalAchievedLeads += monthTarget.getAchievedLeads();
            totalTargetDeals += monthTarget.getTargetDeals();
            totalAchievedDeals += monthTarget.getAchievedDeals();

            if (m == activeMonth) {
                response.setCurrentMonth(monthTarget);
            }
        }

        response.setMonthlyTargets(monthlyResponses);

        // Aggregate Year Summary
        yearSummary.setTotalTargetRevenue(round(totalTargetRev));
        yearSummary.setTotalAchievedRevenue(round(totalAchievedRev));
        yearSummary.setRemainingRevenue(round(Math.max(0.0, totalTargetRev - totalAchievedRev)));
        yearSummary.setOverallRevenueAchievementRate(calculateRate(totalAchievedRev, totalTargetRev));

        yearSummary.setTotalTargetLeads(totalTargetLeads);
        yearSummary.setTotalAchievedLeads(totalAchievedLeads);
        yearSummary.setRemainingLeads(Math.max(0L, totalTargetLeads - totalAchievedLeads));
        yearSummary.setOverallLeadsAchievementRate(calculateRate((double) totalAchievedLeads, (double) totalTargetLeads));

        yearSummary.setTotalTargetDeals(totalTargetDeals);
        yearSummary.setTotalAchievedDeals(totalAchievedDeals);
        yearSummary.setRemainingDeals(Math.max(0L, totalTargetDeals - totalAchievedDeals));
        yearSummary.setOverallDealsAchievementRate(calculateRate((double) totalAchievedDeals, (double) totalTargetDeals));

        response.setYearSummary(yearSummary);

        // Sales Rep Breakdown for the selected month (if querying company-wide)
        if (adminId == null) {
            List<Admin> salesAdmins = adminRepository.findByActiveTrue().stream()
                    .filter(a -> a.getRole() != null && (a.getRole().equalsIgnoreCase("SALES_ADMIN") || a.getRole().equalsIgnoreCase("ADMIN")))
                    .toList();

            List<SalesTargetResponse> repTargets = new ArrayList<>();
            for (Admin rep : salesAdmins) {
                SalesTargetResponse repResp = computeMonthlyTargetAndAchievement(targetYear, activeMonth, rep.getId());
                repResp.setAdminName(rep.getName());
                repTargets.add(repResp);
            }
            response.setSalesRepTargets(repTargets);
        }

        return response;
    }

    /**
     * Get single month target and achievements.
     */
    public SalesTargetResponse getTargetForMonth(int year, int month, Long adminId) {
        return computeMonthlyTargetAndAchievement(year, month, adminId);
    }

    /**
     * Create or update target for a month.
     */
    @Transactional
    public SalesTargetResponse createOrUpdateTarget(SalesTargetDTO dto, String adminEmail) {
        int year = dto.getYear();
        int month = dto.getMonth();
        Long adminId = dto.getAdminId();

        Optional<SalesTarget> existingOpt = (adminId != null)
                ? salesTargetRepository.findByYearAndMonthAndAdminId(year, month, adminId)
                : salesTargetRepository.findByYearAndMonthAndAdminIdIsNull(year, month);

        SalesTarget target = existingOpt.orElseGet(SalesTarget::new);
        target.setYear(year);
        target.setMonth(month);
        target.setPeriod(String.format("%04d-%02d", year, month));
        target.setMonthName(Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        target.setTargetRevenue(dto.getTargetRevenue() != null ? dto.getTargetRevenue() : 0.0);
        target.setTargetLeads(dto.getTargetLeads() != null ? dto.getTargetLeads() : 0);
        target.setTargetDeals(dto.getTargetDeals() != null ? dto.getTargetDeals() : 0);
        target.setTargetUnits(dto.getTargetUnits() != null ? dto.getTargetUnits() : 0);
        target.setAdminId(adminId);
        target.setNotes(dto.getNotes());
        target.setCreatedByAdminEmail(adminEmail);

        if (adminId != null) {
            adminRepository.findById(adminId).ifPresent(a -> target.setAdminName(a.getName()));
        } else {
            target.setAdminName("Company Overall");
        }

        SalesTarget saved = salesTargetRepository.save(target);
        log.info("Sales target saved for period {} (adminId: {}) by {}",
                saved.getPeriod(), adminId, adminEmail);

        return computeMonthlyTargetAndAchievement(year, month, adminId);
    }

    /**
     * Compute both defined/default targets and actual achievements for a given month.
     */
    private SalesTargetResponse computeMonthlyTargetAndAchievement(int year, int month, Long adminId) {
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime end = start.plusMonths(1);

        // Fetch configured target from database if exists
        Optional<SalesTarget> targetOpt = (adminId != null)
                ? salesTargetRepository.findByYearAndMonthAndAdminId(year, month, adminId)
                : salesTargetRepository.findByYearAndMonthAndAdminIdIsNull(year, month);

        SalesTargetResponse resp = new SalesTargetResponse();
        resp.setYear(year);
        resp.setMonth(month);
        resp.setPeriod(String.format("%04d-%02d", year, month));
        resp.setMonthName(Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        resp.setAdminId(adminId);

        if (targetOpt.isPresent()) {
            SalesTarget t = targetOpt.get();
            resp.setId(t.getId());
            resp.setTargetRevenue(t.getTargetRevenue() != null ? t.getTargetRevenue() : 0.0);
            resp.setTargetLeads(t.getTargetLeads() != null ? t.getTargetLeads() : 0);
            resp.setTargetDeals(t.getTargetDeals() != null ? t.getTargetDeals() : 0);
            resp.setTargetUnits(t.getTargetUnits() != null ? t.getTargetUnits() : 0);
            resp.setNotes(t.getNotes());
            resp.setAdminName(t.getAdminName());
        } else {
            // Apply sensible baseline default if not yet explicitly saved
            resp.setTargetRevenue(DEFAULT_MONTHLY_REVENUE_TARGET);
            resp.setTargetLeads(DEFAULT_MONTHLY_LEADS_TARGET);
            resp.setTargetDeals(DEFAULT_MONTHLY_DEALS_TARGET);
            resp.setTargetUnits(DEFAULT_MONTHLY_UNITS_TARGET);
            if (adminId != null) {
                adminRepository.findById(adminId).ifPresent(a -> resp.setAdminName(a.getName()));
            } else {
                resp.setAdminName("Company Overall");
            }
        }

        // 1. Calculate Achieved Leads
        long achievedLeads = (adminId == null)
                ? leadRepository.countByCreatedAtBetween(start, end)
                : leadRepository.countByOwnerAdminIdAndCreatedAtBetween(adminId, start, end);
        resp.setAchievedLeads(achievedLeads);
        resp.setLeadsAchievementRate(calculateRate((double) achievedLeads, (double) resp.getTargetLeads()));
        resp.setRemainingLeads(Math.max(0L, resp.getTargetLeads() - achievedLeads));

        // 2. Calculate Achieved Deals (won opportunities)
        long achievedDeals = (adminId == null)
                ? opportunityRepository.findWonOpportunitiesInPeriod("won", start, end).size()
                : opportunityRepository.findWonOpportunitiesInPeriodForAdmin(adminId, "won", start, end).size();
        resp.setAchievedDeals(achievedDeals);
        resp.setDealsAchievementRate(calculateRate((double) achievedDeals, (double) resp.getTargetDeals()));
        resp.setRemainingDeals(Math.max(0L, resp.getTargetDeals() - achievedDeals));

        // 3. Calculate Achieved Revenue
        Double oppRevenue = (adminId == null)
                ? opportunityRepository.sumWonValueInPeriod("won", start, end)
                : opportunityRepository.sumWonValueInPeriodForAdmin(adminId, "won", start, end);
        double achievedRev = oppRevenue != null ? oppRevenue : 0.0;

        // If company-wide and opportunity revenue is 0, check orders for actual closed invoice amounts
        if (adminId == null && achievedRev == 0.0) {
            Double orderRev = orderRepository.sumTotalInvoiceAmountInPeriod(start, end);
            if (orderRev != null && orderRev > 0) {
                achievedRev = orderRev;
            }
        }

        resp.setAchievedRevenue(round(achievedRev));
        resp.setRevenueAchievementRate(calculateRate(achievedRev, resp.getTargetRevenue()));
        resp.setRemainingRevenue(round(Math.max(0.0, resp.getTargetRevenue() - achievedRev)));

        // 4. Units achieved (from orders in that month)
        if (adminId == null) {
            var orders = orderRepository.findByCreatedAtBetween(start, end);
            int units = orders.stream().mapToInt(o -> o.getTotalQuantity()).sum();
            resp.setAchievedUnits(units);
        }

        // 5. Active pipeline value and pipeline coverage
        Double pipelineVal = opportunityRepository.sumActivePipelineValueInPeriod(start, end);
        resp.setPipelineValue(pipelineVal != null ? round(pipelineVal) : 0.0);
        resp.setPipelineCoverage(resp.getTargetRevenue() > 0 ? round((resp.getPipelineValue() / resp.getTargetRevenue())) : 0.0);

        // 6. Target Achievement Status
        resp.setStatus(determineStatus(resp.getRevenueAchievementRate(), achievedDeals, resp.getTargetDeals()));

        return resp;
    }

    private String determineStatus(double revenueRate, long achievedDeals, int targetDeals) {
        if (revenueRate >= 100.0 || (targetDeals > 0 && achievedDeals >= targetDeals)) {
            return "ACHIEVED";
        } else if (revenueRate >= 75.0) {
            return "ON_TRACK";
        } else if (revenueRate >= 40.0) {
            return "IN_PROGRESS";
        } else {
            return "BEHIND";
        }
    }

    private double calculateRate(double achieved, double target) {
        if (target <= 0.0) {
            return 0.0;
        }
        return round((achieved / target) * 100.0);
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
