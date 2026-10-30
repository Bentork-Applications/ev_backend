package com.bentork.ev_system.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bentork.ev_system.dto.request.SalesTargetDTO;
import com.bentork.ev_system.dto.response.SalesTargetResponse;
import com.bentork.ev_system.dto.response.SalesTargetsOverviewResponse;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Opportunity;
import com.bentork.ev_system.model.SalesTarget;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.OpportunityRepository;
import com.bentork.ev_system.repository.OrderRepository;
import com.bentork.ev_system.repository.SalesTargetRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrmTargetServiceTest {

    @Mock
    private SalesTargetRepository salesTargetRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private OpportunityRepository opportunityRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AdminRepository adminRepository;

    private CrmTargetService targetService;

    @BeforeEach
    void setUp() {
        targetService = new CrmTargetService(
                salesTargetRepository,
                leadRepository,
                opportunityRepository,
                orderRepository,
                adminRepository
        );
    }

    @Test
    void testGetSalesTargetsOverviewWithDefaults() {
        when(salesTargetRepository.findByYearAndMonthAndAdminIdIsNull(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        when(leadRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(15L);
        when(opportunityRepository.findWonOpportunitiesInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.sumWonValueInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(250000.0);
        when(orderRepository.sumTotalInvoiceAmountInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);
        when(orderRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(adminRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        SalesTargetsOverviewResponse overview = targetService.getSalesTargetsOverview(2026, 10, null);

        assertNotNull(overview);
        assertEquals(2026, overview.getYear());
        assertEquals(10, overview.getSelectedMonth());
        assertEquals(12, overview.getMonthlyTargets().size());
        assertNotNull(overview.getCurrentMonth());
        assertEquals("October", overview.getCurrentMonth().getMonthName());
        assertEquals(15L, overview.getCurrentMonth().getAchievedLeads());
        assertEquals(5000000.0, overview.getCurrentMonth().getTargetRevenue());
        assertNotNull(overview.getYearSummary());
    }

    @Test
    void testTargetAchievementsCalculation() {
        SalesTarget customTarget = new SalesTarget();
        customTarget.setId(1L);
        customTarget.setYear(2026);
        customTarget.setMonth(10);
        customTarget.setPeriod("2026-10");
        customTarget.setTargetRevenue(1000000.0);
        customTarget.setTargetLeads(50);
        customTarget.setTargetDeals(5);

        when(salesTargetRepository.findByYearAndMonthAndAdminIdIsNull(2026, 10))
                .thenReturn(Optional.of(customTarget));
        when(leadRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(45L);

        Opportunity wonOpp1 = new Opportunity();
        wonOpp1.setValue(600000.0);
        Opportunity wonOpp2 = new Opportunity();
        wonOpp2.setValue(400000.0);
        List<Opportunity> wonList = List.of(wonOpp1, wonOpp2);

        when(opportunityRepository.findWonOpportunitiesInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(wonList);
        when(opportunityRepository.sumWonValueInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1000000.0);
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(500000.0);
        when(orderRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        SalesTargetResponse result = targetService.getTargetForMonth(2026, 10, null);

        assertNotNull(result);
        assertEquals(1000000.0, result.getTargetRevenue());
        assertEquals(1000000.0, result.getAchievedRevenue());
        assertEquals(100.0, result.getRevenueAchievementRate());
        assertEquals(0.0, result.getRemainingRevenue());
        assertEquals(45L, result.getAchievedLeads());
        assertEquals(90.0, result.getLeadsAchievementRate());
        assertEquals(5L, result.getRemainingLeads());
        assertEquals(2L, result.getAchievedDeals());
        assertEquals("ACHIEVED", result.getStatus());
    }

    @Test
    void testCreateOrUpdateTarget() {
        SalesTargetDTO dto = new SalesTargetDTO();
        dto.setYear(2026);
        dto.setMonth(11);
        dto.setTargetRevenue(2500000.0);
        dto.setTargetLeads(60);
        dto.setTargetDeals(8);
        dto.setNotes("Special Festive Target");

        when(salesTargetRepository.findByYearAndMonthAndAdminIdIsNull(2026, 11))
                .thenReturn(Optional.empty());

        SalesTarget savedTarget = new SalesTarget();
        savedTarget.setId(10L);
        savedTarget.setYear(2026);
        savedTarget.setMonth(11);
        savedTarget.setPeriod("2026-11");
        savedTarget.setTargetRevenue(2500000.0);
        savedTarget.setTargetLeads(60);
        savedTarget.setTargetDeals(8);
        savedTarget.setNotes("Special Festive Target");

        when(salesTargetRepository.save(any(SalesTarget.class))).thenReturn(savedTarget);
        when(salesTargetRepository.findByYearAndMonthAndAdminIdIsNull(2026, 11))
                .thenReturn(Optional.of(savedTarget));
        when(leadRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(10L);
        when(opportunityRepository.findWonOpportunitiesInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(opportunityRepository.sumWonValueInPeriod(eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);
        when(orderRepository.sumTotalInvoiceAmountInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0.0);
        when(orderRepository.findByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        SalesTargetResponse response = targetService.createOrUpdateTarget(dto, "admin@bentork.com");

        assertNotNull(response);
        assertEquals(2500000.0, response.getTargetRevenue());
        assertEquals(60, response.getTargetLeads());
        assertEquals(8, response.getTargetDeals());
        assertEquals("Special Festive Target", response.getNotes());
        verify(salesTargetRepository, times(1)).save(any(SalesTarget.class));
    }

    @Test
    void testSalesRepSpecificTarget() {
        SalesTarget repTarget = new SalesTarget();
        repTarget.setId(2L);
        repTarget.setYear(2026);
        repTarget.setMonth(10);
        repTarget.setAdminId(5L);
        repTarget.setAdminName("John Sales");
        repTarget.setTargetRevenue(500000.0);
        repTarget.setTargetLeads(25);
        repTarget.setTargetDeals(3);

        when(salesTargetRepository.findByYearAndMonthAndAdminId(2026, 10, 5L))
                .thenReturn(Optional.of(repTarget));
        when(leadRepository.countByOwnerAdminIdAndCreatedAtBetween(eq(5L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(20L);
        when(opportunityRepository.findWonOpportunitiesInPeriodForAdmin(eq(5L), eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(new Opportunity()));
        when(opportunityRepository.sumWonValueInPeriodForAdmin(eq(5L), eq("won"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(400000.0);
        when(opportunityRepository.sumActivePipelineValueInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(150000.0);

        SalesTargetResponse repResult = targetService.getTargetForMonth(2026, 10, 5L);

        assertNotNull(repResult);
        assertEquals(5L, repResult.getAdminId());
        assertEquals("John Sales", repResult.getAdminName());
        assertEquals(500000.0, repResult.getTargetRevenue());
        assertEquals(400000.0, repResult.getAchievedRevenue());
        assertEquals(80.0, repResult.getRevenueAchievementRate());
        assertEquals("ON_TRACK", repResult.getStatus());
    }
}
