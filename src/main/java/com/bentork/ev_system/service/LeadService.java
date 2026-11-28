package com.bentork.ev_system.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.CombinedCallLeadDTO;
import com.bentork.ev_system.dto.request.CreateLeadDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.request.UpdateLeadStatusDTO;
import com.bentork.ev_system.dto.response.BulkOperationResponse;
import com.bentork.ev_system.dto.response.CsvImportResponse;
import com.bentork.ev_system.dto.response.LeadResponse;
import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.enums.LeadStatus;
import com.bentork.ev_system.exception.DuplicateLeadException;
import com.bentork.ev_system.model.Activity;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final SalesCompanyRepository companyRepository;
    private final SalesContactRepository contactRepository;
    private final ActivityRepository activityRepository;
    private final AdminRepository adminRepository;
    private final DuplicateMatchingService duplicateMatchingService;
    private final IAdminNotificationService adminNotificationService;

    // ==================== STANDARD LEAD CRUD ====================

    @Transactional
    public LeadResponse createLead(CreateLeadDTO dto, String adminEmail) {
        Admin owner = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        SalesCompany company = null;
        if (dto.getCompanyId() != null) {
            company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        }

        SalesContact contact = null;
        if (dto.getContactId() != null) {
            contact = contactRepository.findById(dto.getContactId())
                    .orElseThrow(() -> new IllegalArgumentException("Contact not found"));
        }

        String rawPhone = (dto.getPhone() != null && !dto.getPhone().trim().isEmpty())
                ? dto.getPhone().trim()
                : (contact != null ? contact.getPhone() : null);
        String rawEmail = (dto.getEmail() != null && !dto.getEmail().trim().isEmpty())
                ? dto.getEmail().trim()
                : (contact != null ? contact.getEmail() : null);

        // Enforce duplicate mobile & email prevention before saving
        duplicateMatchingService.checkDuplicateForNewLead(rawPhone, rawEmail, dto.getContactId());

        String normalizedPhone = DuplicateMatchingService.normalizePhoneOrRaw(rawPhone);
        String normalizedEmail = DuplicateMatchingService.normalizeEmail(rawEmail);

        if (contact == null && (normalizedPhone != null || normalizedEmail != null)
                && dto.getContactName() != null && !dto.getContactName().trim().isEmpty()) {
            contact = new SalesContact();
            contact.setName(dto.getContactName().trim());
            contact.setPhone(normalizedPhone);
            contact.setEmail(normalizedEmail);
            contact.setCompany(company);
            contact.setPrimary(true);
            contact = contactRepository.saveAndFlush(contact);
        }

        Lead lead = new Lead();
        lead.setLeadNumber(generateLeadNumber());
        lead.setTitle(dto.getTitle());
        lead.setSource(dto.getSource());
        lead.setStatus(LeadStatus.NEW.getValue());
        lead.setOwnerAdmin(owner);
        lead.setCity(dto.getCity());
        lead.setState(dto.getState());
        lead.setEstimatedValue(dto.getEstimatedValue());
        lead.setNotes(dto.getNotes());
        lead.setPhone(normalizedPhone);
        lead.setEmail(normalizedEmail);
        lead.setCompany(company);
        lead.setContact(contact);

        if (dto.getNextFollowUpDate() != null && !dto.getNextFollowUpDate().isEmpty()) {
            lead.setNextFollowUpDate(LocalDate.parse(dto.getNextFollowUpDate().trim()));
        }
        if (dto.getNextFollowUpTime() != null && !dto.getNextFollowUpTime().isEmpty()) {
            lead.setNextFollowUpTime(LocalTime.parse(dto.getNextFollowUpTime().trim()));
        }

        Lead saved = leadRepository.saveAndFlush(lead);
        log.info("Lead {} '{}' created by {}", saved.getLeadNumber(), saved.getTitle(), adminEmail);
        return mapToResponse(saved);
    }

    // ==================== COMBINED CALL + LEAD WORKFLOW ====================

    /**
     * The BRD's key workflow: Search by phone/email → if no match,
     * create Contact + Company + Lead + Call Activity in one transaction.
     * Rejects with 409 Conflict if mobile number or email already exists.
     */
    @Transactional
    public LeadResponse combinedCallAndLead(CombinedCallLeadDTO dto, String adminEmail) {
        Admin owner = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        // 1. Check for duplicate mobile number or email BEFORE creating any records
        duplicateMatchingService.checkDuplicateForNewLead(dto.getContactPhone(), dto.getContactEmail(), null);

        String normalizedPhone = DuplicateMatchingService.normalizePhoneOrRaw(dto.getContactPhone());
        String normalizedEmail = DuplicateMatchingService.normalizeEmail(dto.getContactEmail());

        SalesCompany company = null;
        // 2. Create new company (if name provided)
        if (dto.getCompanyName() != null && !dto.getCompanyName().isEmpty()) {
            company = new SalesCompany();
            company.setName(dto.getCompanyName());
            company.setIndustry(dto.getIndustry());
            company.setCompanyType(dto.getCompanyType());
            company.setCity(dto.getCity());
            company.setState(dto.getState());
            company.setSource("phone_call");
            company.setOwnerAdmin(owner);
            company = companyRepository.save(company);
            log.info("New company '{}' created during combined call", company.getName());
        }

        // 3. Create new contact
        SalesContact contact = new SalesContact();
        contact.setName(dto.getContactName());
        contact.setPhone(normalizedPhone);
        contact.setEmail(normalizedEmail);
        contact.setDesignation(dto.getContactDesignation());
        contact.setCompany(company);
        contact.setPrimary(true);
        contact = contactRepository.saveAndFlush(contact);
        log.info("New contact '{}' created during combined call", contact.getName());

        // 4. Create the lead
        Lead lead = new Lead();
        lead.setLeadNumber(generateLeadNumber());
        lead.setTitle(dto.getLeadTitle() != null ? dto.getLeadTitle() : "Call from " + dto.getContactName());
        lead.setSource(dto.getSource() != null ? dto.getSource() : "phone_call");
        lead.setStatus(LeadStatus.CONTACTED.getValue()); // Already contacted since we're logging a call
        lead.setOwnerAdmin(owner);
        lead.setCompany(company);
        lead.setContact(contact);
        lead.setPhone(normalizedPhone);
        lead.setEmail(normalizedEmail);
        lead.setCity(dto.getCity());
        lead.setState(dto.getState());
        lead.setEstimatedValue(dto.getEstimatedValue());

        if (dto.getFollowUpDate() != null && !dto.getFollowUpDate().isEmpty()) {
            lead.setNextFollowUpDate(LocalDate.parse(dto.getFollowUpDate().trim()));
        }
        if (dto.getFollowUpTime() != null && !dto.getFollowUpTime().isEmpty()) {
            lead.setNextFollowUpTime(LocalTime.parse(dto.getFollowUpTime().trim()));
        }

        lead = leadRepository.saveAndFlush(lead);

        // 5. Log the call activity
        Activity activity = new Activity();
        activity.setLead(lead);
        activity.setCompany(company);
        activity.setContact(contact);
        activity.setActivityType("call");
        activity.setCallOutcome(dto.getCallOutcome());
        activity.setSubject("Initial call with " + dto.getContactName());
        activity.setNotes(dto.getCallNotes());
        activity.setDurationMinutes(dto.getCallDurationMinutes());
        activity.setPerformedByAdmin(owner);
        activity.setActivityDate(LocalDateTime.now());

        if (dto.getFollowUpDate() != null && !dto.getFollowUpDate().isEmpty()) {
            activity.setFollowUpDate(LocalDate.parse(dto.getFollowUpDate().trim()));
        }
        if (dto.getFollowUpTime() != null && !dto.getFollowUpTime().isEmpty()) {
            activity.setFollowUpTime(LocalTime.parse(dto.getFollowUpTime().trim()));
        }

        activityRepository.save(activity);

        log.info("Combined call+lead workflow complete. Lead: {}, Contact: {}, Admin: {}",
                lead.getLeadNumber(), contact.getPhone(), adminEmail);

        return mapToResponse(lead);
    }

    // ==================== STATUS MANAGEMENT & LOST WORKFLOW ====================

    @Transactional
    public LeadResponse updateLeadStatus(Long leadId, String newStatus) {
        UpdateLeadStatusDTO dto = new UpdateLeadStatusDTO();
        dto.setStatus(newStatus);
        return updateLeadStatus(leadId, dto, null);
    }

    @Transactional
    public LeadResponse updateLeadStatus(Long leadId, UpdateLeadStatusDTO dto, String adminEmail) {
        Lead lead = findById(leadId);
        String newStatus = dto != null ? dto.getStatus() : null;

        if ("PENDING".equalsIgnoreCase(lead.getLostApprovalStatus())) {
            throw new IllegalStateException("Cannot change status while a Lost Request is pending admin approval");
        }

        LeadStatus current = LeadStatus.fromString(lead.getStatus());
        LeadStatus target = LeadStatus.fromString(newStatus);

        if (target == null) {
            throw new IllegalArgumentException("Unknown lead status: " + newStatus);
        }
        if (!LeadStatus.isValidTransition(current, target)) {
            if (current == LeadStatus.LOST) {
                throw new IllegalArgumentException(
                        "Cannot directly move a LOST lead to '" + newStatus + "'. An Admin must revoke the lost status first.");
            }
            throw new IllegalArgumentException("Invalid status transition from '"
                    + lead.getStatus() + "' to '" + newStatus + "'");
        }

        if (target == LeadStatus.LOST) {
            Admin actingAdmin = resolveAdminIfPresent(adminEmail);
            if (actingAdmin != null && (actingAdmin.getRole() == null || !"ADMIN".equalsIgnoreCase(actingAdmin.getRole()))) {
                throw new IllegalArgumentException(
                        "Only an ADMIN can directly mark a lead as lost. Please submit a lost request for admin approval.");
            }
            lead.setPreviousStatus(lead.getStatus());
            if (dto.getLostReason() != null && !dto.getLostReason().trim().isEmpty()) {
                lead.setLostReason(dto.getLostReason().trim());
            }
            lead.setLostApprovalStatus("APPROVED");
            lead.setLostApprovedByAdmin(actingAdmin);
            lead.setLostApprovedAt(LocalDateTime.now());
        }

        lead.setStatus(target.getValue());
        Lead saved = leadRepository.save(lead);
        log.info("Lead {} status updated to '{}'", saved.getLeadNumber(), target.getValue());
        return mapToResponse(saved);
    }

    /**
     * Step 1 of Lost Workflow: Sales Rep / Admin requests to mark a Lead as Lost.
     * Saves previousStatus, lostReason, and notifies active Admins.
     */
    @Transactional
    public LeadResponse requestLost(Long leadId, LostRequestDTO dto, String adminEmail) {
        Admin requester = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        Lead lead = findById(leadId);
        LeadStatus current = LeadStatus.fromString(lead.getStatus());

        if (current == null || !current.isActiveStatus()) {
            throw new IllegalArgumentException(
                    "Lost request can only be raised for active leads. Current status: " + lead.getStatus());
        }
        if ("PENDING".equalsIgnoreCase(lead.getLostApprovalStatus())) {
            throw new IllegalStateException("A lost request is already pending approval for this lead");
        }

        String reason = dto != null ? dto.getEffectiveLostReason() : null;
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("Lost reason is required");
        }

        lead.setPreviousStatus(lead.getStatus());
        lead.setLostReason(reason);
        lead.setLostApprovalStatus("PENDING");
        lead.setLostRequestedByAdmin(requester);
        lead.setLostRequestedAt(LocalDateTime.now());
        lead.setLostApprovedByAdmin(null);
        lead.setLostApprovedAt(null);
        lead.setLostRevokedByAdmin(null);
        lead.setLostRevokedAt(null);
        lead.setLostRevokeReason(null);

        Lead saved = leadRepository.save(lead);

        adminNotificationService.notifyAdminsOfLostRequest(
                "Lead", saved.getId(), saved.getLeadNumber(), saved.getTitle(),
                saved.getPreviousStatus(), saved.getLostReason(), requester);

        log.info("Lost request submitted for Lead {} (previousStatus='{}') by {}",
                saved.getLeadNumber(), saved.getPreviousStatus(), adminEmail);
        return mapToResponse(saved);
    }

    /**
     * Step 2 of Lost Workflow: Admin approves a pending Lost Request.
     * Moves Lead status to 'lost' and records which Admin approved it.
     */
    @Transactional
    public LeadResponse approveLost(Long leadId, AdminLostActionDTO dto, String adminEmail) {
        Admin admin = requireSuperAdmin(adminEmail);
        Lead lead = findById(leadId);

        if (LeadStatus.LOST.matches(lead.getStatus())) {
            throw new IllegalStateException("Lead is already in LOST status");
        }
        if (LeadStatus.CONVERTED.matches(lead.getStatus())) {
            throw new IllegalArgumentException("Cannot mark a CONVERTED lead as lost");
        }
        if (!"PENDING".equalsIgnoreCase(lead.getLostApprovalStatus())) {
            throw new IllegalStateException("No pending lost request found for this lead");
        }

        if (lead.getPreviousStatus() == null || lead.getPreviousStatus().trim().isEmpty()) {
            lead.setPreviousStatus(lead.getStatus());
        }
        if (dto != null && dto.getEffectiveReason() != null) {
            lead.setLostReason(dto.getEffectiveReason());
        }

        lead.setStatus(LeadStatus.LOST.getValue());
        lead.setLostApprovalStatus("APPROVED");
        lead.setLostApprovedByAdmin(admin);
        lead.setLostApprovedAt(LocalDateTime.now());

        Lead saved = leadRepository.save(lead);

        notifyRequesterAndOwner(saved, "APPROVED", saved.getStatus(), saved.getLostReason(), admin);

        log.info("Lost request APPROVED for Lead {} by admin {}", saved.getLeadNumber(), adminEmail);
        return mapToResponse(saved);
    }

    /**
     * Step 3 of Lost Workflow: Admin revokes a Lost Lead (or rejects a pending Lost Request)
     * and moves the Lead back to its previous pipeline status (or specified target status).
     */
    @Transactional
    public LeadResponse revokeLost(Long leadId, AdminLostActionDTO dto, String adminEmail) {
        Admin admin = requireSuperAdmin(adminEmail);
        Lead lead = findById(leadId);

        boolean isCurrentlyLost = LeadStatus.LOST.matches(lead.getStatus());
        boolean isPendingRequest = "PENDING".equalsIgnoreCase(lead.getLostApprovalStatus());

        if (!isCurrentlyLost && !isPendingRequest) {
            throw new IllegalStateException(
                    "Lead is neither LOST nor pending a lost request (current status: " + lead.getStatus() + ")");
        }
        if (LeadStatus.CONVERTED.matches(lead.getStatus())) {
            throw new IllegalArgumentException("Cannot revoke lost status on a CONVERTED lead");
        }

        String targetStatus;
        String requestedTarget = dto != null ? dto.getEffectiveTargetPipeline() : null;
        if (requestedTarget != null) {
            LeadStatus parsedTarget = LeadStatus.fromString(requestedTarget);
            if (parsedTarget == null || !parsedTarget.isActiveStatus()) {
                throw new IllegalArgumentException(
                        "Invalid target pipeline status for revocation: " + requestedTarget
                                + ". Allowed: new, contacted, qualified, nurture");
            }
            targetStatus = parsedTarget.getValue();
        } else {
            LeadStatus prev = LeadStatus.fromString(lead.getPreviousStatus());
            targetStatus = (prev != null && prev.isActiveStatus())
                    ? prev.getValue()
                    : LeadStatus.QUALIFIED.getValue();
        }

        String revokeReason = dto != null ? dto.getEffectiveReason() : null;

        lead.setStatus(targetStatus);
        lead.setLostApprovalStatus("REVOKED");
        lead.setLostRevokedByAdmin(admin);
        lead.setLostRevokedAt(LocalDateTime.now());
        lead.setLostRevokeReason(revokeReason);

        Lead saved = leadRepository.save(lead);

        notifyRequesterAndOwner(saved, "REVOKED", targetStatus, revokeReason, admin);

        log.info("Lost status REVOKED for Lead {} by admin {} -> restored to '{}'",
                saved.getLeadNumber(), adminEmail, targetStatus);
        return mapToResponse(saved);
    }

    public List<LeadResponse> getPendingLostRequests() {
        return leadRepository.findByLostApprovalStatusOrderByLostRequestedAtDesc("PENDING").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private Admin requireSuperAdmin(String adminEmail) {
        Admin admin = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        if (admin.getRole() == null || !"ADMIN".equalsIgnoreCase(admin.getRole())) {
            throw new AccessDeniedException("Only ADMIN can approve or revoke lost leads");
        }
        return admin;
    }

    private Admin resolveAdminIfPresent(String adminEmail) {
        if (adminEmail == null || adminEmail.trim().isEmpty() || "system".equalsIgnoreCase(adminEmail.trim())) {
            return null;
        }
        return adminRepository.findByEmail(adminEmail).orElse(null);
    }

    private void notifyRequesterAndOwner(Lead lead, String decision, String targetStage, String reason, Admin decidedBy) {
        Set<Long> notifiedIds = new HashSet<>();
        if (lead.getLostRequestedByAdmin() != null) {
            notifiedIds.add(lead.getLostRequestedByAdmin().getId());
            adminNotificationService.notifyAdminOfLostDecision(
                    lead.getLostRequestedByAdmin(), decision, "Lead",
                    lead.getLeadNumber(), lead.getTitle(), targetStage, reason, decidedBy);
        }
        if (lead.getOwnerAdmin() != null && !notifiedIds.contains(lead.getOwnerAdmin().getId())) {
            adminNotificationService.notifyAdminOfLostDecision(
                    lead.getOwnerAdmin(), decision, "Lead",
                    lead.getLeadNumber(), lead.getTitle(), targetStage, reason, decidedBy);
        }
    }

    // ==================== BULK OPERATIONS ====================

    /**
     * Bulk assign leads to a new owner admin.
     */
    @Transactional
    public BulkOperationResponse bulkAssignOwner(List<Long> leadIds, Long targetOwnerAdminId) {
        Admin targetOwner = adminRepository.findById(targetOwnerAdminId)
                .orElseThrow(() -> new IllegalArgumentException("Target admin not found with ID: " + targetOwnerAdminId));

        BulkOperationResponse response = new BulkOperationResponse();
        response.setTotalRequested(leadIds.size());

        int successCount = 0;
        for (Long leadId : leadIds) {
            try {
                Lead lead = leadRepository.findById(leadId)
                        .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));
                lead.setOwnerAdmin(targetOwner);
                leadRepository.save(lead);
                successCount++;
            } catch (Exception e) {
                response.getErrors().add("Lead ID " + leadId + ": " + e.getMessage());
            }
        }

        response.setSuccessCount(successCount);
        response.setFailureCount(leadIds.size() - successCount);
        log.info("Bulk assign completed: {}/{} leads assigned to admin {}", successCount, leadIds.size(), targetOwnerAdminId);
        return response;
    }

    /**
     * Bulk tag leads. Tags are merged (not replaced) with existing tags.
     */
    @Transactional
    public BulkOperationResponse bulkTag(List<Long> leadIds, List<String> newTags) {
        BulkOperationResponse response = new BulkOperationResponse();
        response.setTotalRequested(leadIds.size());

        int successCount = 0;
        for (Long leadId : leadIds) {
            try {
                Lead lead = leadRepository.findById(leadId)
                        .orElseThrow(() -> new IllegalArgumentException("Lead not found: " + leadId));

                // Merge existing tags with new tags
                Set<String> allTags = new HashSet<>();
                if (lead.getTags() != null && !lead.getTags().isEmpty()) {
                    allTags.addAll(Arrays.asList(lead.getTags().split(",")));
                }
                for (String tag : newTags) {
                    allTags.add(tag.trim().toLowerCase());
                }
                lead.setTags(String.join(",", allTags));
                leadRepository.save(lead);
                successCount++;
            } catch (Exception e) {
                response.getErrors().add("Lead ID " + leadId + ": " + e.getMessage());
            }
        }

        response.setSuccessCount(successCount);
        response.setFailureCount(leadIds.size() - successCount);
        log.info("Bulk tag completed: {}/{} leads tagged", successCount, leadIds.size());
        return response;
    }

    // ==================== CSV / EXCEL IMPORT ====================

    /**
     * Import leads from a CSV or Excel (.xlsx/.xls) file.
     * Enforces duplicate prevention by mobile number and email (both against DB and within the file).
     * Expected headers: title,source,city,state,estimatedValue,notes,contactName,contactPhone,contactEmail,companyName
     */
    @Transactional
    public CsvImportResponse importFromCsv(MultipartFile file, String adminEmail) {
        Admin owner = adminRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Import file is empty");
        }

        CsvImportResponse response = new CsvImportResponse();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        boolean isExcel = originalFilename.endsWith(".xlsx") || originalFilename.endsWith(".xls");

        try {
            String[] headers;
            List<String[]> dataRows;

            if (isExcel) {
                ParsedTable table = parseExcelTable(file.getInputStream());
                headers = table.headers;
                dataRows = table.rows;
            } else {
                ParsedTable table = parseCsvTable(file.getInputStream());
                headers = table.headers;
                dataRows = table.rows;
            }

            if (headers == null || headers.length == 0) {
                throw new IllegalArgumentException("Import file is empty");
            }

            Set<String> seenPhonesInFile = new HashSet<>();
            Set<String> seenEmailsInFile = new HashSet<>();
            String defaultSource = isExcel ? "excel_import" : "csv_import";

            int rowNum = 1;
            for (String[] values : dataRows) {
                rowNum++;
                response.setTotalRows(response.getTotalRows() + 1);

                try {
                    if (values.length < 2) {
                        response.setSkippedCount(response.getSkippedCount() + 1);
                        response.getErrors().add("Row " + rowNum + ": insufficient columns");
                        continue;
                    }

                    String title = getColumnValue(headers, values, "title", "leadtitle", "leadname", "subject");
                    String source = getColumnValue(headers, values, "source", "leadsource", "channel", "leadchannel");
                    String city = getColumnValue(headers, values, "city", "location", "town");
                    String state = getColumnValue(headers, values, "state", "province", "region");
                    String estimatedValueStr = getColumnValue(headers, values, "estimatedvalue", "value", "dealvalue", "amount", "budget");
                    String notes = getColumnValue(headers, values, "notes", "note", "remarks", "remark", "comment", "description");
                    String contactName = getColumnValue(headers, values, "contactname", "name", "contact", "person", "fullname", "customername");
                    String contactPhone = getColumnValue(headers, values, "contactphone", "phone", "mobile", "phonenumber", "mobilenumber", "contactnumber", "contactno", "tel");
                    String contactEmail = getColumnValue(headers, values, "contactemail", "email", "emailaddress", "mail");
                    String companyName = getColumnValue(headers, values, "companyname", "company", "organization", "org", "firm", "businessname", "account");

                    String normalizedPhone = DuplicateMatchingService.normalizePhoneOrRaw(contactPhone);
                    String normalizedEmail = DuplicateMatchingService.normalizeEmail(contactEmail);
                    Set<String> phoneVariants = DuplicateMatchingService.buildPhoneVariants(contactPhone);

                    // 1. Check intra-file duplicate mobile or email
                    boolean dupInFile = false;
                    for (String variant : phoneVariants) {
                        if (seenPhonesInFile.contains(variant)) {
                            dupInFile = true;
                            break;
                        }
                    }
                    if (!dupInFile && normalizedEmail != null && seenEmailsInFile.contains(normalizedEmail)) {
                        dupInFile = true;
                    }
                    if (dupInFile) {
                        response.setSkippedCount(response.getSkippedCount() + 1);
                        response.setDuplicateCount(response.getDuplicateCount() + 1);
                        response.getErrors().add("Row " + rowNum + ": Duplicate mobile number or email within import file");
                        continue;
                    }

                    // 2. Check database duplicate mobile or email before saving
                    try {
                        duplicateMatchingService.checkDuplicateForNewLead(contactPhone, contactEmail, null);
                    } catch (DuplicateLeadException dupEx) {
                        response.setSkippedCount(response.getSkippedCount() + 1);
                        response.setDuplicateCount(response.getDuplicateCount() + 1);
                        response.getErrors().add("Row " + rowNum + ": " + dupEx.getMessage());
                        continue;
                    }

                    if (title == null || title.isEmpty()) {
                        if (contactName != null && !contactName.isEmpty()) {
                            title = contactName + (companyName != null && !companyName.isEmpty() ? " - " + companyName : "");
                        } else if (companyName != null && !companyName.isEmpty()) {
                            title = companyName + " Lead";
                        } else {
                            title = "Imported Lead #" + rowNum;
                        }
                    }

                    // Create or find company
                    SalesCompany company = null;
                    if (companyName != null && !companyName.isEmpty()) {
                        List<SalesCompany> existingCompanies = companyRepository.findByNameContainingIgnoreCaseAndActiveTrue(companyName);
                        if (!existingCompanies.isEmpty()) {
                            company = existingCompanies.get(0);
                        } else {
                            company = new SalesCompany();
                            company.setName(companyName);
                            company.setCity(city);
                            company.setState(state);
                            company.setSource(defaultSource);
                            company.setOwnerAdmin(owner);
                            company = companyRepository.save(company);
                        }
                    }

                    // Create contact if phone or email is present
                    SalesContact contact = null;
                    if (normalizedPhone != null || normalizedEmail != null) {
                        SalesContact newContact = new SalesContact();
                        newContact.setName(contactName != null && !contactName.isEmpty() ? contactName : "Unknown");
                        newContact.setPhone(normalizedPhone);
                        newContact.setEmail(normalizedEmail);
                        newContact.setCompany(company);
                        newContact.setPrimary(true);
                        contact = contactRepository.save(newContact);
                    }

                    // Create lead
                    Lead lead = new Lead();
                    lead.setLeadNumber(generateLeadNumber());
                    lead.setTitle(title);
                    lead.setSource(source != null && !source.isEmpty() ? source : defaultSource);
                    lead.setStatus(LeadStatus.NEW.getValue());
                    lead.setOwnerAdmin(owner);
                    lead.setCity(city);
                    lead.setState(state);
                    lead.setNotes(notes);
                    lead.setPhone(normalizedPhone);
                    lead.setEmail(normalizedEmail);
                    lead.setCompany(company);
                    lead.setContact(contact);

                    if (estimatedValueStr != null && !estimatedValueStr.isEmpty()) {
                        try {
                            lead.setEstimatedValue(Double.parseDouble(estimatedValueStr));
                        } catch (NumberFormatException ignored) {
                            // Skip invalid numbers
                        }
                    }

                    leadRepository.save(lead);
                    seenPhonesInFile.addAll(phoneVariants);
                    if (normalizedEmail != null) {
                        seenEmailsInFile.add(normalizedEmail);
                    }
                    response.setImportedCount(response.getImportedCount() + 1);

                } catch (Exception e) {
                    response.setSkippedCount(response.getSkippedCount() + 1);
                    response.getErrors().add("Row " + rowNum + ": " + e.getMessage());
                }
            }

            log.info("Lead import completed by {}: {} imported, {} skipped ({} duplicates) out of {} rows",
                    adminEmail, response.getImportedCount(), response.getSkippedCount(),
                    response.getDuplicateCount(), response.getTotalRows());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse import file: " + e.getMessage());
        }

        return response;
    }

    private static class ParsedTable {
        final String[] headers;
        final List<String[]> rows;

        ParsedTable(String[] headers, List<String[]> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }

    private ParsedTable parseCsvTable(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            String[] headers = parseCsvLine(headerLine);
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    rows.add(parseCsvLine(line));
                }
            }
            return new ParsedTable(headers, rows);
        }
    }

    private ParsedTable parseExcelTable(InputStream is) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 0) {
                throw new IllegalArgumentException("Excel file is empty");
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel header row is missing");
            }
            DataFormatter formatter = new DataFormatter();
            int colCount = headerRow.getLastCellNum();
            String[] headers = new String[colCount];
            for (int c = 0; c < colCount; c++) {
                Cell cell = headerRow.getCell(c);
                headers[c] = cell != null ? formatter.formatCellValue(cell).trim() : "";
            }

            List<String[]> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String[] vals = new String[colCount];
                boolean hasAnyValue = false;
                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.getCell(c);
                    String val = cell != null ? formatter.formatCellValue(cell).trim() : "";
                    vals[c] = val;
                    if (!val.isEmpty()) {
                        hasAnyValue = true;
                    }
                }
                if (hasAnyValue) {
                    rows.add(vals);
                }
            }
            return new ParsedTable(headers, rows);
        }
    }

    // ==================== QUERIES ====================

    public LeadResponse getLeadById(Long id) {
        return mapToResponse(findById(id));
    }

    public List<LeadResponse> getAllLeads() {
        return leadRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Server-side paginated query for all leads.
     */
    public PagedResponse<LeadResponse> getAllLeadsPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Lead> leadPage = leadRepository.findAllByOrderByCreatedAtDesc(pageable);
        List<LeadResponse> content = leadPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return PagedResponse.of(content, page, size, leadPage.getTotalElements(), leadPage.getTotalPages(), leadPage.isLast());
    }

    public List<LeadResponse> getLeadsByOwner(Long adminId) {
        return leadRepository.findByOwnerAdminIdOrderByCreatedAtDesc(adminId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<LeadResponse> getLeadsByStatus(String status) {
        return leadRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<LeadResponse> getLeadsByCompany(Long companyId) {
        return leadRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<LeadResponse> searchByPhone(String phone) {
        return leadRepository.searchByAnyPhoneContaining(phone).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<LeadResponse> getOverdueFollowUps() {
        List<String> terminalStatuses = Arrays.asList(
                LeadStatus.CONVERTED.getValue(),
                LeadStatus.LOST.getValue());
        return leadRepository.findByNextFollowUpDateBeforeAndStatusNotIn(LocalDate.now(), terminalStatuses).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ==================== HELPERS ====================

    public Lead findById(Long id) {
        return leadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lead not found with ID: " + id));
    }

    private String generateLeadNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04d", (int) (Math.random() * 10000));
        return "LD-" + datePart + "-" + randomPart;
    }

    /**
     * Parse a single CSV line, handling quoted values with commas inside.
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        return values.toArray(new String[0]);
    }

    /**
     * Get a column value by header name or any of its alias names (case and punctuation insensitive).
     */
    private String getColumnValue(String[] headers, String[] values, String... aliasNames) {
        for (int i = 0; i < headers.length; i++) {
            String normalizedHeader = headers[i].trim().toLowerCase().replaceAll("[^a-z0-9]", "");
            for (String alias : aliasNames) {
                String normalizedAlias = alias.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (normalizedHeader.equals(normalizedAlias)) {
                    return i < values.length ? values[i].trim() : null;
                }
            }
        }
        return null;
    }

    public LeadResponse mapToResponse(Lead lead) {
        LeadResponse response = new LeadResponse();
        response.setId(lead.getId());
        response.setLeadNumber(lead.getLeadNumber());
        response.setTitle(lead.getTitle());
        response.setSource(lead.getSource());
        response.setStatus(lead.getStatus());
        response.setCity(lead.getCity());
        response.setState(lead.getState());
        response.setEstimatedValue(lead.getEstimatedValue());
        response.setNotes(lead.getNotes());
        response.setNextFollowUpDate(lead.getNextFollowUpDate());
        response.setNextFollowUpTime(lead.getNextFollowUpTime());
        response.setIndiaMartLeadId(lead.getIndiaMartLeadId());
        response.setTags(lead.getTags());
        response.setConvertedToOpportunityId(lead.getConvertedToOpportunityId());
        response.setPreviousStatus(lead.getPreviousStatus());
        response.setLostReason(lead.getLostReason());
        response.setLostApprovalStatus(lead.getLostApprovalStatus());
        response.setLostRequestedAt(lead.getLostRequestedAt());
        response.setLostApprovedAt(lead.getLostApprovedAt());
        response.setLostRevokedAt(lead.getLostRevokedAt());
        response.setLostRevokeReason(lead.getLostRevokeReason());
        response.setCreatedAt(lead.getCreatedAt());
        response.setUpdatedAt(lead.getUpdatedAt());

        if (lead.getCompany() != null) {
            response.setCompanyId(lead.getCompany().getId());
            response.setCompanyName(lead.getCompany().getName());
        }
        if (lead.getContact() != null) {
            response.setContactId(lead.getContact().getId());
            response.setContactName(lead.getContact().getName());
            response.setContactPhone(lead.getContact().getPhone() != null ? lead.getContact().getPhone() : lead.getPhone());
            response.setContactEmail(lead.getContact().getEmail() != null ? lead.getContact().getEmail() : lead.getEmail());
        } else {
            response.setContactPhone(lead.getPhone());
            response.setContactEmail(lead.getEmail());
        }
        if (lead.getOwnerAdmin() != null) {
            response.setOwnerAdminId(lead.getOwnerAdmin().getId());
            response.setOwnerAdminName(lead.getOwnerAdmin().getName());
        }
        if (lead.getLostRequestedByAdmin() != null) {
            response.setLostRequestedByAdminId(lead.getLostRequestedByAdmin().getId());
            response.setLostRequestedByAdminName(lead.getLostRequestedByAdmin().getName());
        }
        if (lead.getLostApprovedByAdmin() != null) {
            response.setLostApprovedByAdminId(lead.getLostApprovedByAdmin().getId());
            response.setLostApprovedByAdminName(lead.getLostApprovedByAdmin().getName());
        }
        if (lead.getLostRevokedByAdmin() != null) {
            response.setLostRevokedByAdminId(lead.getLostRevokedByAdmin().getId());
            response.setLostRevokedByAdminName(lead.getLostRevokedByAdmin().getName());
        }

        return response;
    }
}

