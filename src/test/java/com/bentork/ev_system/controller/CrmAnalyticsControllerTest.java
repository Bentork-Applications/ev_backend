package com.bentork.ev_system.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.response.LeadRevenueTrendPoint;
import com.bentork.ev_system.dto.response.LeadRevenueTrendResponse;
import com.bentork.ev_system.dto.response.LeadRevenueTrendResponse.TrendSummary;
import com.bentork.ev_system.service.CrmAnalyticsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmAnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CrmAnalyticsService crmAnalyticsService;

    @BeforeEach
    void setUp() {
        CrmAnalyticsController controller = new CrmAnalyticsController(crmAnalyticsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testGetLeadRevenueTrendEndpointBothPrefixes() throws Exception {
        LeadRevenueTrendResponse response = new LeadRevenueTrendResponse();
        response.setPeriod("monthly");
        response.setYear(2026);

        LeadRevenueTrendPoint point = new LeadRevenueTrendPoint();
        point.setPeriod("2026-10");
        point.setPeriodLabel("Oct 2026");
        point.setMonth(10);
        point.setMonthName("October");
        point.setTotalLeads(42L);
        point.setConvertedLeads(10L);
        point.setLeadConversionRate(23.81);
        point.setTotalRevenue(3200000.0);
        point.setWonDeals(8L);
        point.setAverageDealSize(400000.0);
        response.setTrendPoints(List.of(point));

        TrendSummary summary = new TrendSummary();
        summary.setTotalLeads(42L);
        summary.setTotalRevenue(3200000.0);
        summary.setOverallConversionRate(23.81);
        summary.setLeadGrowthRate(12.5);
        summary.setRevenueGrowthRate(15.0);
        response.setSummary(summary);

        when(crmAnalyticsService.getLeadRevenueTrend(eq("monthly"), eq(2026), any(), any()))
                .thenReturn(response);

        // Test GET /crm/analytics/lead-revenue-trend?period=monthly
        mockMvc.perform(get("/crm/analytics/lead-revenue-trend?period=monthly&year=2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("monthly"))
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.trendPoints[0].period").value("2026-10"))
                .andExpect(jsonPath("$.trendPoints[0].totalLeads").value(42))
                .andExpect(jsonPath("$.trendPoints[0].totalRevenue").value(3200000.0))
                .andExpect(jsonPath("$.trendPoints[0].leadConversionRate").value(23.81))
                .andExpect(jsonPath("$.summary.overallConversionRate").value(23.81))
                .andExpect(jsonPath("$.summary.leadGrowthRate").value(12.5))
                .andExpect(jsonPath("$.summary.revenueGrowthRate").value(15.0));

        // Test GET /api/crm/analytics/lead-revenue-trend?period=monthly
        mockMvc.perform(get("/api/crm/analytics/lead-revenue-trend?period=monthly&year=2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("monthly"))
                .andExpect(jsonPath("$.trendPoints[0].totalRevenue").value(3200000.0));
    }
}
