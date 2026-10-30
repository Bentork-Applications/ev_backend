package com.bentork.ev_system.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.response.LeadRevenueTrendResponse;
import com.bentork.ev_system.service.CrmAnalyticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller providing historical trend and analytics endpoints for the Sales CRM.
 * Mapped to both /crm/analytics and /api/crm/analytics.
 */
@RestController
@RequestMapping({"/api/crm/analytics", "/crm/analytics"})
@RequiredArgsConstructor
@Slf4j
public class CrmAnalyticsController {

    private final CrmAnalyticsService crmAnalyticsService;

    /**
     * GET /crm/analytics/lead-revenue-trend?period=monthly
     * or /api/crm/analytics/lead-revenue-trend?period=monthly
     *
     * Provides historical trend data for leads and revenue over time.
     *
     * @param period Period type: "monthly" (default), "quarterly", "yearly"
     * @param year   Target calendar year (optional)
     * @param months Number of trailing months (optional, default 12)
     * @param adminId Optional filter by sales executive
     * @return Trend data points with summary metrics
     */
    @GetMapping("/lead-revenue-trend")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<LeadRevenueTrendResponse> getLeadRevenueTrend(
            @RequestParam(defaultValue = "monthly") String period,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) Long adminId) {
        log.info("Fetching lead-revenue trend analytics: period={}, year={}, months={}, adminId={}",
                period, year, months, adminId);
        LeadRevenueTrendResponse response = crmAnalyticsService.getLeadRevenueTrend(period, year, months, adminId);
        return ResponseEntity.ok(response);
    }
}
