package com.bentork.ev_system.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.CombinedCallLeadDTO;
import com.bentork.ev_system.dto.request.CreateLeadDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.request.UpdateLeadStatusDTO;
import com.bentork.ev_system.dto.response.CsvImportResponse;
import com.bentork.ev_system.dto.response.LeadResponse;
import com.bentork.ev_system.exception.DuplicateLeadException;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.SalesCompany;
import com.bentork.ev_system.model.SalesContact;
import com.bentork.ev_system.repository.ActivityRepository;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.SalesCompanyRepository;
import com.bentork.ev_system.repository.SalesContactRepository;
import com.bentork.ev_system.service.interfaces.IAdminNotificationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private SalesCompanyRepository companyRepository;
    @Mock
    private SalesContactRepository contactRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private IAdminNotificationService adminNotificationService;

    private DuplicateMatchingService duplicateMatchingService;
    private LeadService leadService;

    private Admin salesAdmin;
    private Admin superAdmin;

    @BeforeEach
    void setUp() {
        duplicateMatchingService = new DuplicateMatchingService(leadRepository, contactRepository);
        leadService = new LeadService(
                leadRepository,
                companyRepository,
                contactRepository,
                activityRepository,
                adminRepository,
                duplicateMatchingService,
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
    }

    @Test
    @DisplayName("createLead should save normalized phone & email when unique")
    void testCreateLead_Success() {
        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));
        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(contactRepository.findFirstByPhoneIn(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("buyer@acme.com")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactEmailIgnoreCaseOrderByCreatedAtDesc("buyer@acme.com")).thenReturn(Optional.empty());
        when(contactRepository.findByEmailIgnoreCase("buyer@acme.com")).thenReturn(Optional.empty());
        when(leadRepository.saveAndFlush(any(Lead.class))).thenAnswer(inv -> {
            Lead l = inv.getArgument(0);
            l.setId(100L);
            return l;
        });

        CreateLeadDTO dto = new CreateLeadDTO();
        dto.setTitle("48V Battery Enquiry");
        dto.setSource("website");
        dto.setPhone("+91 98765-43210");
        dto.setEmail("Buyer@Acme.com");

        LeadResponse response = leadService.createLead(dto, "rahul@bentork.com");

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("919876543210", response.getContactPhone());
        assertEquals("buyer@acme.com", response.getContactEmail());
        verify(leadRepository).saveAndFlush(any(Lead.class));
    }

    @Test
    @DisplayName("createLead should throw DuplicateLeadException (409) when phone already exists")
    void testCreateLead_DuplicatePhoneThrows409() {
        Lead existing = new Lead();
        existing.setId(50L);
        existing.setLeadNumber("LD-20261128-0050");

        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));
        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.of(existing));

        CreateLeadDTO dto = new CreateLeadDTO();
        dto.setTitle("Duplicate Phone Lead");
        dto.setSource("website");
        dto.setPhone("9876543210");

        DuplicateLeadException ex = assertThrows(DuplicateLeadException.class,
                () -> leadService.createLead(dto, "rahul@bentork.com"));

        assertEquals("phone", ex.getDuplicateField());
        assertEquals(50L, ex.getExistingLeadId());
        verify(leadRepository, never()).saveAndFlush(any());
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("combinedCallAndLead should reject duplicate email before saving any Contact, Company, Lead, or Activity")
    void testCombinedCallAndLead_DuplicateEmailThrows409() {
        Lead existing = new Lead();
        existing.setId(60L);
        existing.setLeadNumber("LD-20261128-0060");

        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));
        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(contactRepository.findFirstByPhoneIn(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("dup@fleet.in")).thenReturn(Optional.of(existing));

        CombinedCallLeadDTO dto = new CombinedCallLeadDTO();
        dto.setContactName("Vikram");
        dto.setContactPhone("9911223344");
        dto.setContactEmail("DUP@FLEET.IN");
        dto.setCompanyName("Fleet Corp");

        DuplicateLeadException ex = assertThrows(DuplicateLeadException.class,
                () -> leadService.combinedCallAndLead(dto, "rahul@bentork.com"));

        assertEquals("email", ex.getDuplicateField());
        verify(companyRepository, never()).save(any());
        verify(contactRepository, never()).save(any());
        verify(contactRepository, never()).saveAndFlush(any());
        verify(leadRepository, never()).saveAndFlush(any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    @DisplayName("importFromCsv should skip DB and intra-file duplicates in CSV imports")
    void testImportCsv_SkipsDuplicatePhoneAndEmail() {
        String csv = "title,contactName,contactPhone,contactEmail,companyName\n"
                + "Lead A,Alice,9876543210,alice@corp.com,Corp A\n"
                + "Lead B,Bob,+91 98765-43210,bob@corp.com,Corp B\n"   // intra-file duplicate phone
                + "Lead C,Carol,9988776655,ALICE@CORP.COM,Corp C\n"  // intra-file duplicate email
                + "Lead D,Dan,9123456789,dan@db.com,Corp D\n";       // DB duplicate email

        MockMultipartFile file = new MockMultipartFile(
                "file", "leads.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        Lead existingDbLead = new Lead();
        existingDbLead.setId(999L);
        existingDbLead.setLeadNumber("LD-DB-999");

        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));
        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(contactRepository.findFirstByPhoneIn(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("alice@corp.com")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactEmailIgnoreCaseOrderByCreatedAtDesc("alice@corp.com")).thenReturn(Optional.empty());
        when(contactRepository.findByEmailIgnoreCase("alice@corp.com")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("dan@db.com")).thenReturn(Optional.of(existingDbLead));

        when(companyRepository.findByNameContainingIgnoreCaseAndActiveTrue(anyString())).thenReturn(List.of());
        when(companyRepository.save(any(SalesCompany.class))).thenAnswer(inv -> inv.getArgument(0));
        when(contactRepository.save(any(SalesContact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        CsvImportResponse resp = leadService.importFromCsv(file, "rahul@bentork.com");

        assertEquals(4, resp.getTotalRows());
        assertEquals(1, resp.getImportedCount());
        assertEquals(3, resp.getSkippedCount());
        assertEquals(3, resp.getDuplicateCount());
        verify(leadRepository, times(1)).save(any(Lead.class));
    }

    @Test
    @DisplayName("importFromCsv should parse .xlsx Excel files and prevent duplicate leads")
    void testImportExcel_SkipsDuplicates() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Leads");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("title");
        header.createCell(1).setCellValue("contactName");
        header.createCell(2).setCellValue("contactPhone");
        header.createCell(3).setCellValue("contactEmail");

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("Excel Lead 1");
        row1.createCell(1).setCellValue("Suresh");
        row1.createCell(2).setCellValue("9000011111");
        row1.createCell(3).setCellValue("suresh@ev.com");

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("Excel Lead 2");
        row2.createCell(1).setCellValue("Suresh Dup");
        row2.createCell(2).setCellValue("9000011111"); // duplicate within excel
        row2.createCell(3).setCellValue("other@ev.com");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        wb.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "leads.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray());

        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));
        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(contactRepository.findFirstByPhoneIn(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactEmailIgnoreCaseOrderByCreatedAtDesc(anyString())).thenReturn(Optional.empty());
        when(contactRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(contactRepository.save(any(SalesContact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        CsvImportResponse resp = leadService.importFromCsv(file, "rahul@bentork.com");

        assertEquals(2, resp.getTotalRows());
        assertEquals(1, resp.getImportedCount());
        assertEquals(1, resp.getDuplicateCount());
        verify(leadRepository, times(1)).save(any(Lead.class));
    }

    @Test
    @DisplayName("Lead Lost Request -> Admin Approve -> Admin Revoke restores previousStatus")
    void testLeadLostApproveAndRevokeWorkflow() {
        Lead lead = new Lead();
        lead.setId(200L);
        lead.setLeadNumber("LD-20261128-0200");
        lead.setTitle("Depot Charging Lead");
        lead.setStatus("contacted");
        lead.setOwnerAdmin(salesAdmin);

        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));
        when(adminRepository.findByEmail("admin@bentork.com")).thenReturn(Optional.of(superAdmin));
        when(leadRepository.findById(200L)).thenReturn(Optional.of(lead));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Sales Rep submits Lost Request
        LostRequestDTO reqDto = LostRequestDTO.builder().lostReason("Bought from competitor").build();
        LeadResponse requested = leadService.requestLost(200L, reqDto, "rahul@bentork.com");

        assertEquals("contacted", requested.getStatus());
        assertEquals("contacted", requested.getPreviousStatus());
        assertEquals("Bought from competitor", requested.getLostReason());
        assertEquals("PENDING", requested.getLostApprovalStatus());
        assertEquals(10L, requested.getLostRequestedByAdminId());
        verify(adminNotificationService).notifyAdminsOfLostRequest(
                eq("Lead"), eq(200L), eq("LD-20261128-0200"), eq("Depot Charging Lead"),
                eq("contacted"), eq("Bought from competitor"), eq(salesAdmin));

        // Normal status transitions blocked while pending
        UpdateLeadStatusDTO statusDto = new UpdateLeadStatusDTO();
        statusDto.setStatus("qualified");
        assertThrows(IllegalStateException.class,
                () -> leadService.updateLeadStatus(200L, statusDto, "rahul@bentork.com"));

        // Non-ADMIN cannot approve
        assertThrows(AccessDeniedException.class,
                () -> leadService.approveLost(200L, null, "rahul@bentork.com"));

        // 2. Super Admin approves Lost
        LeadResponse approved = leadService.approveLost(200L, null, "admin@bentork.com");
        assertEquals("lost", approved.getStatus());
        assertEquals("APPROVED", approved.getLostApprovalStatus());
        assertEquals("contacted", approved.getPreviousStatus());
        assertEquals(1L, approved.getLostApprovedByAdminId());
        assertNotNull(approved.getLostApprovedAt());

        // Direct status transition from LOST still blocked
        assertThrows(IllegalArgumentException.class,
                () -> leadService.updateLeadStatus(200L, statusDto, "admin@bentork.com"));

        // 3. Super Admin revokes Lost -> moves back to previousStatus ('contacted')
        AdminLostActionDTO revokeDto = AdminLostActionDTO.builder()
                .reason("Customer re-engaged for Q1")
                .build();
        LeadResponse revoked = leadService.revokeLost(200L, revokeDto, "admin@bentork.com");
        assertEquals("contacted", revoked.getStatus());
        assertEquals("REVOKED", revoked.getLostApprovalStatus());
        assertEquals(1L, revoked.getLostRevokedByAdminId());
        assertEquals("Customer re-engaged for Q1", revoked.getLostRevokeReason());
        assertNotNull(revoked.getLostRevokedAt());
    }

    @Test
    @DisplayName("Sales Admin cannot directly mark a Lead as LOST via updateLeadStatus")
    void testDirectLostBlockedForSalesAdmin() {
        Lead lead = new Lead();
        lead.setId(201L);
        lead.setLeadNumber("LD-20261128-0201");
        lead.setStatus("qualified");

        when(leadRepository.findById(201L)).thenReturn(Optional.of(lead));
        when(adminRepository.findByEmail("rahul@bentork.com")).thenReturn(Optional.of(salesAdmin));

        UpdateLeadStatusDTO dto = new UpdateLeadStatusDTO();
        dto.setStatus("lost");

        assertThrows(IllegalArgumentException.class,
                () -> leadService.updateLeadStatus(201L, dto, "rahul@bentork.com"));
        verify(leadRepository, never()).save(any());
    }

    @Test
    @DisplayName("Admin revoking legacy LOST Lead without previousStatus defaults back to qualified")
    void testRevokeLegacyLostLead_DefaultsToQualified() {
        Lead legacyLost = new Lead();
        legacyLost.setId(202L);
        legacyLost.setLeadNumber("LD-OLD-01");
        legacyLost.setTitle("Legacy Lost Lead");
        legacyLost.setStatus("lost");
        legacyLost.setPreviousStatus(null);

        when(adminRepository.findByEmail("admin@bentork.com")).thenReturn(Optional.of(superAdmin));
        when(leadRepository.findById(202L)).thenReturn(Optional.of(legacyLost));
        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> inv.getArgument(0));

        LeadResponse revoked = leadService.revokeLost(202L, null, "admin@bentork.com");

        assertEquals("qualified", revoked.getStatus());
        assertEquals("REVOKED", revoked.getLostApprovalStatus());
        assertEquals(1L, revoked.getLostRevokedByAdminId());
    }
}
