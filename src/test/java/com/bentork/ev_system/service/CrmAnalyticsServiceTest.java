package com.bentork.ev_system.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bentork.ev_system.dto.response.LeadRevenueTrendPoint;
import com.bentork.ev_system.dto.response.LeadRevenueTrendResponse;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.Opportunity;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.OpportunityRepository;
import com.bentork.ev_system.repository.OrderRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrmAnalyticsServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private OpportunityRepository opportunityRepository;

    @Mock
    private OrderRepository orderRepository;

    private CrmAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new CrmAnalyticsService(leadRepository, opportunityRepository, orderRepository);
    }

    @Test
    void testGetLeadRevenueTrendMonthlyCalendarYear() {
        when(leadRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.findWonOpportunitiesInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);
        when(orderRepository.sumTotalInvoiceAmountInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);

        LeadRevenueTrendResponse response = analyticsService.getLeadRevenueTrend("monthly", 2026, null, null);

        assertNotNull(response);
        assertEquals("monthly", response.getPeriod());
        assertEquals(2026, response.getYear());
        assertEquals(12, response.getTrendPoints().size());

        LeadRevenueTrendPoint oct = response.getTrendPoints().get(9); // Month 10
        assertEquals("2026-10", oct.getPeriod());
        assertEquals("Oct 2026", oct.getPeriodLabel());
        assertEquals("October", oct.getMonthName());
        assertEquals(0L, oct.getTotalLeads());
        assertEquals(0.0, oct.getTotalRevenue());
    }

    @Test
    void testLeadStatusAggregationAndRevenueComputation() {
        // Setup mock leads for month
        List<Lead> mockLeads = new ArrayList<>();
        Lead l1 = new Lead(); l1.setStatus("new"); mockLeads.add(l1);
        Lead l2 = new Lead(); l2.setStatus("contacted"); mockLeads.add(l2);
        Lead l3 = new Lead(); l3.setStatus("qualified"); mockLeads.add(l3);
        Lead l4 = new Lead(); l4.setStatus("converted"); mockLeads.add(l4);
        Lead l5 = new Lead(); l5.setStatus("lost"); mockLeads.add(l5);

        // Setup mock won opportunities
        Opportunity opp1 = new Opportunity(); opp1.setValue(500000.0);
        Opportunity opp2 = new Opportunity(); opp2.setValue(300000.0);
        List<Opportunity> wonOpps = List.of(opp1, opp2);

        when(leadRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(mockLeads);
        when(opportunityRepository.findWonOpportunitiesInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(wonOpps);
        when(opportunityRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(opp1, opp2));
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1200000.0);

        LeadRevenueTrendResponse response = analyticsService.getLeadRevenueTrend("monthly", 2026, 12, null);

        assertNotNull(response);
        LeadRevenueTrendPoint point = response.getTrendPoints().get(0);

        assertEquals(5L, point.getTotalLeads());
        assertEquals(1L, point.getNewLeads());
        assertEquals(1L, point.getContactedLeads());
        assertEquals(1L, point.getQualifiedLeads());
        assertEquals(1L, point.getConvertedLeads());
        assertEquals(1L, point.getLostLeads());
        assertEquals(20.0, point.getLeadConversionRate()); // 1 out of 5 = 20%

        assertEquals(2L, point.getWonDeals());
        assertEquals(800000.0, point.getTotalRevenue());
        assertEquals(400000.0, point.getAverageDealSize());
        assertEquals(1200000.0, point.getPipelineValue());
        assertEquals(600000.0, point.getExpectedRevenue());

        // Check overall summary
        assertNotNull(response.getSummary());
        assertEquals(60L, response.getSummary().getTotalLeads()); // 5 * 12
        assertEquals(9600000.0, response.getSummary().getTotalRevenue()); // 800,000 * 12
        assertEquals(24L, response.getSummary().getTotalWonDeals());
        assertEquals(400000.0, response.getSummary().getAverageDealSize());
    }

    @Test
    void testQuarterlyTrends() {
        when(leadRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.findWonOpportunitiesInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);
        when(orderRepository.sumTotalInvoiceAmountInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);

        LeadRevenueTrendResponse response = analyticsService.getLeadRevenueTrend("quarterly", 2026, null, null);

        assertNotNull(response);
        assertEquals("quarterly", response.getPeriod());
        assertEquals(4, response.getTrendPoints().size());
        assertEquals("2026-Q1", response.getTrendPoints().get(0).getPeriod());
        assertEquals("Q1 2026", response.getTrendPoints().get(0).getPeriodLabel());
        assertEquals("2026-Q4", response.getTrendPoints().get(3).getPeriod());
    }

    @Test
    void testSummaryGrowthRateCalculation() {
        LocalDateTime start1 = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        LocalDateTime end1 = start1.plusMonths(1);
        LocalDateTime start2 = LocalDateTime.of(2026, 2, 1, 0, 0, 0);
        LocalDateTime end2 = start2.plusMonths(1);

        Lead l1 = new Lead(); l1.setStatus("new");
        Lead l2 = new Lead(); l2.setStatus("new");

        Opportunity o1 = new Opportunity(); o1.setValue(100000.0);
        Opportunity o2 = new Opportunity(); o2.setValue(200000.0);

        // Month 11 has 1 lead, Month 12 has 2 leads (100% growth)
        when(leadRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(l1));
        when(opportunityRepository.findWonOpportunitiesInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(o1));
        when(opportunityRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);

        LeadRevenueTrendResponse response = analyticsService.getLeadRevenueTrend("monthly", 2026, 12, null);

        assertNotNull(response.getSummary());
        assertEquals(0.0, response.getSummary().getLeadGrowthRate()); // Equal values -> 0% growth
        assertEquals(0.0, response.getSummary().getRevenueGrowthRate());
    }
}
