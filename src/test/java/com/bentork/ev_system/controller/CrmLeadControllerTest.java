package com.bentork.ev_system.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.BulkLeadAssignDTO;
import com.bentork.ev_system.dto.request.BulkLeadTagDTO;
import com.bentork.ev_system.dto.response.BulkOperationResponse;
import com.bentork.ev_system.dto.response.CsvImportResponse;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.service.LeadService;
import com.bentork.ev_system.service.OpportunityService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmLeadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LeadService leadService;

    @Mock
    private OpportunityService opportunityService;

    @Mock
    private AdminRepository adminRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CrmLeadController controller = new CrmLeadController(leadService, opportunityService, adminRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/crm/leads/bulk-assign should assign leads successfully")
    void testBulkAssign() throws Exception {
        BulkLeadAssignDTO dto = BulkLeadAssignDTO.builder()
                .leadIds(List.of(1L, 2L, 3L))
                .targetOwnerAdminId(5L)
                .build();

        BulkOperationResponse response = new BulkOperationResponse();
        response.setTotalRequested(3);
        response.setSuccessCount(3);
        response.setFailureCount(0);

        when(leadService.bulkAssignOwner(eq(List.of(1L, 2L, 3L)), eq(5L))).thenReturn(response);

        mockMvc.perform(post("/api/crm/leads/bulk-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(3))
                .andExpect(jsonPath("$.successCount").value(3));

        // Test alias path /bulk/assign
        mockMvc.perform(post("/api/crm/leads/bulk/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/crm/leads/bulk-tag should tag leads successfully")
    void testBulkTag() throws Exception {
        BulkLeadTagDTO dto = BulkLeadTagDTO.builder()
                .leadIds(List.of(1L, 2L))
                .tags(List.of("VIP", "HOT"))
                .build();

        BulkOperationResponse response = new BulkOperationResponse();
        response.setTotalRequested(2);
        response.setSuccessCount(2);

        when(leadService.bulkTag(eq(List.of(1L, 2L)), anyList())).thenReturn(response);

        mockMvc.perform(post("/api/crm/leads/bulk-tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(2));

        // Test alias path /bulk/tag
        mockMvc.perform(post("/api/crm/leads/bulk/tag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/crm/leads/import should import leads from CSV")
    void testCsvImport() throws Exception {
        String csvContent = "title,contactName,contactPhone,companyName\nLead 1,John,9876543210,Acme\n";
        MockMultipartFile file = new MockMultipartFile("file", "leads.csv", "text/csv", csvContent.getBytes());

        CsvImportResponse resp = new CsvImportResponse();
        resp.setTotalRows(1);
        resp.setImportedCount(1);
        resp.setSkippedCount(0);

        when(leadService.importFromCsv(any(), any())).thenReturn(resp);

        mockMvc.perform(multipart("/api/crm/leads/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.importedCount").value(1));

        // Test alias /csv-import
        mockMvc.perform(multipart("/api/crm/leads/csv-import").file(file))
                .andExpect(status().isOk());
    }
}
