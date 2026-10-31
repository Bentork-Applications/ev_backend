package com.bentork.ev_system.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;

import com.bentork.ev_system.dto.followup.FollowUpCampaignResponse;
import com.bentork.ev_system.dto.followup.FollowUpDispatchResultDTO;
import com.bentork.ev_system.dto.followup.FollowUpMessageConfigDTO;
import com.bentork.ev_system.dto.followup.FollowUpProgressResponse;
import com.bentork.ev_system.dto.followup.FollowUpSelectLeadsDTO;
import com.bentork.ev_system.dto.response.WhatsAppSendResult;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpMessage;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.model.followup.LeadFollowUpRoundLead;
import com.bentork.ev_system.model.followup.LeadImportBatch;
import com.bentork.ev_system.model.followup.LeadImportBatchLead;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.ContactSuppressionRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpAttachmentRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpCampaignRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpDeliveryLogRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpMessageRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundLeadRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;
import com.bentork.ev_system.repository.followup.LeadImportBatchLeadRepository;
import com.bentork.ev_system.repository.followup.LeadImportBatchRepository;
import com.bentork.ev_system.service.followup.FollowUpAttachmentService;
import com.bentork.ev_system.service.followup.FollowUpImportService;
import com.bentork.ev_system.service.followup.FollowUpMessageDispatchService;
import com.bentork.ev_system.service.followup.FollowUpRoundValidationService;
import com.bentork.ev_system.service.followup.FollowUpSelectionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FollowUpCampaignServiceTest {

    // Repositories
    @Mock private LeadImportBatchRepository batchRepository;
    @Mock private LeadImportBatchLeadRepository batchLeadRepository;
    @Mock private LeadFollowUpCampaignRepository campaignRepository;
    @Mock private LeadFollowUpRoundRepository roundRepository;
    @Mock private LeadFollowUpRoundLeadRepository roundLeadRepository;
    @Mock private LeadFollowUpMessageRepository messageRepository;
    @Mock private LeadFollowUpAttachmentRepository attachmentRepository;
    @Mock private LeadFollowUpDeliveryLogRepository deliveryLogRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private ContactSuppressionRepository suppressionRepository;

    // Services & integrations
    @Mock private DuplicateMatchingService duplicateMatchingService;
    @Mock private WhatsAppDispatchService whatsAppDispatchService;
    @Mock private EmailDispatchService emailDispatchService;
    @Mock private JavaMailSender mailSender;

    private FollowUpRoundValidationService validationService;
    private FollowUpAttachmentService attachmentService;
    private FollowUpImportService importService;
    private FollowUpSelectionService selectionService;
    private FollowUpMessageDispatchService dispatchService;

    private Admin adminUser;
    private LeadImportBatch batch;
    private LeadFollowUpCampaign campaign;
    private List<LeadFollowUpRound> rounds;
    private List<Lead> testLeads;

    @BeforeEach
    void setUp() {
        validationService = new FollowUpRoundValidationService(batchLeadRepository, roundLeadRepository);
        attachmentService = new FollowUpAttachmentService(attachmentRepository, roundRepository, validationService);
        importService = new FollowUpImportService(
                batchRepository, batchLeadRepository, campaignRepository,
                roundRepository, leadRepository, adminRepository, duplicateMatchingService);
        selectionService = new FollowUpSelectionService(
                campaignRepository, roundRepository, roundLeadRepository,
                batchLeadRepository, leadRepository, validationService);
        dispatchService = new FollowUpMessageDispatchService(
                campaignRepository, roundRepository, roundLeadRepository,
                messageRepository, deliveryLogRepository, suppressionRepository,
                validationService, attachmentService, whatsAppDispatchService,
                emailDispatchService, mailSender);

        adminUser = new Admin();
        adminUser.setId(1L);
        adminUser.setEmail("admin@bentork.com");
        adminUser.setName("Sales Admin");

        batch = LeadImportBatch.builder()
                .id(10L)
                .batchNumber("BATCH-20261030-001")
                .originalFileName("leads.xlsx")
                .totalRows(500)
                .validRows(500)
                .build();

        campaign = LeadFollowUpCampaign.builder()
                .id(100L)
                .name("EV Fleet Outreach")
                .batch(batch)
                .channel("WHATSAPP")
                .status("DRAFT")
                .currentRoundNumber(1)
                .totalRounds(5)
                .completedRounds(0)
                .ownerAdmin(adminUser)
                .build();

        rounds = new ArrayList<>();
        LeadFollowUpRound parent = null;
        for (int i = 1; i <= 5; i++) {
            LeadFollowUpRound r = LeadFollowUpRound.builder()
                    .id((long) (200 + i))
                    .campaign(campaign)
                    .roundNumber(i)
                    .parentRound(parent)
                    .channel("WHATSAPP")
                    .subject("Round " + i)
                    .messageBody("Hello {{name}}, this is round " + i)
                    .status("PENDING")
                    .selectedLeadCount(0)
                    .sentCount(0)
                    .deliveredCount(0)
                    .failedCount(0)
                    .build();
            rounds.add(r);
            parent = r;
        }

        testLeads = new ArrayList<>();
        for (long i = 1; i <= 500; i++) {
            Lead l = new Lead();
            l.setId(i);
            l.setLeadNumber("LD-TEST-" + i);
            l.setTitle("Customer " + i);
            l.setPhone("9198765" + String.format("%05d", i));
            testLeads.add(l);
        }
    }

    @Test
    @DisplayName("1. One-time Excel import creates batch and scaffolds 5 rounds")
    void testOneTimeExcelImport_CreatesBatchAndScaffolds5Rounds() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Leads");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("name");
        header.createCell(1).setCellValue("phone");
        header.createCell(2).setCellValue("email");
        header.createCell(3).setCellValue("company");

        for (int i = 1; i <= 10; i++) {
            Row r = sheet.createRow(i);
            r.createCell(0).setCellValue("Customer " + i);
            r.createCell(1).setCellValue("987650000" + i);
            r.createCell(2).setCellValue("cust" + i + "@example.com");
            r.createCell(3).setCellValue("Company " + i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "leads.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray());

        when(batchRepository.save(any(LeadImportBatch.class))).thenAnswer(inv -> {
            LeadImportBatch b = inv.getArgument(0);
            b.setId(50L);
            return b;
        });

        when(duplicateMatchingService.matchByPhone(anyString()))
                .thenReturn(DuplicateMatchingService.MatchResult.builder().matched(false).build());

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead l = inv.getArgument(0);
            l.setId(1000L + (long) (Math.random() * 500));
            return l;
        });

        when(campaignRepository.save(any(LeadFollowUpCampaign.class))).thenAnswer(inv -> {
            LeadFollowUpCampaign c = inv.getArgument(0);
            c.setId(300L);
            return c;
        });

        when(roundRepository.save(any(LeadFollowUpRound.class))).thenAnswer(inv -> {
            LeadFollowUpRound r = inv.getArgument(0);
            r.setId(500L + r.getRoundNumber());
            return r;
        });

        FollowUpCampaignResponse response = importService.importAndCreateCampaign(
                file, "Q4 Fleet Campaign", "WHATSAPP", null, "admin@bentork.com");

        assertNotNull(response);
        assertEquals("Q4 Fleet Campaign", response.getName());
        assertEquals(5, response.getTotalRounds());
        assertEquals(5, response.getRounds().size());
        assertEquals(1, response.getRounds().get(0).getRoundNumber());
        assertEquals(5, response.getRounds().get(4).getRoundNumber());
        verify(batchRepository, atLeastOnce()).save(any(LeadImportBatch.class));
        verify(batchLeadRepository, atLeast(10)).save(any(LeadImportBatchLead.class));
        verify(roundRepository, times(5)).save(any(LeadFollowUpRound.class));
    }

    @Test
    @DisplayName("2. Sequential Cascading Selection: 500 -> 300 -> 250 -> 100 -> 10")
    void testSequentialSelection_500To300To250To100To10() {
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 1)).thenReturn(Optional.of(rounds.get(0)));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 2)).thenReturn(Optional.of(rounds.get(1)));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 3)).thenReturn(Optional.of(rounds.get(2)));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 4)).thenReturn(Optional.of(rounds.get(3)));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 5)).thenReturn(Optional.of(rounds.get(4)));

        // Round 1: Select 500 leads from batch
        List<Long> all500Ids = new ArrayList<>();
        for (long i = 1; i <= 500; i++) all500Ids.add(i);
        when(batchLeadRepository.findLeadIdsByBatchId(batch.getId())).thenReturn(all500Ids);
        when(leadRepository.findAllById(all500Ids)).thenReturn(testLeads);

        Map<String, Object> r1Res = selectionService.selectLeadsForRound(
                100L, 1, FollowUpSelectLeadsDTO.builder().leadIds(all500Ids).build(), "admin@bentork.com");
        assertEquals(500, r1Res.get("selectedCount"));

        // Mark Round 1 as COMPLETED to unlock Round 2
        rounds.get(0).setStatus("COMPLETED");
        rounds.get(0).setSelectedLeadCount(500);

        // Round 2: Select 300 leads from Round 1
        List<Long> r2Selected300 = all500Ids.subList(0, 300);
        when(roundLeadRepository.findSelectedLeadIdsByRoundId(rounds.get(0).getId())).thenReturn(all500Ids);
        when(leadRepository.findAllById(r2Selected300)).thenReturn(testLeads.subList(0, 300));

        Map<String, Object> r2Res = selectionService.selectLeadsForRound(
                100L, 2, FollowUpSelectLeadsDTO.builder().leadIds(r2Selected300).build(), "admin@bentork.com");
        assertEquals(300, r2Res.get("selectedCount"));

        // Mark Round 2 as COMPLETED to unlock Round 3
        rounds.get(1).setStatus("COMPLETED");
        rounds.get(1).setSelectedLeadCount(300);

        // Round 3: Select 250 leads from Round 2
        List<Long> r3Selected250 = r2Selected300.subList(0, 250);
        when(roundLeadRepository.findSelectedLeadIdsByRoundId(rounds.get(1).getId())).thenReturn(r2Selected300);
        when(leadRepository.findAllById(r3Selected250)).thenReturn(testLeads.subList(0, 250));

        Map<String, Object> r3Res = selectionService.selectLeadsForRound(
                100L, 3, FollowUpSelectLeadsDTO.builder().leadIds(r3Selected250).build(), "admin@bentork.com");
        assertEquals(250, r3Res.get("selectedCount"));

        // Mark Round 3 as COMPLETED to unlock Round 4
        rounds.get(2).setStatus("COMPLETED");
        rounds.get(2).setSelectedLeadCount(250);

        // Round 4: Select 100 leads from Round 3
        List<Long> r4Selected100 = r3Selected250.subList(0, 100);
        when(roundLeadRepository.findSelectedLeadIdsByRoundId(rounds.get(2).getId())).thenReturn(r3Selected250);
        when(leadRepository.findAllById(r4Selected100)).thenReturn(testLeads.subList(0, 100));

        Map<String, Object> r4Res = selectionService.selectLeadsForRound(
                100L, 4, FollowUpSelectLeadsDTO.builder().leadIds(r4Selected100).build(), "admin@bentork.com");
        assertEquals(100, r4Res.get("selectedCount"));

        // Mark Round 4 as COMPLETED to unlock Round 5
        rounds.get(3).setStatus("COMPLETED");
        rounds.get(3).setSelectedLeadCount(100);

        // Round 5: Select 10 leads from Round 4
        List<Long> r5Selected10 = r4Selected100.subList(0, 10);
        when(roundLeadRepository.findSelectedLeadIdsByRoundId(rounds.get(3).getId())).thenReturn(r4Selected100);
        when(leadRepository.findAllById(r5Selected10)).thenReturn(testLeads.subList(0, 10));

        Map<String, Object> r5Res = selectionService.selectLeadsForRound(
                100L, 5, FollowUpSelectLeadsDTO.builder().leadIds(r5Selected10).build(), "admin@bentork.com");
        assertEquals(10, r5Res.get("selectedCount"));
    }

    @Test
    @DisplayName("3. Reject candidate leads that do not belong to immediate parent round")
    void testRejectingLeadsOutsideParentRound() {
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 3)).thenReturn(Optional.of(rounds.get(2)));

        // Round 2 is completed with leads [1, 2, 3]
        rounds.get(1).setStatus("COMPLETED");
        when(roundLeadRepository.findSelectedLeadIdsByRoundId(rounds.get(1).getId())).thenReturn(Arrays.asList(1L, 2L, 3L));

        // User attempts to select leads [1, 2, 999] in Round 3 (where 999 was not in Round 2)
        FollowUpSelectLeadsDTO dto = FollowUpSelectLeadsDTO.builder()
                .leadIds(Arrays.asList(1L, 2L, 999L))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                selectionService.selectLeadsForRound(100L, 3, dto, "admin@bentork.com"));

        assertTrue(ex.getMessage().contains("Cascading selection rejected"));
        assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    @DisplayName("4. Prevent later rounds from starting early (locked until parent completes)")
    void testPreventingLaterRoundsFromStartingEarly() {
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 2)).thenReturn(Optional.of(rounds.get(1)));

        // Round 1 is PENDING (not COMPLETED)
        rounds.get(0).setStatus("PENDING");

        FollowUpSelectLeadsDTO dto = FollowUpSelectLeadsDTO.builder()
                .leadIds(Arrays.asList(1L, 2L))
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                selectionService.selectLeadsForRound(100L, 2, dto, "admin@bentork.com"));

        assertTrue(ex.getMessage().contains("Round 2 is locked"));
        assertTrue(ex.getMessage().contains("Round 1 must be COMPLETED"));
    }

    @Test
    @DisplayName("5. Allow same lead to appear sequentially across valid rounds")
    void testAllowingSameLeadInMultipleSequentialRounds() {
        Lead lead1 = testLeads.get(0);

        // Round 1 valid leads
        when(batchLeadRepository.findLeadIdsByBatchId(batch.getId())).thenReturn(Collections.singletonList(lead1.getId()));
        validationService.validateLeadLineage(campaign, rounds.get(0), Collections.singletonList(lead1.getId()));

        // Round 2 valid leads: Lead 1 was selected in Round 1
        when(roundLeadRepository.findSelectedLeadIdsByRoundId(rounds.get(0).getId())).thenReturn(Collections.singletonList(lead1.getId()));
        validationService.validateLeadLineage(campaign, rounds.get(1), Collections.singletonList(lead1.getId()));

        // Round 3 valid leads: Lead 1 was selected in Round 2
        when(roundLeadRepository.findSelectedLeadIdsByRoundId(rounds.get(1).getId())).thenReturn(Collections.singletonList(lead1.getId()));
        validationService.validateLeadLineage(campaign, rounds.get(2), Collections.singletonList(lead1.getId()));

        // No exceptions thrown: verified that same lead correctly survives the cascading funnel!
        assertTrue(true);
    }

    @Test
    @DisplayName("6. Dispatch round messages, track provider responses, and complete round")
    void testRoundDispatchAndDeliveryTracking() {
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 1)).thenReturn(Optional.of(rounds.get(0)));

        rounds.get(0).setSelectedLeadCount(2);
        rounds.get(0).setMessageBody("Hello {{name}}, welcome to Bentork EV!");

        LeadFollowUpRoundLead rl1 = LeadFollowUpRoundLead.builder()
                .id(11L)
                .round(rounds.get(0))
                .campaign(campaign)
                .lead(testLeads.get(0))
                .selectionStatus("SELECTED")
                .deliveryStatus("PENDING")
                .build();

        LeadFollowUpRoundLead rl2 = LeadFollowUpRoundLead.builder()
                .id(12L)
                .round(rounds.get(0))
                .campaign(campaign)
                .lead(testLeads.get(1))
                .selectionStatus("SELECTED")
                .deliveryStatus("PENDING")
                .build();

        when(roundLeadRepository.findByRoundIdAndSelectionStatus(rounds.get(0).getId(), "SELECTED"))
                .thenReturn(Arrays.asList(rl1, rl2));

        when(whatsAppDispatchService.sendCampaignMessage(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(WhatsAppSendResult.builder()
                        .success(true)
                        .providerMessageId("wamid.test_12345")
                        .status("SUBMITTED")
                        .build());

        when(messageRepository.save(any(LeadFollowUpMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowUpDispatchResultDTO result = dispatchService.dispatchRound(100L, 1, "admin@bentork.com");

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(2, result.getSentCount());
        assertEquals(0, result.getFailedCount());
        assertEquals(2, result.getNextRoundUnlocked());
        assertEquals("COMPLETED", rounds.get(0).getStatus());
        verify(roundRepository, atLeastOnce()).save(rounds.get(0));
    }

    @Test
    @DisplayName("7. Duplicate send protection via idempotency prevents repeated sends")
    void testDuplicateSendProtectionWithIdempotency() {
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 1)).thenReturn(Optional.of(rounds.get(0)));

        rounds.get(0).setSelectedLeadCount(1);
        rounds.get(0).setMessageBody("Special follow-up message");

        LeadFollowUpRoundLead rl1 = LeadFollowUpRoundLead.builder()
                .id(11L)
                .round(rounds.get(0))
                .campaign(campaign)
                .lead(testLeads.get(0))
                .selectionStatus("SELECTED")
                .deliveryStatus("PENDING")
                .build();

        when(roundLeadRepository.findByRoundIdAndSelectionStatus(rounds.get(0).getId(), "SELECTED"))
                .thenReturn(Collections.singletonList(rl1));

        String idempotencyKey = LeadFollowUpMessage.generateIdempotencyKey(100L, 1, testLeads.get(0).getId(), 0);
        LeadFollowUpMessage alreadySent = LeadFollowUpMessage.builder()
                .id(88L)
                .status("SENT")
                .idempotencyKey(idempotencyKey)
                .build();

        when(messageRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(alreadySent));

        FollowUpDispatchResultDTO result = dispatchService.dispatchRound(100L, 1, "admin@bentork.com");

        assertEquals(1, result.getSentCount());
        // Verify provider dispatch was SKIPPED because it was already sent
        verify(whatsAppDispatchService, never()).sendCampaignMessage(anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("8. Safe retry of failed messages without duplicating successful sends")
    void testRetryFailedMessagesWithoutDuplicateSends() {
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 1)).thenReturn(Optional.of(rounds.get(0)));

        LeadFollowUpRoundLead rlFail = LeadFollowUpRoundLead.builder()
                .id(15L)
                .lead(testLeads.get(4))
                .round(rounds.get(0))
                .deliveryStatus("FAILED")
                .build();

        LeadFollowUpMessage failedMsg = LeadFollowUpMessage.builder()
                .id(99L)
                .roundLead(rlFail)
                .round(rounds.get(0))
                .lead(testLeads.get(4))
                .recipientPhone("919876500005")
                .status("FAILED")
                .retryCount(0)
                .build();

        when(messageRepository.findByRoundIdAndStatus(rounds.get(0).getId(), "FAILED"))
                .thenReturn(Collections.singletonList(failedMsg));

        when(whatsAppDispatchService.sendCampaignMessage(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(WhatsAppSendResult.builder().success(true).providerMessageId("wamid.retry_success").build());

        when(messageRepository.countByRoundIdAndStatus(rounds.get(0).getId(), "FAILED")).thenReturn(0L);
        when(messageRepository.countByRoundIdAndStatus(rounds.get(0).getId(), "SENT")).thenReturn(1L);

        FollowUpDispatchResultDTO retryRes = dispatchService.retryFailedMessages(100L, 1, null, "admin@bentork.com");

        assertNotNull(retryRes);
        assertEquals(1, retryRes.getSentCount());
        assertEquals(0, retryRes.getFailedCount());
        assertEquals("SENT", failedMsg.getStatus());
        assertEquals(1, failedMsg.getRetryCount());
    }

    @Test
    @DisplayName("9. Campaign automatically transitions to COMPLETED after Round 5 dispatch")
    void testCampaignCompletionAfterRound5() {
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(roundRepository.findByCampaignIdAndRoundNumber(100L, 5)).thenReturn(Optional.of(rounds.get(4)));

        // Previous rounds 1-4 are completed
        for (int i = 0; i < 4; i++) {
            rounds.get(i).setStatus("COMPLETED");
        }

        rounds.get(4).setSelectedLeadCount(1);
        rounds.get(4).setMessageBody("Final Round 5 closing offer");

        LeadFollowUpRoundLead rl5 = LeadFollowUpRoundLead.builder()
                .id(50L)
                .round(rounds.get(4))
                .campaign(campaign)
                .lead(testLeads.get(0))
                .selectionStatus("SELECTED")
                .deliveryStatus("PENDING")
                .build();

        when(roundLeadRepository.findByRoundIdAndSelectionStatus(rounds.get(4).getId(), "SELECTED"))
                .thenReturn(Collections.singletonList(rl5));

        when(whatsAppDispatchService.sendCampaignMessage(anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(WhatsAppSendResult.builder().success(true).providerMessageId("wamid.round5_ack").build());

        when(messageRepository.save(any(LeadFollowUpMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowUpDispatchResultDTO result = dispatchService.dispatchRound(100L, 5, "admin@bentork.com");

        assertNotNull(result);
        assertEquals("COMPLETED", rounds.get(4).getStatus());
        assertEquals("COMPLETED", campaign.getStatus());
        assertNotNull(campaign.getCompletedAt());
        assertNull(result.getNextRoundUnlocked()); // Round 5 is the final round
    }

    @Test
    @DisplayName("10. Progress waterfall calculation correctly shows retention rate")
    void testCampaignProgressWaterfall() {
        when(campaignRepository.findById(100L)).thenReturn(Optional.of(campaign));
        when(roundRepository.findByCampaignIdOrderByRoundNumberAsc(100L)).thenReturn(rounds);

        rounds.get(0).setSelectedLeadCount(500);
        rounds.get(1).setSelectedLeadCount(300);
        rounds.get(2).setSelectedLeadCount(250);
        rounds.get(3).setSelectedLeadCount(100);
        rounds.get(4).setSelectedLeadCount(10);

        FollowUpProgressResponse progress = dispatchService.getCampaignProgress(100L);

        assertNotNull(progress);
        assertEquals(5, progress.getWaterfall().size());
        assertEquals(500, progress.getWaterfall().get(0).getSelectedCount());
        assertEquals(300, progress.getWaterfall().get(1).getSelectedCount());
        assertEquals(250, progress.getWaterfall().get(2).getSelectedCount());
        assertEquals(100, progress.getWaterfall().get(3).getSelectedCount());
        assertEquals(10, progress.getWaterfall().get(4).getSelectedCount());
        assertEquals("2.0%", progress.getOverallRetentionRate()); // 10 / 500 = 2.0%
    }
}

