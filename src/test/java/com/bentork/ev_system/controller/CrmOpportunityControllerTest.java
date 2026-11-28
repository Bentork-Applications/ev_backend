package com.bentork.ev_system.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.response.OpportunityResponse;
import com.bentork.ev_system.service.OpportunityService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmOpportunityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OpportunityService opportunityService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CrmOpportunityController controller = new CrmOpportunityController(opportunityService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Opportunity lost request, approve, revoke, and pending-lost endpoints")
    void testOpportunityLostWorkflowEndpoints() throws Exception {
        OpportunityResponse requested = new OpportunityResponse();
        requested.setId(9L);
        requested.setStage("proposal");
        requested.setPreviousStage("proposal");
        requested.setLostReason("Competitor pricing");
        requested.setLostApprovalStatus("PENDING");

        when(opportunityService.requestLost(eq(9L), any(LostRequestDTO.class), anyString())).thenReturn(requested);

        mockMvc.perform(post("/api/crm/opportunities/9/lost-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LostRequestDTO.builder().lostReason("Competitor pricing").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lostApprovalStatus").value("PENDING"))
                .andExpect(jsonPath("$.previousStage").value("proposal"));

        OpportunityResponse approved = new OpportunityResponse();
        approved.setId(9L);
        approved.setStage("lost");
        approved.setProbability(0);
        approved.setLostApprovalStatus("APPROVED");

        when(opportunityService.approveLost(eq(9L), any(), anyString())).thenReturn(approved);

        mockMvc.perform(put("/api/crm/opportunities/9/lost/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("lost"))
                .andExpect(jsonPath("$.lostApprovalStatus").value("APPROVED"));

        OpportunityResponse revoked = new OpportunityResponse();
        revoked.setId(9L);
        revoked.setStage("proposal");
        revoked.setProbability(60);
        revoked.setLostApprovalStatus("REVOKED");

        when(opportunityService.revokeLost(eq(9L), any(AdminLostActionDTO.class), anyString())).thenReturn(revoked);

        mockMvc.perform(post("/api/crm/opportunities/9/lost/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminLostActionDTO.builder().reason("Reopened deal").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("proposal"))
                .andExpect(jsonPath("$.probability").value(60))
                .andExpect(jsonPath("$.lostApprovalStatus").value("REVOKED"));

        when(opportunityService.getPendingLostRequests()).thenReturn(List.of(requested));
        mockMvc.perform(get("/api/crm/opportunities/lost-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9));
    }
}
