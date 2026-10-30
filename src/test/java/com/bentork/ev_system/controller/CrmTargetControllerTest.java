package com.bentork.ev_system.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.SalesTargetDTO;
import com.bentork.ev_system.dto.response.SalesTargetResponse;
import com.bentork.ev_system.dto.response.SalesTargetsOverviewResponse;
import com.bentork.ev_system.service.CrmTargetService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmTargetControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CrmTargetService crmTargetService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CrmTargetController controller = new CrmTargetController(crmTargetService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testGetTargetsOverviewBothEndpoints() throws Exception {
        SalesTargetsOverviewResponse response = new SalesTargetsOverviewResponse();
        response.setYear(2026);
        response.setSelectedMonth(10);

        SalesTargetResponse monthResp = new SalesTargetResponse();
        monthResp.setMonth(10);
        monthResp.setMonthName("October");
        monthResp.setTargetRevenue(5000000.0);
        monthResp.setAchievedRevenue(3500000.0);
        monthResp.setRevenueAchievementRate(70.0);
        monthResp.setStatus("ON_TRACK");
        response.setCurrentMonth(monthResp);

        when(crmTargetService.getSalesTargetsOverview(2026, 10, null)).thenReturn(response);

        // Test GET /crm/targets
        mockMvc.perform(get("/crm/targets?year=2026&month=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.selectedMonth").value(10))
                .andExpect(jsonPath("$.currentMonth.monthName").value("October"))
                .andExpect(jsonPath("$.currentMonth.targetRevenue").value(5000000.0))
                .andExpect(jsonPath("$.currentMonth.achievedRevenue").value(3500000.0))
                .andExpect(jsonPath("$.currentMonth.status").value("ON_TRACK"));

        // Test GET /api/crm/targets
        mockMvc.perform(get("/api/crm/targets?year=2026&month=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.currentMonth.status").value("ON_TRACK"));
    }

    @Test
    void testGetCurrentMonthTarget() throws Exception {
        SalesTargetResponse target = new SalesTargetResponse();
        target.setPeriod("2026-10");
        target.setMonthName("October");
        target.setTargetRevenue(4000000.0);
        target.setAchievedRevenue(4200000.0);
        target.setStatus("ACHIEVED");

        when(crmTargetService.getTargetForMonth(anyInt(), anyInt(), isNull())).thenReturn(target);

        mockMvc.perform(get("/crm/targets/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthName").value("October"))
                .andExpect(jsonPath("$.status").value("ACHIEVED"));

        mockMvc.perform(get("/api/crm/targets/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthName").value("October"))
                .andExpect(jsonPath("$.status").value("ACHIEVED"));
    }

    @Test
    void testPostTarget() throws Exception {
        SalesTargetDTO dto = new SalesTargetDTO();
        dto.setYear(2026);
        dto.setMonth(11);
        dto.setTargetRevenue(6000000.0);
        dto.setTargetLeads(80);
        dto.setTargetDeals(15);

        SalesTargetResponse response = new SalesTargetResponse();
        response.setYear(2026);
        response.setMonth(11);
        response.setTargetRevenue(6000000.0);
        response.setTargetLeads(80);
        response.setTargetDeals(15);
        response.setStatus("IN_PROGRESS");

        when(crmTargetService.createOrUpdateTarget(any(SalesTargetDTO.class), any())).thenReturn(response);

        mockMvc.perform(post("/crm/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.month").value(11))
                .andExpect(jsonPath("$.targetRevenue").value(6000000.0));
    }
}
