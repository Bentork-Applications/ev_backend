package com.bentork.ev_system.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.request.UpdateOpportunityStageDTO;
import com.bentork.ev_system.dto.response.OpportunityResponse;
import com.bentork.ev_system.enums.OpportunityStage;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Opportunity;
import com.bentork.ev_system.model.SalesCompany;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.OpportunityRepository;
import com.bentork.ev_system.repository.SalesCompanyRepository;
import com.bentork.ev_system.repository.SalesContactRepository;
import com.bentork.ev_system.service.interfaces.IAdminNotificationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpportunityServiceTest {

    @Mock
    private OpportunityRepository opportunityRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private SalesCompanyRepository companyRepository;
    @Mock
    private SalesContactRepository contactRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private IAdminNotificationService adminNotificationService;

    private OpportunityService opportunityService;

    private Admin salesAdmin;
    private Admin superAdmin;
    private SalesCompany company;

    @BeforeEach
    void setUp() {
        opportunityService = new OpportunityService(
                opportunityRepository,
                leadRepository,
                companyRepository,
                contactRepository,
                adminRepository,
                adminNotificationService);

        salesAdmin = new Admin();
        salesAdmin.setId(10L);
        salesAdmin.setName("Rahul Sales");
        salesAdmin.setEmail("rahul@bentork.com");
        salesAdmin.setRole("SALES_ADMIN");

        superAdmin = new Admin();
        superAdmin.setId(1L);
        superAdmin.setName("Super Admin");
        superAdmin.setEmail("admin@bentork.com");
        superAdmin.setRole("ADMIN");

        company = new SalesCompany();
        company.setId(5L);
        company.setName("Acme EV Fleet");
    }

    @Test
    @DisplayName("Opportunity Lost Request -> Admin Approve -> Admin Revoke restores previousStage and probability")
    void testOpportunityLostApproveAndRevokeWorkflow() {
        Opportunity opp = new Opportunity();
        opp.setId(300L);
        opp.setOpportunityNumber("OPP-20261128-0300");
        opp.setTitle("100x 48V Pack Deal");
        opp.setStage(OpportunityStage.PROPOSAL.getValue());
        opp.setProbability(OpportunityStage.PROPOSAL.getProbability());
        opp.setCompany(company);
        opp.setOwnerAdmin(salesAdmin);

        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));
        when(adminRepository.findByEmail("admin@bentork.com")).thenReturn(Optional.of(superAdmin));
        when(opportunityRepository.findById(300L)).thenReturn(Optional.of(opp));
        when(opportunityRepository.save(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Sales Rep submits Lost Request
        LostRequestDTO lostReq = LostRequestDTO.builder().lostReason("Pricing mismatch").build();
        OpportunityResponse requested = opportunityService.requestLost(300L, lostReq, "rahul@bentork.com");

        assertEquals("proposal", requested.getStage());
        assertEquals("proposal", requested.getPreviousStage());
        assertEquals("Pricing mismatch", requested.getLostReason());
        assertEquals("PENDING", requested.getLostApprovalStatus());
        assertEquals(10L, requested.getLostRequestedByAdminId());
        verify(adminNotificationService).notifyAdminsOfLostRequest(
                eq("Opportunity"), eq(300L), eq("OPP-20261128-0300"), eq("100x 48V Pack Deal"),
                eq("proposal"), eq("Pricing mismatch"), eq(salesAdmin));

        // Direct stage change blocked while pending
        UpdateOpportunityStageDTO stageDto = new UpdateOpportunityStageDTO();
        stageDto.setStage("negotiation");
        assertThrows(IllegalStateException.class,
                () -> opportunityService.updateStage(300L, stageDto, "rahul@bentork.com"));

        // Non-ADMIN cannot approve
        assertThrows(AccessDeniedException.class,
                () -> opportunityService.approveLost(300L, null, "rahul@bentork.com"));

        // 2. Super Admin approves Lost
        OpportunityResponse approved = opportunityService.approveLost(300L, null, "admin@bentork.com");
        assertEquals("lost", approved.getStage());
        assertEquals(0, approved.getProbability());
        assertEquals("APPROVED", approved.getLostApprovalStatus());
        assertEquals("proposal", approved.getPreviousStage());
        assertEquals(1L, approved.getLostApprovedByAdminId());

        // Direct transition from LOST to QUALIFIED still blocked
        UpdateOpportunityStageDTO backToQual = new UpdateOpportunityStageDTO();
        backToQual.setStage("qualified");
        assertThrows(IllegalArgumentException.class,
                () -> opportunityService.updateStage(300L, backToQual, "admin@bentork.com"));

        // 3. Super Admin revokes Lost -> restores previousStage ('proposal') & probability (60)
        AdminLostActionDTO revokeDto = AdminLostActionDTO.builder()
                .reason("Client approved revised discount")
                .build();
        OpportunityResponse revoked = opportunityService.revokeLost(300L, revokeDto, "admin@bentork.com");
        assertEquals("proposal", revoked.getStage());
        assertEquals(60, revoked.getProbability());
        assertEquals("REVOKED", revoked.getLostApprovalStatus());
        assertEquals(1L, revoked.getLostRevokedByAdminId());
        assertEquals("Client approved revised discount", revoked.getLostRevokeReason());
    }

    @Test
    @DisplayName("Admin revoking pending Lost Request cancels request and keeps Opportunity in pipeline")
    void testAdminRevokePendingLostRequest() {
        Opportunity opp = new Opportunity();
        opp.setId(301L);
        opp.setOpportunityNumber("OPP-20261128-0301");
        opp.setTitle("Depot Charger Deal");
        opp.setStage("negotiation");
        opp.setProbability(80);
        opp.setPreviousStage("negotiation");
        opp.setLostReason("Client delayed");
        opp.setLostApprovalStatus("PENDING");
        opp.setLostRequestedByAdmin(salesAdmin);
        opp.setOwnerAdmin(salesAdmin);

        when(adminRepository.findByEmail("admin@bentork.com")).thenReturn(Optional.of(superAdmin));
        when(opportunityRepository.findById(301L)).thenReturn(Optional.of(opp));
        when(opportunityRepository.save(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminLostActionDTO dto = AdminLostActionDTO.builder()
                .reason("Keep deal open for follow-up")
                .build();
        OpportunityResponse response = opportunityService.revokeLost(301L, dto, "admin@bentork.com");

        assertEquals("negotiation", response.getStage());
        assertEquals(80, response.getProbability());
        assertEquals("REVOKED", response.getLostApprovalStatus());
        assertEquals(1L, response.getLostRevokedByAdminId());
        verify(adminNotificationService).notifyAdminOfLostDecision(
                eq(salesAdmin), eq("REVOKED"), eq("Opportunity"),
                eq("OPP-20261128-0301"), eq("Depot Charger Deal"),
                eq("negotiation"), eq("Keep deal open for follow-up"), eq(superAdmin));
    }

    @Test
    @DisplayName("Admin revoking legacy LOST Opportunity without previousStage moves to qualified pipeline")
    void testRevokeLegacyLostOpportunity_WithOverrideOrFallback() {
        Opportunity opp = new Opportunity();
        opp.setId(302L);
        opp.setOpportunityNumber("OPP-OLD-302");
        opp.setTitle("Legacy Lost Deal");
        opp.setStage("lost");
        opp.setProbability(0);
        opp.setPreviousStage(null);

        when(adminRepository.findByEmail("admin@bentork.com")).thenReturn(Optional.of(superAdmin));
        when(opportunityRepository.findById(302L)).thenReturn(Optional.of(opp));
        when(opportunityRepository.save(any(Opportunity.class))).thenAnswer(inv -> inv.getArgument(0));

        OpportunityResponse revoked = opportunityService.revokeLost(302L, null, "admin@bentork.com");
        assertEquals("qualified", revoked.getStage());
        assertEquals(20, revoked.getProbability());
        assertEquals("REVOKED", revoked.getLostApprovalStatus());
    }

    @Test
    @DisplayName("Sales Admin cannot directly mark an Opportunity as LOST via updateStage")
    void testDirectLostBlockedForSalesAdmin() {
        Opportunity opp = new Opportunity();
        opp.setId(303L);
        opp.setStage("requirement");

        when(opportunityRepository.findById(303L)).thenReturn(Optional.of(opp));
        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));

        UpdateOpportunityStageDTO dto = new UpdateOpportunityStageDTO();
        dto.setStage("lost");
        dto.setLostReason("No budget");

        assertThrows(IllegalArgumentException.class,
                () -> opportunityService.updateStage(303L, dto, "rahul@bentork.com"));
        verify(opportunityRepository, never()).save(any());
    }
}
