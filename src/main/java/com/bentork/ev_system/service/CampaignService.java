package com.bentork.ev_system.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.dto.request.AddRecipientsDTO;
import com.bentork.ev_system.dto.request.AudienceFilterDTO;
import com.bentork.ev_system.dto.request.CampaignRoundDTO;
import com.bentork.ev_system.dto.request.CreateCampaignDTO;
import com.bentork.ev_system.dto.request.UpdateCampaignDTO;
import com.bentork.ev_system.dto.response.AudienceSegmentResponse;
import com.bentork.ev_system.dto.response.CampaignDispatchResultDTO;
import com.bentork.ev_system.dto.response.CampaignRecipientResponse;
import com.bentork.ev_system.dto.response.CampaignResponse;
import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.enums.CampaignAuditAction;
import com.bentork.ev_system.enums.CampaignStatus;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.AudienceSegment;
import com.bentork.ev_system.model.Campaign;
import com.bentork.ev_system.model.CampaignRecipient;
import com.bentork.ev_system.model.CampaignRound;
import com.bentork.ev_system.model.SalesCompany;
import com.bentork.ev_system.model.SalesContact;
import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.AudienceSegmentRepository;
import com.bentork.ev_system.repository.CampaignRecipientRepository;
import com.bentork.ev_system.repository.CampaignRepository;
import com.bentork.ev_system.repository.CampaignRoundRepository;
import com.bentork.ev_system.repository.SalesCompanyRepository;
import com.bentork.ev_system.repository.SalesContactRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpCampaignRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final SalesCompanyRepository salesCompanyRepository;
    private final SalesContactRepository salesContactRepository;
    private final AudienceSegmentRepository audienceSegmentRepository;
    private final AudienceService audienceService;
    private final EmailDispatchService emailDispatchService;
    private final WhatsAppDispatchService whatsAppDispatchService;
    private final AdminRepository adminRepository;
    private final CampaignAuditService auditService;
    private final CampaignRoundRepository campaignRoundRepository;
    private final LeadFollowUpCampaignRepository leadFollowUpCampaignRepository;
    private final LeadFollowUpRoundRepository leadFollowUpRoundRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== CRUD & LIFECYCLE ====================

    @Transactional
    public CampaignResponse createCampaign(CreateCampaignDTO dto, String adminEmail) {
        Campaign campaign = new Campaign();
        campaign.setName(dto.getName());
        campaign.setType(dto.getType() != null ? dto.getType() : "OFFER");
        campaign.setChannel(dto.getChannel() != null ? dto.getChannel().toUpperCase().trim() : "EMAIL");
        campaign.setSubject(dto.getSubject());
        campaign.setMessageBody(dto.getMessageBody() != null ? dto.getMessageBody() : dto.getDescription());
        campaign.setDescription(dto.getDescription());
        campaign.setObjective(dto.getObjective());
        campaign.setScheduledDate(dto.getScheduledDate());
        campaign.setStartDate(dto.getStartDate());
        campaign.setEndDate(dto.getEndDate());
        if (dto.getTimezone() != null && !dto.getTimezone().trim().isEmpty()) {
            campaign.setTimezone(dto.getTimezone().trim());
        }
        if (dto.getSendMode() != null && !dto.getSendMode().trim().isEmpty()) {
            campaign.setSendMode(dto.getSendMode().toUpperCase().trim());
        }
        if (dto.getOwnerAdminId() != null) {
            adminRepository.findById(dto.getOwnerAdminId()).ifPresent(campaign::setOwnerAdmin);
        } else if (adminEmail != null) {
            adminRepository.findByEmail(adminEmail).ifPresent(campaign::setOwnerAdmin);
        }
        campaign.setTargetAudienceSegmentId(dto.getTargetAudienceSegmentId());
        campaign.setCreatedByAdminEmail(adminEmail);
        campaign.setStatus(dto.getScheduledDate() != null ? "SCHEDULED" : "DRAFT");

        Campaign saved = campaignRepository.save(campaign);

        // If target audience segment is specified, auto-populate recipients
        if (dto.getTargetAudienceSegmentId() != null) {
            try {
                addRecipientsFromSegment(saved.getId(), dto.getTargetAudienceSegmentId());
                saved = campaignRepository.findById(saved.getId()).orElse(saved);
            } catch (Exception e) {
                log.warn("Campaign {} created, but failed to auto-attach segment {}: {}",
                        saved.getId(), dto.getTargetAudienceSegmentId(), e.getMessage());
            }
        }

        try {
            auditService.logCampaign(saved.getId(), CampaignAuditAction.CAMPAIGN_CREATED.getValue(),
                    "Campaign created with name: " + saved.getName(), adminEmail);
        } catch (Exception e) {
            log.warn("Failed to log audit for campaign {}: {}", saved.getId(), e.getMessage());
        }

        log.info("Campaign '{}' (ID: {}) created by {} with channel {}", saved.getName(), saved.getId(), adminEmail, saved.getChannel());
        return mapToResponse(saved);
    }

    public List<CampaignResponse> getAllCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CampaignResponse getCampaignById(Long id) {
        Optional<Campaign> optCampaign = campaignRepository.findById(id);
        if (optCampaign.isPresent()) {
            return mapToResponse(optCampaign.get());
        }

        Optional<LeadFollowUpCampaign> optFollowUp = leadFollowUpCampaignRepository.findById(id);
        if (optFollowUp.isPresent()) {
            LeadFollowUpCampaign fup = optFollowUp.get();
            List<LeadFollowUpRound> rounds = leadFollowUpRoundRepository.findByCampaignIdOrderByRoundNumberAsc(id);
            List<CampaignRoundDTO> roundDtos = rounds.stream().map(r -> CampaignRoundDTO.builder()
                    .id(r.getId())
                    .roundNumber(r.getRoundNumber())
                    .channel(r.getChannel())
                    .subject(r.getSubject())
                    .messageBody(r.getMessageBody())
                    .plannedDate(r.getPlannedDate())
                    .plannedTime(r.getPlannedTime())
                    .timezone(r.getTimezone())
                    .sendMode(r.getSendMode())
                    .status(r.getStatus())
                    .build()).collect(Collectors.toList());

            return CampaignResponse.builder()
                    .id(fup.getId())
                    .name(fup.getName())
                    .type("FOLLOW_UP")
                    .channel(fup.getChannel())
                    .status(fup.getStatus())
                    .totalRounds(fup.getTotalRounds())
                    .completedRounds(fup.getCompletedRounds())
                    .ownerAdminId(fup.getOwnerAdmin() != null ? fup.getOwnerAdmin().getId() : null)
                    .ownerAdminName(fup.getOwnerAdmin() != null ? fup.getOwnerAdmin().getName() : null)
                    .ownerAdminEmail(fup.getOwnerAdmin() != null ? fup.getOwnerAdmin().getEmail() : null)
                    .rounds(roundDtos)
                    .createdAt(fup.getCreatedAt())
                    .updatedAt(fup.getUpdatedAt())
                    .build();
        }

        throw new IllegalArgumentException("Campaign not found with id: " + id);
    }

    @Transactional
    public CampaignResponse updateCampaignStatus(Long id, String status) {
        Optional<Campaign> opt = campaignRepository.findById(id);
        if (opt.isPresent()) {
            Campaign campaign = opt.get();
            campaign.setStatus(status.toUpperCase().trim());
            Campaign updated = campaignRepository.save(campaign);
            return mapToResponse(updated);
        }

        Optional<LeadFollowUpCampaign> optFup = leadFollowUpCampaignRepository.findById(id);
        if (optFup.isPresent()) {
            LeadFollowUpCampaign fup = optFup.get();
            fup.setStatus(status.toUpperCase().trim());
            leadFollowUpCampaignRepository.save(fup);
            return getCampaignById(id);
        }

        throw new IllegalArgumentException("Campaign not found with id: " + id);
    }

    @Transactional
    public CampaignResponse updateCampaign(Long id, UpdateCampaignDTO dto, String adminEmail) {
        Optional<Campaign> optCampaign = campaignRepository.findById(id);
        if (optCampaign.isPresent()) {
            Campaign campaign = optCampaign.get();

            if (CampaignStatus.COMPLETED.getValue().equalsIgnoreCase(campaign.getStatus())
                    || CampaignStatus.CANCELLED.getValue().equalsIgnoreCase(campaign.getStatus())) {
                throw new IllegalStateException("Cannot modify a " + campaign.getStatus() + " campaign");
            }

            if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
                campaign.setName(dto.getName().trim());
            }
            if (dto.getType() != null && !dto.getType().trim().isEmpty()) {
                campaign.setType(dto.getType().trim());
            }
            if (dto.getChannel() != null && !dto.getChannel().trim().isEmpty()) {
                campaign.setChannel(dto.getChannel().toUpperCase().trim());
            }
            if (dto.getSubject() != null) {
                campaign.setSubject(dto.getSubject());
            }
            if (dto.getMessageBody() != null) {
                campaign.setMessageBody(dto.getMessageBody());
            }
            if (dto.getDescription() != null) {
                campaign.setDescription(dto.getDescription());
            }
            if (dto.getObjective() != null) {
                campaign.setObjective(dto.getObjective());
            }
            if (dto.getScheduledDate() != null) {
                campaign.setScheduledDate(dto.getScheduledDate());
            }
            if (dto.getStartDate() != null) {
                campaign.setStartDate(dto.getStartDate());
            }
            if (dto.getEndDate() != null) {
                campaign.setEndDate(dto.getEndDate());
            }
            if (dto.getTimezone() != null && !dto.getTimezone().trim().isEmpty()) {
                campaign.setTimezone(dto.getTimezone().trim());
            }
            if (dto.getSendMode() != null && !dto.getSendMode().trim().isEmpty()) {
                campaign.setSendMode(dto.getSendMode().toUpperCase().trim());
            }
            if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
                campaign.setStatus(dto.getStatus().toUpperCase().trim());
            }
            if (dto.getOwnerAdminId() != null) {
                adminRepository.findById(dto.getOwnerAdminId()).ifPresent(campaign::setOwnerAdmin);
            }

            // If target audience segment changed, re-sync recipients
            if (dto.getTargetAudienceSegmentId() != null
                    && !dto.getTargetAudienceSegmentId().equals(campaign.getTargetAudienceSegmentId())) {
                campaign.setTargetAudienceSegmentId(dto.getTargetAudienceSegmentId());
                try {
                    addRecipientsFromSegment(campaign.getId(), dto.getTargetAudienceSegmentId());
                } catch (Exception e) {
                    log.warn("Campaign {} updated, but failed to auto-attach segment {}: {}",
                            campaign.getId(), dto.getTargetAudienceSegmentId(), e.getMessage());
                }
            }

            // Update or create campaign rounds if provided (e.g. 5 sequential rounds)
            if (dto.getRounds() != null && !dto.getRounds().isEmpty()) {
                for (CampaignRoundDTO rDto : dto.getRounds()) {
                    if (rDto.getRoundNumber() == null) continue;
                    CampaignRound round = campaignRoundRepository
                            .findByCampaignIdAndRoundNumber(campaign.getId(), rDto.getRoundNumber())
                            .orElseGet(() -> {
                                CampaignRound nr = new CampaignRound();
                                nr.setCampaign(campaign);
                                nr.setRoundNumber(rDto.getRoundNumber());
                                return nr;
                            });

                    if (rDto.getTemplateName() != null && !rDto.getTemplateName().trim().isEmpty()) {
                        round.setTemplateName(rDto.getTemplateName().trim());
                    } else if (round.getTemplateName() == null) {
                        round.setTemplateName(rDto.getSubject() != null ? rDto.getSubject() : "Round " + rDto.getRoundNumber());
                    }
                    if (rDto.getMessageBody() != null) {
                        round.setTemplateBody(rDto.getMessageBody());
                    } else if (rDto.getTemplateBody() != null) {
                        round.setTemplateBody(rDto.getTemplateBody());
                    }
                    if (rDto.getTemplateId() != null) {
                        round.setTemplateId(rDto.getTemplateId());
                    }
                    if (rDto.getTemplateStatus() != null) {
                        round.setTemplateStatus(rDto.getTemplateStatus());
                    }
                    if (rDto.getPlannedDate() != null) {
                        round.setPlannedDate(rDto.getPlannedDate());
                    }
                    if (rDto.getPlannedTime() != null) {
                        round.setPlannedTime(rDto.getPlannedTime());
                    }
                    if (rDto.getTimezone() != null) {
                        round.setTimezone(rDto.getTimezone());
                    }
                    if (rDto.getSendMode() != null) {
                        round.setSendMode(rDto.getSendMode().toUpperCase().trim());
                    }
                    if (rDto.getStatus() != null) {
                        round.setStatus(rDto.getStatus().toUpperCase().trim());
                    }
                    campaignRoundRepository.save(round);
                }
                campaign.setTotalRounds(dto.getRounds().size());
            }

            Campaign updated = campaignRepository.save(campaign);

            try {
                auditService.logCampaign(id, CampaignAuditAction.CAMPAIGN_UPDATED.getValue(),
                        "Campaign details updated via PUT /api/crm/campaigns/" + id, adminEmail);
            } catch (Exception e) {
                log.warn("Failed to log audit for campaign {}: {}", id, e.getMessage());
            }

            log.info("Campaign '{}' (ID: {}) updated by {}", updated.getName(), updated.getId(), adminEmail);
            return mapToResponse(updated);
        }

        // Check if ID belongs to LeadFollowUpCampaign (the 5 rounds of sequential follow-ups)
        Optional<LeadFollowUpCampaign> optFollowUp = leadFollowUpCampaignRepository.findById(id);
        if (optFollowUp.isPresent()) {
            LeadFollowUpCampaign fup = optFollowUp.get();

            if ("COMPLETED".equalsIgnoreCase(fup.getStatus()) || "CANCELLED".equalsIgnoreCase(fup.getStatus())) {
                throw new IllegalStateException("Cannot modify a " + fup.getStatus() + " campaign");
            }

            if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
                fup.setName(dto.getName().trim());
            }
            if (dto.getChannel() != null && !dto.getChannel().trim().isEmpty()) {
                fup.setChannel(dto.getChannel().toUpperCase().trim());
            }
            if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
                fup.setStatus(dto.getStatus().toUpperCase().trim());
            }
            if (dto.getOwnerAdminId() != null) {
                adminRepository.findById(dto.getOwnerAdminId()).ifPresent(fup::setOwnerAdmin);
            }

            // Update or save all 5 rounds of sequential follow-ups
            if (dto.getRounds() != null && !dto.getRounds().isEmpty()) {
                for (CampaignRoundDTO rDto : dto.getRounds()) {
                    if (rDto.getRoundNumber() == null) continue;
                    LeadFollowUpRound round = leadFollowUpRoundRepository
                            .findByCampaignIdAndRoundNumber(fup.getId(), rDto.getRoundNumber())
                            .orElseGet(() -> {
                                LeadFollowUpRound nr = new LeadFollowUpRound();
                                nr.setCampaign(fup);
                                nr.setRoundNumber(rDto.getRoundNumber());
                                return nr;
                            });

                    if (rDto.getChannel() != null) {
                        round.setChannel(rDto.getChannel().toUpperCase().trim());
                    }
                    if (rDto.getSubject() != null) {
                        round.setSubject(rDto.getSubject());
                    }
                    if (rDto.getMessageBody() != null) {
                        round.setMessageBody(rDto.getMessageBody());
                    } else if (rDto.getTemplateBody() != null) {
                        round.setMessageBody(rDto.getTemplateBody());
                    }
                    if (rDto.getPlannedDate() != null) {
                        round.setPlannedDate(rDto.getPlannedDate());
                    }
                    if (rDto.getPlannedTime() != null) {
                        round.setPlannedTime(rDto.getPlannedTime());
                    }
                    if (rDto.getTimezone() != null) {
                        round.setTimezone(rDto.getTimezone());
                    }
                    if (rDto.getSendMode() != null) {
                        round.setSendMode(rDto.getSendMode().toUpperCase().trim());
                    }
                    if (rDto.getStatus() != null) {
                        round.setStatus(rDto.getStatus().toUpperCase().trim());
                    }
                    leadFollowUpRoundRepository.save(round);
                }
                fup.setTotalRounds(dto.getRounds().size());
            }

            LeadFollowUpCampaign updatedFup = leadFollowUpCampaignRepository.save(fup);
            log.info("Lead follow-up campaign '{}' (ID: {}) updated by {}", updatedFup.getName(), updatedFup.getId(), adminEmail);

            List<LeadFollowUpRound> rounds = leadFollowUpRoundRepository.findByCampaignIdOrderByRoundNumberAsc(updatedFup.getId());
            List<CampaignRoundDTO> roundDtos = rounds.stream().map(r -> CampaignRoundDTO.builder()
                    .id(r.getId())
                    .roundNumber(r.getRoundNumber())
                    .channel(r.getChannel())
                    .subject(r.getSubject())
                    .messageBody(r.getMessageBody())
                    .plannedDate(r.getPlannedDate())
                    .plannedTime(r.getPlannedTime())
                    .timezone(r.getTimezone())
                    .sendMode(r.getSendMode())
                    .status(r.getStatus())
                    .build()).collect(Collectors.toList());

            return CampaignResponse.builder()
                    .id(updatedFup.getId())
                    .name(updatedFup.getName())
                    .type("FOLLOW_UP")
                    .channel(updatedFup.getChannel())
                    .status(updatedFup.getStatus())
                    .totalRounds(updatedFup.getTotalRounds())
                    .completedRounds(updatedFup.getCompletedRounds())
                    .ownerAdminId(updatedFup.getOwnerAdmin() != null ? updatedFup.getOwnerAdmin().getId() : null)
                    .ownerAdminName(updatedFup.getOwnerAdmin() != null ? updatedFup.getOwnerAdmin().getName() : null)
                    .ownerAdminEmail(updatedFup.getOwnerAdmin() != null ? updatedFup.getOwnerAdmin().getEmail() : null)
                    .rounds(roundDtos)
                    .createdAt(updatedFup.getCreatedAt())
                    .updatedAt(updatedFup.getUpdatedAt())
                    .build();
        }

        throw new IllegalArgumentException("Campaign not found with id: " + id);
    }

    // ==================== RECIPIENT MANAGEMENT ====================

    @Transactional
    public CampaignResponse addRecipients(Long campaignId, AddRecipientsDTO dto) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        if (dto.getCompanyIds() != null && !dto.getCompanyIds().isEmpty()) {
            List<SalesCompany> companies = salesCompanyRepository.findAllById(dto.getCompanyIds());
            int addedCount = 0;

            for (SalesCompany company : companies) {
                if (campaignRecipientRepository.existsByCampaignIdAndCompanyId(campaign.getId(), company.getId())) {
                    continue; // Skip duplicate
                }

                // Resolve primary contact
                SalesContact contact = resolvePrimaryContact(company);

                CampaignRecipient recipient = new CampaignRecipient();
                recipient.setCampaign(campaign);
                recipient.setCompany(company);
                recipient.setContact(contact);
                recipient.setRecipientEmail(contact != null && contact.getEmail() != null ? contact.getEmail() : company.getEmail());
                recipient.setRecipientPhone(contact != null && contact.getPhone() != null ? contact.getPhone() : company.getPhone());
                recipient.setDeliveryStatus("PENDING");

                campaignRecipientRepository.save(recipient);
                addedCount++;
            }

            campaign.setTotalRecipients(campaign.getTotalRecipients() + addedCount);
            campaign = campaignRepository.save(campaign);
            log.info("Added {} recipients to Campaign {}", addedCount, campaignId);
        }

        return mapToResponse(campaign);
    }

    /**
     * Attach all matching companies from a saved Audience Segment to this campaign.
     */
    @Transactional
    public CampaignResponse addRecipientsFromSegment(Long campaignId, Long segmentId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        AudienceSegment segment = audienceSegmentRepository.findById(segmentId)
                .orElseThrow(() -> new IllegalArgumentException("Audience segment not found with id: " + segmentId));

        try {
            AudienceFilterDTO filter = objectMapper.readValue(segment.getFiltersJson(), AudienceFilterDTO.class);
            AudienceSegmentResponse audienceResult = audienceService.buildAudience(filter);

            List<Long> companyIds = audienceResult.getMatchedCompanyIds();
            if (companyIds != null && !companyIds.isEmpty()) {
                AddRecipientsDTO dto = new AddRecipientsDTO();
                dto.setCompanyIds(companyIds);
                addRecipients(campaignId, dto);
            }

            campaign.setTargetAudienceSegmentId(segmentId);
            campaign = campaignRepository.save(campaign);
            log.info("Attached audience segment '{}' to Campaign {}, resolving {} companies",
                    segment.getName(), campaignId, companyIds != null ? companyIds.size() : 0);

            return mapToResponse(campaign);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to attach audience segment: " + e.getMessage(), e);
        }
    }

    /**
     * Get paginated list of recipients and their delivery statuses for a campaign.
     */
    public PagedResponse<CampaignRecipientResponse> getCampaignRecipients(Long campaignId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CampaignRecipient> recipientPage = campaignRecipientRepository.findByCampaignId(campaignId, pageable);

        List<CampaignRecipientResponse> content = recipientPage.getContent().stream()
                .map(this::mapRecipientToResponse)
                .collect(Collectors.toList());

        return PagedResponse.of(content, page, size, recipientPage.getTotalElements(), recipientPage.getTotalPages(), recipientPage.isLast());
    }

    // ==================== DISPATCH ENGINE ====================

    /**
     * Execute campaign dispatch to all pending recipients across configured channels (EMAIL, WHATSAPP, or BOTH).
     */
    @Transactional
    public CampaignDispatchResultDTO sendCampaign(Long campaignId, String adminEmail) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        List<CampaignRecipient> recipients = campaignRecipientRepository.findByCampaignId(campaignId);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("Campaign has no recipients. Add recipients or an audience segment first.");
        }

        String channel = campaign.getChannel() != null ? campaign.getChannel().toUpperCase().trim() : "EMAIL";
        boolean sendEmail = "EMAIL".equals(channel) || "BOTH".equals(channel);
        boolean sendWhatsApp = "WHATSAPP".equals(channel) || "BOTH".equals(channel);

        CampaignDispatchResultDTO result = new CampaignDispatchResultDTO();
        result.setCampaignId(campaign.getId());
        result.setCampaignName(campaign.getName());
        result.setChannel(channel);
        result.setTotalRecipients(recipients.size());
        result.setDispatchedAt(LocalDateTime.now());

        int emailsSent = 0;
        int whatsAppSent = 0;
        int failedCount = 0;

        for (CampaignRecipient recipient : recipients) {
            // Skip already sent recipients
            if (recipient.isSent() && "SENT".equalsIgnoreCase(recipient.getDeliveryStatus())) {
                continue;
            }

            SalesCompany company = recipient.getCompany();
            SalesContact contact = recipient.getContact();

            String contactName = contact != null ? contact.getName() : (company != null ? company.getName() : "Customer");
            String companyName = company != null ? company.getName() : "";
            String email = recipient.getRecipientEmail() != null ? recipient.getRecipientEmail() :
                    (contact != null && contact.getEmail() != null ? contact.getEmail() : (company != null ? company.getEmail() : null));
            String phone = recipient.getRecipientPhone() != null ? recipient.getRecipientPhone() :
                    (contact != null && contact.getPhone() != null ? contact.getPhone() : (company != null ? company.getPhone() : null));

            boolean emailSuccess = false;
            boolean whatsAppSuccess = false;
            List<String> errors = new ArrayList<>();

            // 1. Dispatch Email
            if (sendEmail) {
                if (email != null && !email.trim().isEmpty()) {
                    try {
                        emailDispatchService.sendCampaignEmail(
                                email.trim(),
                                campaign.getSubject(),
                                campaign.getMessageBody(),
                                contactName,
                                companyName);
                        emailSuccess = true;
                        emailsSent++;
                    } catch (Exception e) {
                        errors.add("Email error: " + e.getMessage());
                    }
                } else {
                    errors.add("Email skipped: No email address found");
                }
            }

            // 2. Dispatch WhatsApp
            if (sendWhatsApp) {
                if (phone != null && !phone.trim().isEmpty()) {
                    try {
                        whatsAppDispatchService.sendWhatsAppMessage(
                                phone.trim(),
                                campaign.getMessageBody(),
                                contactName,
                                companyName);
                        whatsAppSuccess = true;
                        whatsAppSent++;
                    } catch (Exception e) {
                        errors.add("WhatsApp error: " + e.getMessage());
                    }
                } else {
                    errors.add("WhatsApp skipped: No phone number found");
                }
            }

            // 3. Update recipient status
            boolean overallSuccess = (sendEmail && emailSuccess) || (sendWhatsApp && whatsAppSuccess);

            if (overallSuccess) {
                recipient.setSent(true);
                recipient.setDeliveryStatus("SENT");
                recipient.setChannelUsed(channel);
                recipient.setSentAt(LocalDateTime.now());
                recipient.setErrorMessage(null);
            } else {
                recipient.setSent(false);
                recipient.setDeliveryStatus("FAILED");
                recipient.setErrorMessage(String.join("; ", errors));
                failedCount++;
                result.getErrors().add("Recipient ID " + recipient.getId() + " (" + contactName + "): " + recipient.getErrorMessage());
            }

            campaignRecipientRepository.save(recipient);
        }

        // 4. Update campaign aggregates
        long totalSent = campaignRecipientRepository.countByCampaignIdAndSentTrue(campaignId);
        campaign.setSentCount((int) totalSent);
        campaign.setStatus("COMPLETED");
        campaignRepository.save(campaign);

        result.setEmailsSent(emailsSent);
        result.setWhatsAppSent(whatsAppSent);
        result.setFailedCount(failedCount);
        result.setStatus(campaign.getStatus());

        log.info("Campaign {} dispatch complete by {}: {} emails, {} WhatsApp sent, {} failed out of {}",
                campaign.getName(), adminEmail, emailsSent, whatsAppSent, failedCount, recipients.size());

        return result;
    }

    // ==================== HELPERS ====================

    private SalesContact resolvePrimaryContact(SalesCompany company) {
        if (company == null) return null;
        List<SalesContact> contacts = salesContactRepository.findByCompanyIdOrderByIsPrimaryDescNameAsc(company.getId());
        return !contacts.isEmpty() ? contacts.get(0) : null;
    }

    public CampaignResponse mapToResponse(Campaign campaign) {
        CampaignResponse response = new CampaignResponse();
        response.setId(campaign.getId());
        response.setName(campaign.getName());
        response.setType(campaign.getType());
        response.setChannel(campaign.getChannel());
        response.setSubject(campaign.getSubject());
        response.setMessageBody(campaign.getMessageBody());
        response.setTargetAudienceSegmentId(campaign.getTargetAudienceSegmentId());
        response.setDescription(campaign.getDescription());
        response.setObjective(campaign.getObjective());
        response.setScheduledDate(campaign.getScheduledDate());
        response.setStartDate(campaign.getStartDate());
        response.setEndDate(campaign.getEndDate());
        response.setTimezone(campaign.getTimezone());
        response.setSendMode(campaign.getSendMode());
        response.setStatus(campaign.getStatus());

        if (campaign.getOwnerAdmin() != null) {
            response.setOwnerAdminId(campaign.getOwnerAdmin().getId());
            response.setOwnerAdminName(campaign.getOwnerAdmin().getName());
            response.setOwnerAdminEmail(campaign.getOwnerAdmin().getEmail());
        }

        response.setTotalRounds(campaign.getTotalRounds());
        response.setCompletedRounds(campaign.getCompletedRounds());
        response.setTotalRecipients(campaign.getTotalRecipients());
        response.setSentCount(campaign.getSentCount());
        response.setDeliveredCount(campaign.getDeliveredCount());
        response.setReadCount(campaign.getReadCount());
        response.setOpenedCount(campaign.getOpenedCount());
        response.setRespondedCount(campaign.getRespondedCount());
        response.setFailedCount(campaign.getFailedCount());

        response.setPauseReason(campaign.getPauseReason());
        response.setPausedAt(campaign.getPausedAt());
        response.setPausedByAdminEmail(campaign.getPausedByAdminEmail());

        response.setCreatedByAdminEmail(campaign.getCreatedByAdminEmail());
        response.setCreatedAt(campaign.getCreatedAt());
        response.setUpdatedAt(campaign.getUpdatedAt());

        try {
            List<CampaignRound> rounds = campaignRoundRepository.findByCampaignIdOrderByRoundNumberAsc(campaign.getId());
            if (rounds != null && !rounds.isEmpty()) {
                response.setRounds(rounds.stream().map(this::mapRoundToDto).collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.debug("Could not attach rounds to campaign response: {}", e.getMessage());
        }

        return response;
    }

    private CampaignRoundDTO mapRoundToDto(CampaignRound r) {
        return CampaignRoundDTO.builder()
                .id(r.getId())
                .roundNumber(r.getRoundNumber())
                .channel(r.getCampaign() != null ? r.getCampaign().getChannel() : "WHATSAPP")
                .subject(r.getTemplateName())
                .messageBody(r.getTemplateBody())
                .templateName(r.getTemplateName())
                .templateBody(r.getTemplateBody())
                .templateId(r.getTemplateId())
                .templateStatus(r.getTemplateStatus())
                .plannedDate(r.getPlannedDate())
                .plannedTime(r.getPlannedTime())
                .timezone(r.getTimezone())
                .sendMode(r.getSendMode())
                .status(r.getStatus())
                .build();
    }

    public CampaignRecipientResponse mapRecipientToResponse(CampaignRecipient r) {
        CampaignRecipientResponse res = new CampaignRecipientResponse();
        res.setId(r.getId());
        res.setCampaignId(r.getCampaign() != null ? r.getCampaign().getId() : null);
        if (r.getCompany() != null) {
            res.setCompanyId(r.getCompany().getId());
            res.setCompanyName(r.getCompany().getName());
        }
        if (r.getContact() != null) {
            res.setContactId(r.getContact().getId());
            res.setContactName(r.getContact().getName());
        }
        res.setRecipientEmail(r.getRecipientEmail());
        res.setRecipientPhone(r.getRecipientPhone());
        res.setChannelUsed(r.getChannelUsed());
        res.setDeliveryStatus(r.getDeliveryStatus());
        res.setSent(r.isSent());
        res.setErrorMessage(r.getErrorMessage());
        res.setSentAt(r.getSentAt());
        res.setOpened(r.isOpened());
        res.setResponded(r.isResponded());
        res.setCreatedAt(r.getCreatedAt());
        return res;
    }
}
