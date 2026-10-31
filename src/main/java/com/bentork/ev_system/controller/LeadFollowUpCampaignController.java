package com.bentork.ev_system.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bentork.ev_system.dto.followup.FollowUpAttachmentResponse;
import com.bentork.ev_system.dto.followup.FollowUpCampaignResponse;
import com.bentork.ev_system.dto.followup.FollowUpDispatchResultDTO;
import com.bentork.ev_system.dto.followup.FollowUpEligibleLeadDTO;
import com.bentork.ev_system.dto.followup.FollowUpMessageConfigDTO;
import com.bentork.ev_system.dto.followup.FollowUpMessageHistoryResponse;
import com.bentork.ev_system.dto.followup.FollowUpProgressResponse;
import com.bentork.ev_system.dto.followup.FollowUpRetryDTO;
import com.bentork.ev_system.dto.followup.FollowUpRoundResponse;
import com.bentork.ev_system.dto.followup.FollowUpSelectLeadsDTO;
import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.repository.followup.LeadFollowUpCampaignRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;
import com.bentork.ev_system.service.followup.FollowUpAttachmentService;
import com.bentork.ev_system.service.followup.FollowUpImportService;
import com.bentork.ev_system.service.followup.FollowUpMessageDispatchService;
import com.bentork.ev_system.service.followup.FollowUpSelectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for the 5-Round Sequential Lead Follow-up Campaign System.
 * Enforces strict cascading sequential rounds, batch isolation, and multi-channel messaging.
 */
@RestController
@RequestMapping("/api/crm/follow-up")
@RequiredArgsConstructor
@Slf4j
public class LeadFollowUpCampaignController {

    private final FollowUpImportService importService;
    private final FollowUpSelectionService selectionService;
    private final FollowUpMessageDispatchService dispatchService;
    private final FollowUpAttachmentService attachmentService;
    private final LeadFollowUpCampaignRepository campaignRepository;
    private final LeadFollowUpRoundRepository roundRepository;

    /**
     * 1. Single Excel/CSV file upload to initialize campaign and scaffold all 5 cascading rounds.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> importBatchAndCreateCampaign(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "campaignName", required = false) String campaignName,
            @RequestParam(value = "channel", defaultValue = "WHATSAPP") String channel,
            @RequestParam(value = "defaultOwnerAdminId", required = false) Long defaultOwnerAdminId) {
        try {
            FollowUpCampaignResponse response = importService.importAndCreateCampaign(
                    file, campaignName, channel, defaultOwnerAdminId, getCurrentUserEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Import failed: " + e.getMessage());
        }
    }

    /**
     * 2. List all 5-round follow-up campaigns.
     */
    @GetMapping("/campaigns")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<PagedResponse<FollowUpCampaignResponse>> getAllCampaigns(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<LeadFollowUpCampaign> campPage;
        if (status != null && !status.trim().isEmpty()) {
            campPage = campaignRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable);
        } else {
            campPage = campaignRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<FollowUpCampaignResponse> list = campPage.getContent().stream()
                .map(c -> {
                    List<LeadFollowUpRound> rounds = roundRepository.findByCampaignIdOrderByRoundNumberAsc(c.getId());
                    return importService.mapCampaignToResponse(c, c.getBatch(), rounds);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(PagedResponse.of(
                list, campPage.getNumber(), campPage.getSize(), campPage.getTotalElements(), campPage.getTotalPages(), campPage.isLast()));
    }

    /**
     * 3. Get specific campaign details.
     */
    @GetMapping("/campaigns/{campaignId}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getCampaignById(@PathVariable Long campaignId) {
        return campaignRepository.findById(campaignId)
                .<ResponseEntity<?>>map(c -> {
                    List<LeadFollowUpRound> rounds = roundRepository.findByCampaignIdOrderByRoundNumberAsc(c.getId());
                    return ResponseEntity.ok(importService.mapCampaignToResponse(c, c.getBatch(), rounds));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Campaign not found with ID: " + campaignId));
    }

    /**
     * 4. Get all 5 rounds for a campaign.
     */
    @GetMapping("/campaigns/{campaignId}/rounds")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<FollowUpRoundResponse>> getRounds(@PathVariable Long campaignId) {
        List<LeadFollowUpRound> rounds = roundRepository.findByCampaignIdOrderByRoundNumberAsc(campaignId);
        List<FollowUpRoundResponse> responses = rounds.stream()
                .map(r -> {
                    List<FollowUpAttachmentResponse> atts = attachmentService.getAttachmentsForRound(campaignId, r.getRoundNumber());
                    return FollowUpRoundResponse.builder()
                            .id(r.getId())
                            .campaignId(campaignId)
                            .roundNumber(r.getRoundNumber())
                            .parentRoundId(r.getParentRound() != null ? r.getParentRound().getId() : null)
                            .channel(r.getChannel())
                            .subject(r.getSubject())
                            .messageBody(r.getMessageBody())
                            .status(r.getStatus())
                            .selectedLeadCount(r.getSelectedLeadCount())
                            .sentCount(r.getSentCount())
                            .deliveredCount(r.getDeliveredCount())
                            .failedCount(r.getFailedCount())
                            .startedAt(r.getStartedAt())
                            .completedAt(r.getCompletedAt())
                            .lastTriggeredByEmail(r.getLastTriggeredByEmail())
                            .attachments(atts)
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 5. Get eligible leads for a round (supports search, filter, sorting, pagination).
     * Strictly queries immediate parent round selections (or batch for Round 1).
     */
    @GetMapping("/campaigns/{campaignId}/rounds/{roundNumber}/eligible-leads")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getEligibleLeads(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        try {
            PagedResponse<FollowUpEligibleLeadDTO> response = selectionService.getEligibleLeads(
                    campaignId, roundNumber, search, status, city, state, page, size, sortBy, direction);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 6. Select leads for a round with strict parent-round cascading lineage validation.
     */
    @PostMapping("/campaigns/{campaignId}/rounds/{roundNumber}/select-leads")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> selectLeadsForRound(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber,
            @RequestBody FollowUpSelectLeadsDTO dto) {
        try {
            return ResponseEntity.ok(selectionService.selectLeadsForRound(
                    campaignId, roundNumber, dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 7. Save/update round subject and message body template.
     */
    @PutMapping("/campaigns/{campaignId}/rounds/{roundNumber}/message")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateRoundMessage(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber,
            @RequestBody FollowUpMessageConfigDTO dto) {
        try {
            LeadFollowUpRound updated = dispatchService.saveMessageConfig(
                    campaignId, roundNumber, dto, getCurrentUserEmail());
            return ResponseEntity.ok(FollowUpRoundResponse.builder()
                    .id(updated.getId())
                    .campaignId(campaignId)
                    .roundNumber(updated.getRoundNumber())
                    .channel(updated.getChannel())
                    .subject(updated.getSubject())
                    .messageBody(updated.getMessageBody())
                    .status(updated.getStatus())
                    .selectedLeadCount(updated.getSelectedLeadCount())
                    .build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 8. Upload multi-part attachments for a round.
     */
    @PostMapping(value = "/campaigns/{campaignId}/rounds/{roundNumber}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> uploadRoundAttachments(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            List<FollowUpAttachmentResponse> responses = attachmentService.storeAttachments(
                    campaignId, roundNumber, files, getCurrentUserEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(responses);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 9. List attachments for a round.
     */
    @GetMapping("/campaigns/{campaignId}/rounds/{roundNumber}/attachments")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<FollowUpAttachmentResponse>> getRoundAttachments(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber) {
        return ResponseEntity.ok(attachmentService.getAttachmentsForRound(campaignId, roundNumber));
    }

    /**
     * 10. Delete an attachment from a round.
     */
    @DeleteMapping("/campaigns/{campaignId}/rounds/{roundNumber}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> deleteRoundAttachment(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber,
            @PathVariable Long attachmentId) {
        try {
            attachmentService.deleteAttachment(campaignId, roundNumber, attachmentId, getCurrentUserEmail());
            return ResponseEntity.ok("Attachment deleted successfully");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 11. Execute message dispatch for the round.
     */
    @PostMapping("/campaigns/{campaignId}/rounds/{roundNumber}/send")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> triggerRoundSend(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber) {
        try {
            FollowUpDispatchResultDTO result = dispatchService.dispatchRound(
                    campaignId, roundNumber, getCurrentUserEmail());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Round dispatch failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Round dispatch failed: " + e.getMessage());
        }
    }

    /**
     * 12. Safe retry for failed messages in the round without duplicate sends.
     */
    @PostMapping("/campaigns/{campaignId}/rounds/{roundNumber}/retry")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> retryFailedMessages(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber,
            @RequestBody(required = false) FollowUpRetryDTO dto) {
        try {
            List<Long> msgIds = dto != null ? dto.getMessageIds() : null;
            FollowUpDispatchResultDTO result = dispatchService.retryFailedMessages(
                    campaignId, roundNumber, msgIds, getCurrentUserEmail());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 13. Get message delivery audit history for the round.
     */
    @GetMapping("/campaigns/{campaignId}/rounds/{roundNumber}/history")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<PagedResponse<FollowUpMessageHistoryResponse>> getRoundHistory(
            @PathVariable Long campaignId,
            @PathVariable Integer roundNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(dispatchService.getRoundMessageHistory(campaignId, roundNumber, page, size));
    }

    /**
     * 14. Get overall campaign waterfall progress report (500 -> 300 -> 250 -> 100 -> 10).
     */
    @GetMapping("/campaigns/{campaignId}/progress")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getCampaignProgress(@PathVariable Long campaignId) {
        try {
            return ResponseEntity.ok(dispatchService.getCampaignProgress(campaignId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    /**
     * 15. Secure private attachment download stream.
     */
    @GetMapping("/attachments/{attachmentId}/download")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        try {
            Resource resource = attachmentService.getAttachmentResource(attachmentId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}

