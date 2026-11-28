package com.bentork.ev_system.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.CreateOpportunityDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.request.UpdateOpportunityStageDTO;
import com.bentork.ev_system.dto.response.OpportunityResponse;
import com.bentork.ev_system.enums.LeadStatus;
import com.bentork.ev_system.enums.OpportunityStage;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.Opportunity;
import com.bentork.ev_system.model.SalesCompany;
import com.bentork.ev_system.model.SalesContact;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.OpportunityRepository;
import com.bentork.ev_system.repository.SalesCompanyRepository;
import com.bentork.ev_system.repository.SalesContactRepository;
import com.bentork.ev_system.service.interfaces.IAdminNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;
    private final LeadRepository leadRepository;
    private final SalesCompanyRepository companyRepository;
    private final SalesContactRepository contactRepository;
    private final AdminRepository adminRepository;
    private final IAdminNotificationService adminNotificationService;

    @Transactional
    public OpportunityResponse createOpportunity(CreateOpportunityDTO dto, String adminEmail) {
        Admin owner = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        SalesCompany company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        Opportunity opp = new Opportunity();
        opp.setOpportunityNumber(generateOpportunityNumber());
        opp.setTitle(dto.getTitle());
        opp.setCompany(company);
        opp.setOwnerAdmin(owner);
        opp.setStage(OpportunityStage.QUALIFIED.getValue());
        opp.setValue(dto.getValue());
        opp.setProbability(OpportunityStage.QUALIFIED.getProbability());
        opp.setNotes(dto.getNotes());

        if (dto.getExpectedCloseDate() != null && !dto.getExpectedCloseDate().isEmpty()) {
            opp.setExpectedCloseDate(LocalDate.parse(dto.getExpectedCloseDate()));
        }
        if (dto.getContactId() != null) {
            opp.setContact(contactRepository.findById(dto.getContactId())
                    .orElseThrow(() -> new IllegalArgumentException("Contact not found")));
        }
        if (dto.getLeadId() != null) {
            Lead lead = leadRepository.findById(dto.getLeadId())
                    .orElseThrow(() -> new IllegalArgumentException("Lead not found"));
            opp.setLead(lead);
        }

        Opportunity saved = opportunityRepository.save(opp);
        log.info("Opportunity {} '{}' created by {}", saved.getOpportunityNumber(), saved.getTitle(), adminEmail);
        return mapToResponse(saved);
    }

    /**
     * Convert a qualified Lead into an Opportunity in one step.
     */
    @Transactional
    public OpportunityResponse convertLeadToOpportunity(Long leadId, String adminEmail) {
        Admin owner = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new IllegalArgumentException("Lead not found"));

        if (!LeadStatus.QUALIFIED.matches(lead.getStatus())) {
            throw new IllegalArgumentException("Lead must be in QUALIFIED status to convert. Current: " + lead.getStatus());
        }
        if ("PENDING".equalsIgnoreCase(lead.getLostApprovalStatus())) {
            throw new IllegalStateException("Cannot convert a lead while a lost request is pending admin approval");
        }

        if (lead.getCompany() == null) {
            throw new IllegalArgumentException("Lead must have an associated company to convert to an opportunity");
        }

        Opportunity opp = new Opportunity();
        opp.setOpportunityNumber(generateOpportunityNumber());
        opp.setTitle(lead.getTitle());
        opp.setLead(lead);
        opp.setCompany(lead.getCompany());
        opp.setContact(lead.getContact());
        opp.setOwnerAdmin(owner);
        opp.setStage(OpportunityStage.QUALIFIED.getValue());
        opp.setValue(lead.getEstimatedValue());
        opp.setProbability(OpportunityStage.QUALIFIED.getProbability());

        Opportunity saved = opportunityRepository.save(opp);

        // Update the lead
        lead.setStatus(LeadStatus.CONVERTED.getValue());
        lead.setConvertedToOpportunityId(saved.getId());
        leadRepository.save(lead);

        log.info("Lead {} converted to Opportunity {} by {}", lead.getLeadNumber(), saved.getOpportunityNumber(), adminEmail);
        return mapToResponse(saved);
    }

    /**
     * Update opportunity stage with validation.
     */
    @Transactional
    public OpportunityResponse updateStage(Long opportunityId, UpdateOpportunityStageDTO dto) {
        return updateStage(opportunityId, dto, null);
    }

    /**
     * Update opportunity stage with role-aware guardrails for LOST transitions.
     */
    @Transactional
    public OpportunityResponse updateStage(Long opportunityId, UpdateOpportunityStageDTO dto, String adminEmail) {
        Opportunity opp = findById(opportunityId);

        if ("PENDING".equalsIgnoreCase(opp.getLostApprovalStatus())) {
            throw new IllegalStateException("Cannot change stage while a Lost Request is pending admin approval");
        }

        OpportunityStage current = OpportunityStage.fromString(opp.getStage());
        OpportunityStage target = OpportunityStage.fromString(dto.getStage());

        if (target == null) {
            throw new IllegalArgumentException("Unknown opportunity stage: " + dto.getStage());
        }
        if (!OpportunityStage.isValidTransition(current, target)) {
            if (current == OpportunityStage.LOST) {
                throw new IllegalArgumentException(
                        "Cannot directly move a LOST opportunity to '" + dto.getStage()
                                + "'. An Admin must revoke the lost status first.");
            }
            throw new IllegalArgumentException("Invalid stage transition from '"
                    + opp.getStage() + "' to '" + dto.getStage() + "'");
        }

        if (target == OpportunityStage.LOST) {
            Admin actingAdmin = resolveAdminIfPresent(adminEmail);
            if (actingAdmin != null && (actingAdmin.getRole() == null || !"ADMIN".equalsIgnoreCase(actingAdmin.getRole()))) {
                throw new IllegalArgumentException(
                        "Only an ADMIN can directly mark an opportunity as lost. Please submit a lost request for admin approval.");
            }
            opp.setPreviousStage(opp.getStage());
            opp.setLostReason(dto.getLostReason());
            opp.setLostApprovalStatus("APPROVED");
            opp.setLostApprovedByAdmin(actingAdmin);
            opp.setLostApprovedAt(LocalDateTime.now());
        }

        opp.setStage(target.getValue());
        opp.setProbability(target.getProbability());

        Opportunity saved = opportunityRepository.save(opp);
        log.info("Opportunity {} stage updated to '{}'", saved.getOpportunityNumber(), target.getValue());
        return mapToResponse(saved);
    }

    /**
     * Step 1 of Lost Workflow: Sales Rep / Admin requests to mark an Opportunity as Lost.
     * Saves previousStage, lostReason, and sends notification to active Admins.
     */
    @Transactional
    public OpportunityResponse requestLost(Long opportunityId, LostRequestDTO dto, String adminEmail) {
        Admin requester = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        Opportunity opp = findById(opportunityId);
        OpportunityStage current = OpportunityStage.fromString(opp.getStage());

        if (current == null || !current.isActiveStage()) {
            throw new IllegalArgumentException(
                    "Lost request can only be raised for active opportunities. Current stage: " + opp.getStage());
        }
        if ("PENDING".equalsIgnoreCase(opp.getLostApprovalStatus())) {
            throw new IllegalStateException("A lost request is already pending approval for this opportunity");
        }

        String reason = dto != null ? dto.getEffectiveLostReason() : null;
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("Lost reason is required");
        }

        opp.setPreviousStage(opp.getStage());
        opp.setLostReason(reason);
        opp.setLostApprovalStatus("PENDING");
        opp.setLostRequestedByAdmin(requester);
        opp.setLostRequestedAt(LocalDateTime.now());
        opp.setLostApprovedByAdmin(null);
        opp.setLostApprovedAt(null);
        opp.setLostRevokedByAdmin(null);
        opp.setLostRevokedAt(null);
        opp.setLostRevokeReason(null);

        Opportunity saved = opportunityRepository.save(opp);

        adminNotificationService.notifyAdminsOfLostRequest(
                "Opportunity", saved.getId(), saved.getOpportunityNumber(), saved.getTitle(),
                saved.getPreviousStage(), saved.getLostReason(), requester);

        log.info("Lost request submitted for Opportunity {} (previousStage='{}') by {}",
                saved.getOpportunityNumber(), saved.getPreviousStage(), adminEmail);
        return mapToResponse(saved);
    }

    /**
     * Step 2 of Lost Workflow: Admin approves a pending Lost Request.
     * Moves Opportunity stage to 'lost' (probability 0) and records which Admin approved it.
     */
    @Transactional
    public OpportunityResponse approveLost(Long opportunityId, AdminLostActionDTO dto, String adminEmail) {
        Admin admin = requireSuperAdmin(adminEmail);
        Opportunity opp = findById(opportunityId);

        if (OpportunityStage.LOST.matches(opp.getStage())) {
            throw new IllegalStateException("Opportunity is already in LOST stage");
        }
        if (OpportunityStage.WON.matches(opp.getStage())) {
            throw new IllegalArgumentException("Cannot mark a WON opportunity as lost");
        }
        if (!"PENDING".equalsIgnoreCase(opp.getLostApprovalStatus())) {
            throw new IllegalStateException("No pending lost request found for this opportunity");
        }

        if (opp.getPreviousStage() == null || opp.getPreviousStage().trim().isEmpty()) {
            opp.setPreviousStage(opp.getStage());
        }
        if (dto != null && dto.getEffectiveReason() != null) {
            opp.setLostReason(dto.getEffectiveReason());
        }

        opp.setStage(OpportunityStage.LOST.getValue());
        opp.setProbability(OpportunityStage.LOST.getProbability());
        opp.setLostApprovalStatus("APPROVED");
        opp.setLostApprovedByAdmin(admin);
        opp.setLostApprovedAt(LocalDateTime.now());

        Opportunity saved = opportunityRepository.save(opp);

        notifyRequesterAndOwner(saved, "APPROVED", saved.getStage(), saved.getLostReason(), admin);

        log.info("Lost request APPROVED for Opportunity {} by admin {}", saved.getOpportunityNumber(), adminEmail);
        return mapToResponse(saved);
    }

    /**
     * Step 3 of Lost Workflow: Admin revokes a Lost Opportunity (or rejects a pending Lost Request)
     * and moves it back to its previous pipeline stage (or specified target stage).
     */
    @Transactional
    public OpportunityResponse revokeLost(Long opportunityId, AdminLostActionDTO dto, String adminEmail) {
        Admin admin = requireSuperAdmin(adminEmail);
        Opportunity opp = findById(opportunityId);

        boolean isCurrentlyLost = OpportunityStage.LOST.matches(opp.getStage());
        boolean isPendingRequest = "PENDING".equalsIgnoreCase(opp.getLostApprovalStatus());

        if (!isCurrentlyLost && !isPendingRequest) {
            throw new IllegalStateException(
                    "Opportunity is neither LOST nor pending a lost request (current stage: " + opp.getStage() + ")");
        }
        if (OpportunityStage.WON.matches(opp.getStage())) {
            throw new IllegalArgumentException("Cannot revoke lost stage on a WON opportunity");
        }

        OpportunityStage targetStage;
        String requestedTarget = dto != null ? dto.getEffectiveTargetPipeline() : null;
        if (requestedTarget != null) {
            OpportunityStage parsedTarget = OpportunityStage.fromString(requestedTarget);
            if (parsedTarget == null || !parsedTarget.isActiveStage()) {
                throw new IllegalArgumentException(
                        "Invalid target pipeline stage for revocation: " + requestedTarget
                                + ". Allowed: qualified, requirement, proposal, negotiation");
            }
            targetStage = parsedTarget;
        } else {
            OpportunityStage prev = OpportunityStage.fromString(opp.getPreviousStage());
            targetStage = (prev != null && prev.isActiveStage())
                    ? prev
                    : OpportunityStage.QUALIFIED;
        }

        String revokeReason = dto != null ? dto.getEffectiveReason() : null;

        opp.setStage(targetStage.getValue());
        opp.setProbability(targetStage.getProbability());
        opp.setLostApprovalStatus("REVOKED");
        opp.setLostRevokedByAdmin(admin);
        opp.setLostRevokedAt(LocalDateTime.now());
        opp.setLostRevokeReason(revokeReason);

        Opportunity saved = opportunityRepository.save(opp);

        notifyRequesterAndOwner(saved, "REVOKED", targetStage.getValue(), revokeReason, admin);

        log.info("Lost stage REVOKED for Opportunity {} by admin {} -> restored to '{}'",
                saved.getOpportunityNumber(), adminEmail, targetStage.getValue());
        return mapToResponse(saved);
    }

    public List<OpportunityResponse> getPendingLostRequests() {
        return opportunityRepository.findByLostApprovalStatusOrderByLostRequestedAtDesc("PENDING").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Admin requireSuperAdmin(String adminEmail) {
        Admin admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        if (admin.getRole() == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            throw new AccessDeniedException("Only ADMIN can approve or revoke lost opportunities");
        }
        return admin;
    }

    private Admin resolveAdminIfPresent(String adminEmail) {
        if (adminEmail == null || adminEmail.trim().isEmpty() || "system".equalsIgnoreCase(adminEmail.trim())) {
            return null;
        }
        return adminRepository.findByEmail(adminEmail).orElse(null);
    }

    private void notifyRequesterAndOwner(Opportunity opp, String decision, String targetStage, String reason, Admin decidedBy) {
        Set<Long> notifiedIds = new HashSet<>();
        if (opp.getLostRequestedByAdmin() != null) {
            notifiedIds.add(opp.getLostRequestedByAdmin().getId());
            adminNotificationService.notifyAdminOfLostDecision(
                    opp.getLostRequestedByAdmin(), decision, "Opportunity",
                    opp.getOpportunityNumber(), opp.getTitle(), targetStage, reason, decidedBy);
        }
        if (opp.getOwnerAdmin() != null && !notifiedIds.contains(opp.getOwnerAdmin().getId())) {
            adminNotificationService.notifyAdminOfLostDecision(
                    opp.getOwnerAdmin(), decision, "Opportunity",
                    opp.getOpportunityNumber(), opp.getTitle(), targetStage, reason, decidedBy);
        }
    }

    // ==================== KANBAN & QUERIES ====================

    public List<OpportunityResponse> getKanbanData() {
        List<String> activeStages = Arrays.asList(
                OpportunityStage.QUALIFIED.getValue(),
                OpportunityStage.REQUIREMENT.getValue(),
                OpportunityStage.PROPOSAL.getValue(),
                OpportunityStage.NEGOTIATION.getValue());
        return opportunityRepository.findByStageInOrderByExpectedCloseDateAsc(activeStages).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<OpportunityResponse> getAllOpportunities() {
        return opportunityRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    public OpportunityResponse getOpportunityById(Long id) {
        return mapToResponse(findById(id));
    }

    public List<OpportunityResponse> getOpportunitiesByCompany(Long companyId) {
        return opportunityRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    public Double getWeightedPipelineValue() {
        Double value = opportunityRepository.getWeightedPipelineValue();
        return value != null ? value : 0.0;
    }

    // ==================== HELPERS ====================

    public Opportunity findById(Long id) {
        return opportunityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Opportunity not found with ID: " + id));
    }

    private String generateOpportunityNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04d", (int) (Math.random() * 10000));
        return "OPP-" + datePart + "-" + randomPart;
    }

    public OpportunityResponse mapToResponse(Opportunity opp) {
        OpportunityResponse response = new OpportunityResponse();
        response.setId(opp.getId());
        response.setOpportunityNumber(opp.getOpportunityNumber());
        response.setTitle(opp.getTitle());
        response.setStage(opp.getStage());
        response.setValue(opp.getValue());
        response.setExpectedCloseDate(opp.getExpectedCloseDate());
        response.setProbability(opp.getProbability());
        response.setLinkedOrderId(opp.getLinkedOrderId());
        response.setLostReason(opp.getLostReason());
        response.setPreviousStage(opp.getPreviousStage());
        response.setLostApprovalStatus(opp.getLostApprovalStatus());
        response.setLostRequestedAt(opp.getLostRequestedAt());
        response.setLostApprovedAt(opp.getLostApprovedAt());
        response.setLostRevokedAt(opp.getLostRevokedAt());
        response.setLostRevokeReason(opp.getLostRevokeReason());
        response.setNotes(opp.getNotes());
        response.setCreatedAt(opp.getCreatedAt());
        response.setUpdatedAt(opp.getUpdatedAt());

        if (opp.getCompany() != null) {
            response.setCompanyId(opp.getCompany().getId());
            response.setCompanyName(opp.getCompany().getName());
        }
        if (opp.getContact() != null) {
            response.setContactId(opp.getContact().getId());
            response.setContactName(opp.getContact().getName());
        }
        if (opp.getLead() != null) {
            response.setLeadId(opp.getLead().getId());
            response.setLeadNumber(opp.getLead().getLeadNumber());
        }
        if (opp.getOwnerAdmin() != null) {
            response.setOwnerAdminId(opp.getOwnerAdmin().getId());
            response.setOwnerAdminName(opp.getOwnerAdmin().getName());
        }
        if (opp.getLostRequestedByAdmin() != null) {
            response.setLostRequestedByAdminId(opp.getLostRequestedByAdmin().getId());
            response.setLostRequestedByAdminName(opp.getLostRequestedByAdmin().getName());
        }
        if (opp.getLostApprovedByAdmin() != null) {
            response.setLostApprovedByAdminId(opp.getLostApprovedByAdmin().getId());
            response.setLostApprovedByAdminName(opp.getLostApprovedByAdmin().getName());
        }
        if (opp.getLostRevokedByAdmin() != null) {
            response.setLostRevokedByAdminId(opp.getLostRevokedByAdmin().getId());
            response.setLostRevokedByAdminName(opp.getLostRevokedByAdmin().getName());
        }

        return response;
    }
}
