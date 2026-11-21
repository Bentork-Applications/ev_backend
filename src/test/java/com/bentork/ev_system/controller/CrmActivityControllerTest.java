package com.bentork.ev_system.controller;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.CompleteActivityDTO;
import com.bentork.ev_system.dto.request.RescheduleActivityDTO;
import com.bentork.ev_system.dto.response.ActivityOverviewStatsDTO;
import com.bentork.ev_system.dto.response.ActivityResponse;
import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.service.ActivityService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmActivityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ActivityService activityService;

    @Mock
    private AdminRepository adminRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CrmActivityController controller = new CrmActivityController(activityService, adminRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/crm/activities/all should return paged activities")
    void testGetAllActivities() throws Exception {
        PagedResponse<ActivityResponse> page = PagedResponse.of(Collections.emptyList(), 0, 20, 0, 0, true);
        when(activityService.getAllActivitiesPaged(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(page);

        mockMvc.perform(get("/api/crm/activities/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/crm/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/crm/activities/follow-ups should return follow-ups")
    void testGetFollowUps() throws Exception {
        PagedResponse<ActivityResponse> page = PagedResponse.of(Collections.emptyList(), 0, 20, 0, 0, true);
        when(activityService.getFollowUpsPaged(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/crm/activities/follow-ups").param("bucket", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/crm/activities/followups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/crm/activities/summary should return overview KPIs")
    void testGetSummary() throws Exception {
        ActivityOverviewStatsDTO stats = new ActivityOverviewStatsDTO();
        stats.setCallsToday(5L);
        stats.setOverdueFollowUps(2L);

        when(activityService.getActivityOverviewStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/crm/activities/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callsToday").value(5L))
                .andExpect(jsonPath("$.overdueFollowUps").value(2L));
    }

    @Test
    @DisplayName("PUT /api/crm/activities/{id}/reschedule should reschedule follow-up")
    void testRescheduleActivity() throws Exception {
        RescheduleActivityDTO dto = RescheduleActivityDTO.builder()
                .newFollowUpDate("2026-11-25")
                .newFollowUpTime("14:30")
                .reason("Client requested later time")
                .build();

        ActivityResponse resp = new ActivityResponse();
        resp.setId(1L);
        resp.setStatus("pending");

        when(activityService.rescheduleActivity(eq(1L), any(RescheduleActivityDTO.class), anyString()))
                .thenReturn(resp);

        mockMvc.perform(put("/api/crm/activities/1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));

        // Test POST method works too
        mockMvc.perform(post("/api/crm/activities/1/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("PUT /api/crm/activities/{id}/complete should mark activity complete")
    void testCompleteActivity() throws Exception {
        CompleteActivityDTO dto = CompleteActivityDTO.builder()
                .notes("Meeting completed successfully")
                .outcome("interested")
                .build();

        ActivityResponse resp = new ActivityResponse();
        resp.setId(2L);
        resp.setStatus("completed");

        when(activityService.completeActivityWithDetails(eq(2L), any(CompleteActivityDTO.class), anyString()))
                .thenReturn(resp);

        mockMvc.perform(put("/api/crm/activities/2/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.status").value("completed"));
    }
}
