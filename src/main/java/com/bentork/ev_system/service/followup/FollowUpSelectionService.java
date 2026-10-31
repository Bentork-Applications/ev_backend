package com.bentork.ev_system.service.followup;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.dto.followup.FollowUpEligibleLeadDTO;
import com.bentork.ev_system.dto.followup.FollowUpSelectLeadsDTO;
import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.model.followup.LeadFollowUpRoundLead;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpCampaignRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundLeadRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;
import com.bentork.ev_system.repository.followup.LeadImportBatchLeadRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles cascading lead queries, filtering, sorting, pagination, and persistent selection
 * across sequential campaign rounds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpSelectionService {

    private final LeadFollowUpCampaignRepository campaignRepository;
    private final LeadFollowUpRoundRepository roundRepository;
    private final LeadFollowUpRoundLeadRepository roundLeadRepository;
    private final LeadImportBatchLeadRepository batchLeadRepository;
    private final LeadRepository leadRepository;
    private final FollowUpRoundValidationService validationService;

    /**
     * Retrieves eligible leads for a specific round with search, filtering, sorting, and pagination.
     * Only leads from the immediately preceding round (or batch for Round 1) are returned.
     */
    @Transactional(readOnly = true)
    public PagedResponse<FollowUpEligibleLeadDTO> getEligibleLeads(
            Long campaignId,
            Integer roundNumber,
            String search,
            String status,
            String city,
            String state,
            int page,
            int size,
            String sortBy,
            String direction) {

        LeadFollowUpCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Round %d not found for Campaign %d", roundNumber, campaignId)));

        // Check sequential access (parent round must be completed)
        validationService.validateSequentialAccess(round);

        Sort.Direction dir = "DESC".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String sortProperty = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy.trim() : "id";
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortProperty));

        Page<Lead> leadsPage;

        if (roundNumber == 1) {
            // Round 1: Pool is all leads from the batch
            leadsPage = batchLeadRepository.findLeadsByBatchIdWithFilter(
                    campaign.getBatch().getId(), search, status, city, state, pageable);
        } else {
            // Round 2 to 5: Pool is strictly leads selected in the parent round
            LeadFollowUpRound parentRound = round.getParentRound();
            if (parentRound == null) {
                throw new IllegalStateException("Parent round is missing for Round " + roundNumber);
            }
            leadsPage = roundLeadRepository.findSelectedLeadsFromParentRoundWithFilter(
                    parentRound.getId(), search, status, city, state, pageable);
        }

        // Fetch current round selections to flag 'selectedInCurrentRound'
        List<Long> currentRoundSelectedIds = roundLeadRepository.findSelectedLeadIdsByRoundId(round.getId());
        Set<Long> currentSelectedSet = new HashSet<>(currentRoundSelectedIds);

        // Fetch parent round delivery statuses if roundNumber > 1
        Map<Long, String> parentDeliveryStatusMap = Collections.emptyMap();
        if (roundNumber > 1 && round.getParentRound() != null) {
            List<LeadFollowUpRoundLead> parentRoundLeads = roundLeadRepository.findByRoundId(round.getParentRound().getId());
            parentDeliveryStatusMap = parentRoundLeads.stream()
                    .collect(Collectors.toMap(rl -> rl.getLead().getId(), LeadFollowUpRoundLead::getDeliveryStatus, (s1, s2) -> s1));
        }

        Map<Long, String> finalParentDeliveryMap = parentDeliveryStatusMap;
        List<FollowUpEligibleLeadDTO> content = leadsPage.getContent().stream()
                .map(lead -> mapToEligibleDTO(lead, currentSelectedSet.contains(lead.getId()),
                        roundNumber > 1, finalParentDeliveryMap.get(lead.getId())))
                .collect(Collectors.toList());

        return PagedResponse.of(
                content,
                leadsPage.getNumber(),
                leadsPage.getSize(),
                leadsPage.getTotalElements(),
                leadsPage.getTotalPages(),
                leadsPage.isLast()
        );
    }

    /**
     * Persists selected leads for a round, strictly enforcing parent-round cascading lineage.
     */
    @Transactional
    public Map<String, Object> selectLeadsForRound(
            Long campaignId,
            Integer roundNumber,
            FollowUpSelectLeadsDTO dto,
            String adminEmail) {

        LeadFollowUpCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Round %d not found for Campaign %d", roundNumber, campaignId)));

        validationService.validateSequentialAccess(round);

        if ("IN_PROGRESS".equalsIgnoreCase(round.getStatus()) || "COMPLETED".equalsIgnoreCase(round.getStatus())) {
            throw new IllegalStateException(String.format(
                    "Cannot modify lead selection for Round %d because it is %s", roundNumber, round.getStatus()));
        }

        List<Long> candidateLeadIds;

        if (Boolean.TRUE.equals(dto.getSelectAllEligible())) {
            // Select all leads from the parent pool
            if (roundNumber == 1) {
                candidateLeadIds = batchLeadRepository.findLeadIdsByBatchId(campaign.getBatch().getId());
            } else {
                candidateLeadIds = roundLeadRepository.findSelectedLeadIdsByRoundId(round.getParentRound().getId());
            }
        } else {
            candidateLeadIds = dto.getLeadIds() != null ? dto.getLeadIds() : new ArrayList<>();
        }

        if (candidateLeadIds.isEmpty()) {
            throw new IllegalArgumentException("At least one eligible lead must be selected for the round.");
        }

        // Enforce strict parent-round lineage validation
        validationService.validateLeadLineage(campaign, round, candidateLeadIds);

        // Clear existing draft selections for this round to permit re-selection
        roundLeadRepository.deleteByRoundId(round.getId());

        List<Lead> selectedLeads = leadRepository.findAllById(candidateLeadIds);
        List<LeadFollowUpRoundLead> roundLeads = new ArrayList<>();

        for (Lead lead : selectedLeads) {
            LeadFollowUpRoundLead rl = LeadFollowUpRoundLead.builder()
                    .round(round)
                    .campaign(campaign)
                    .lead(lead)
                    .selectionStatus("SELECTED")
                    .deliveryStatus("PENDING")
                    .selectedAt(LocalDateTime.now())
                    .selectedByEmail(adminEmail)
                    .build();
            roundLeads.add(rl);
        }

        roundLeadRepository.saveAll(roundLeads);

        // Update round metadata
        round.setSelectedLeadCount(roundLeads.size());
        if (round.getMessageBody() != null && !round.getMessageBody().trim().isEmpty()) {
            round.setStatus("CONFIGURED");
        }
        roundRepository.save(round);

        // Update campaign status if draft
        if ("DRAFT".equalsIgnoreCase(campaign.getStatus())) {
            campaign.setStatus("ACTIVE");
            campaignRepository.save(campaign);
        }

        log.info("Selected {} leads for Campaign {} Round {} by {}",
                roundLeads.size(), campaignId, roundNumber, adminEmail);

        return Map.of(
                "campaignId", campaignId,
                "roundNumber", roundNumber,
                "selectedCount", roundLeads.size(),
                "status", round.getStatus(),
                "message", String.format("Successfully selected %d leads for Round %d", roundLeads.size(), roundNumber)
        );
    }

    private FollowUpEligibleLeadDTO mapToEligibleDTO(
            Lead lead, boolean selectedInCurrent, boolean hasParent, String previousDeliveryStatus) {

        String contactName = lead.getContact() != null ? lead.getContact().getName() : null;
        String companyName = lead.getCompany() != null ? lead.getCompany().getName() : null;
        String phone = (lead.getPhone() != null && !lead.getPhone().isEmpty()) ? lead.getPhone() :
                (lead.getContact() != null ? lead.getContact().getPhone() : null);
        String email = (lead.getEmail() != null && !lead.getEmail().isEmpty()) ? lead.getEmail() :
                (lead.getContact() != null ? lead.getContact().getEmail() : null);

        return FollowUpEligibleLeadDTO.builder()
                .leadId(lead.getId())
                .leadNumber(lead.getLeadNumber())
                .title(lead.getTitle())
                .name(contactName != null ? contactName : lead.getTitle())
                .phone(phone)
                .email(email)
                .company(companyName)
                .city(lead.getCity())
                .state(lead.getState())
                .leadStatus(lead.getStatus())
                .estimatedValue(lead.getEstimatedValue())
                .followUpDate(lead.getNextFollowUpDate())
                .assignedUserId(lead.getOwnerAdmin() != null ? lead.getOwnerAdmin().getId() : null)
                .assignedUserName(lead.getOwnerAdmin() != null ? lead.getOwnerAdmin().getName() : null)
                .selectedInCurrentRound(selectedInCurrent)
                .selectedInParentRound(hasParent)
                .previousRoundDeliveryStatus(previousDeliveryStatus)
                .createdAt(lead.getCreatedAt())
                .build();
    }
}

