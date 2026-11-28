package com.bentork.ev_system.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.BulkLeadAssignDTO;
import com.bentork.ev_system.dto.request.BulkLeadTagDTO;
import com.bentork.ev_system.dto.request.CombinedCallLeadDTO;
import com.bentork.ev_system.dto.request.CreateLeadDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.response.BulkOperationResponse;
import com.bentork.ev_system.dto.response.CsvImportResponse;
import com.bentork.ev_system.dto.response.LeadResponse;
import com.bentork.ev_system.exception.DuplicateLeadException;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.service.LeadService;
import com.bentork.ev_system.service.OpportunityService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    @DisplayName("POST /api/crm/leads/create should return 409 Conflict for duplicate phone or email")
    void testCreateLead_DuplicateReturns409() throws Exception {
        CreateLeadDTO dto = new CreateLeadDTO();
        dto.setTitle("Duplicate Lead");
        dto.setSource("website");
        dto.setPhone("9876543210");

        when(leadService.createLead(any(CreateLeadDTO.class), anyString()))
                .thenThrow(new DuplicateLeadException("Lead with mobile number '9876543210' already exists", "phone", 15L, "LD-20261128-0015"));

        mockMvc.perform(post("/api/crm/leads/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.duplicateField").value("phone"))
                .andExpect(jsonPath("$.existingLeadId").value(15));
    }

    @Test
    @DisplayName("POST /api/crm/leads/combined-call should return 409 Conflict for duplicate email")
    void testCombinedCall_DuplicateReturns409() throws Exception {
        CombinedCallLeadDTO dto = new CombinedCallLeadDTO();
        dto.setContactName("Anita");
        dto.setContactPhone("9876543210");
        dto.setContactEmail("anita@corp.com");

        when(leadService.combinedCallAndLead(any(CombinedCallLeadDTO.class), anyString()))
                .thenThrow(new DuplicateLeadException("Lead with email 'anita@corp.com' already exists", "email", 22L, "LD-20261128-0022"));

        mockMvc.perform(post("/api/crm/leads/combined-call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.duplicateField").value("email"));
    }

    @Test
    @DisplayName("Lost request, Admin approve, Admin revoke, and pending-lost endpoints on Leads")
    void testLeadLostWorkflowEndpoints() throws Exception {
        LeadResponse requested = new LeadResponse();
        requested.setId(5L);
        requested.setStatus("contacted");
        requested.setPreviousStatus("contacted");
        requested.setLostReason("No budget");
        requested.setLostApprovalStatus("PENDING");

        when(leadService.requestLost(eq(5L), any(LostRequestDTO.class), anyString())).thenReturn(requested);

        mockMvc.perform(post("/api/crm/leads/5/lost-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LostRequestDTO.builder().lostReason("No budget").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lostApprovalStatus").value("PENDING"))
                .andExpect(jsonPath("$.previousStatus").value("contacted"));

        LeadResponse approved = new LeadResponse();
        approved.setId(5L);
        approved.setStatus("lost");
        approved.setLostApprovalStatus("APPROVED");
        when(leadService.approveLost(eq(5L), any(), anyString())).thenReturn(approved);

        mockMvc.perform(put("/api/crm/leads/5/lost/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("lost"))
                .andExpect(jsonPath("$.lostApprovalStatus").value("APPROVED"));

        LeadResponse revoked = new LeadResponse();
        revoked.setId(5L);
        revoked.setStatus("contacted");
        revoked.setLostApprovalStatus("REVOKED");
        when(leadService.revokeLost(eq(5L), any(AdminLostActionDTO.class), anyString())).thenReturn(revoked);

        mockMvc.perform(post("/api/crm/leads/5/lost/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminLostActionDTO.builder().reason("Reopened").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("contacted"))
                .andExpect(jsonPath("$.lostApprovalStatus").value("REVOKED"));

        when(leadService.getPendingLostRequests()).thenReturn(List.of(requested));
        mockMvc.perform(get("/api/crm/leads/lost-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
    }

    @Test
    @DisplayName("POST /api/crm/leads/create should map concurrent DB unique constraint DataIntegrityViolationException to 409 Conflict")
    void testCreateLead_ConcurrentDbUniqueViolationReturns409() throws Exception {
        CreateLeadDTO dto = new CreateLeadDTO();
        dto.setTitle("Concurrent Lead");
        dto.setSource("website");
        dto.setPhone("9876543210");

        when(leadService.createLead(any(CreateLeadDTO.class), anyString()))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry '919876543210' for key 'leads.uk_lead_phone'"));

        mockMvc.perform(post("/api/crm/leads/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
