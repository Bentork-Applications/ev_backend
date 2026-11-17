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
}
