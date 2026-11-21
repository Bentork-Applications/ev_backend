package com.bentork.ev_system.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.UpdateCampaignDTO;
import com.bentork.ev_system.dto.response.CampaignResponse;
import com.bentork.ev_system.service.CampaignService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmCampaignControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CampaignService campaignService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        CrmCampaignController controller = new CrmCampaignController(campaignService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("PUT /api/crm/campaigns/{id} should update campaign successfully")
    void testUpdateCampaign_Success() throws Exception {
        UpdateCampaignDTO dto = UpdateCampaignDTO.builder()
                .name("Updated Monsoon Offer")
                .type("OFFER")
                .channel("WHATSAPP")
                .subject("Special Discount")
                .messageBody("Hello {{contactName}}, check our latest discounts!")
                .description("Updated discount campaign")
                .objective("Drive sales")
                .scheduledDate(LocalDate.of(2026, 12, 1))
                .status("DRAFT")
                .build();

        CampaignResponse response = CampaignResponse.builder()
                .id(1L)
                .name("Updated Monsoon Offer")
                .type("OFFER")
                .channel("WHATSAPP")
                .subject("Special Discount")
                .messageBody("Hello {{contactName}}, check our latest discounts!")
                .description("Updated discount campaign")
                .objective("Drive sales")
                .scheduledDate(LocalDate.of(2026, 12, 1))
                .status("DRAFT")
                .build();

        when(campaignService.updateCampaign(eq(1L), any(UpdateCampaignDTO.class), anyString())).thenReturn(response);

        mockMvc.perform(put("/api/crm/campaigns/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Updated Monsoon Offer"))
                .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("PUT /api/crm/campaigns/{id} should return 404 when campaign not found")
    void testUpdateCampaign_NotFound() throws Exception {
        UpdateCampaignDTO dto = UpdateCampaignDTO.builder()
                .name("Unknown Campaign")
                .build();

        when(campaignService.updateCampaign(eq(999L), any(UpdateCampaignDTO.class), anyString()))
                .thenThrow(new IllegalArgumentException("Campaign not found with id: 999"));

        mockMvc.perform(put("/api/crm/campaigns/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/crm/campaigns/{id} should return 400 when campaign cannot be modified")
    void testUpdateCampaign_Completed() throws Exception {
        UpdateCampaignDTO dto = UpdateCampaignDTO.builder()
                .name("Completed Campaign Edit")
                .build();

        when(campaignService.updateCampaign(eq(2L), any(UpdateCampaignDTO.class), anyString()))
                .thenThrow(new IllegalStateException("Cannot modify a COMPLETED campaign"));

        mockMvc.perform(put("/api/crm/campaigns/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/crm/campaigns/{id} should update campaign with 5 sequential follow-up rounds")
    void testUpdateCampaign_WithFiveRounds() throws Exception {
        java.util.List<com.bentork.ev_system.dto.request.CampaignRoundDTO> rounds = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            rounds.add(com.bentork.ev_system.dto.request.CampaignRoundDTO.builder()
                    .roundNumber(i)
                    .channel("WHATSAPP")
                    .subject("Round " + i + " Follow-up")
                    .messageBody("Round " + i + " body content")
                    .sendMode("MANUAL")
                    .status("CONFIGURED")
                    .build());
        }

        UpdateCampaignDTO dto = UpdateCampaignDTO.builder()
                .name("5-Round Lead Outreach")
                .type("FOLLOW_UP")
                .channel("WHATSAPP")
                .rounds(rounds)
                .build();

        CampaignResponse response = CampaignResponse.builder()
                .id(10L)
                .name("5-Round Lead Outreach")
                .type("FOLLOW_UP")
                .channel("WHATSAPP")
                .totalRounds(5)
                .rounds(rounds)
                .build();

        when(campaignService.updateCampaign(eq(10L), any(UpdateCampaignDTO.class), anyString())).thenReturn(response);

        mockMvc.perform(put("/api/crm/campaigns/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.name").value("5-Round Lead Outreach"))
                .andExpect(jsonPath("$.totalRounds").value(5))
                .andExpect(jsonPath("$.rounds.length()").value(5));
    }

    @Test
    @DisplayName("GET /api/crm/campaigns/all should return all campaigns successfully")
    void testGetAllCampaigns_AllEndpoint() throws Exception {
        CampaignResponse c1 = CampaignResponse.builder().id(1L).name("C1").build();
        CampaignResponse c2 = CampaignResponse.builder().id(2L).name("C2").build();
        when(campaignService.getAllCampaigns()).thenReturn(java.util.List.of(c1, c2));

        mockMvc.perform(get("/api/crm/campaigns/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
