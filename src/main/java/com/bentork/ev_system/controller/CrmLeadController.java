package com.bentork.ev_system.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.CombinedCallLeadDTO;
import com.bentork.ev_system.dto.request.CreateLeadDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.request.UpdateLeadStatusDTO;
import com.bentork.ev_system.dto.response.LeadResponse;
import com.bentork.ev_system.exception.DuplicateLeadException;
import com.bentork.ev_system.exception.GlobalExceptionHandler;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.service.LeadService;
import com.bentork.ev_system.service.OpportunityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/crm/leads")
@RequiredArgsConstructor
@Slf4j
public class CrmLeadController {

    private final LeadService leadService;
    private final OpportunityService opportunityService;
    private final AdminRepository adminRepository;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> createLead(@RequestBody CreateLeadDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(leadService.createLead(dto, getCurrentUserEmail()));
        } catch (DuplicateLeadException e) {
            return buildDuplicateConflictResponse(e);
        } catch (DataIntegrityViolationException e) {
            if (GlobalExceptionHandler.isDuplicateConstraintViolation(e)) {
                return buildConflictMessageResponse("Duplicate mobile number or email already exists");
            }
            return ResponseEntity.badRequest().body("Failed to create lead due to data constraint");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/combined-call")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> combinedCallAndLead(@RequestBody CombinedCallLeadDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(leadService.combinedCallAndLead(dto, getCurrentUserEmail()));
        } catch (DuplicateLeadException e) {
            return buildDuplicateConflictResponse(e);
        } catch (DataIntegrityViolationException e) {
            if (GlobalExceptionHandler.isDuplicateConstraintViolation(e)) {
                return buildConflictMessageResponse("Duplicate mobile number or email already exists");
            }
            return ResponseEntity.badRequest().body("Failed to create lead due to data constraint");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my-leads")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<LeadResponse>> getMyLeads() {
        Admin admin = adminRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        return ResponseEntity.ok(leadService.getLeadsByOwner(admin.getId()));
    }

    @GetMapping({"", "/all", "/list"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getAllLeads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean paged) {
        if (Boolean.TRUE.equals(paged)) {
            return ResponseEntity.ok(leadService.getAllLeadsPaged(page, size));
        }
        return ResponseEntity.ok(leadService.getAllLeads());
    }

    @RequestMapping(value = {"/bulk-assign", "/bulk/assign", "/assign-bulk"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> bulkAssign(@RequestBody com.bentork.ev_system.dto.request.BulkLeadAssignDTO dto) {
        if (dto == null || dto.getLeadIds() == null || dto.getLeadIds().isEmpty()) {
            return ResponseEntity.badRequest().body("At least one lead ID is required");
        }
        Long targetAdminId = dto.getEffectiveTargetOwnerAdminId();
        if (targetAdminId == null) {
            return ResponseEntity.badRequest().body("Target owner admin ID is required");
        }
        try {
            return ResponseEntity.ok(leadService.bulkAssignOwner(dto.getLeadIds(), targetAdminId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @RequestMapping(value = {"/bulk-tag", "/bulk/tag", "/tags-bulk", "/tag-bulk"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> bulkTag(@RequestBody com.bentork.ev_system.dto.request.BulkLeadTagDTO dto) {
        if (dto == null || dto.getLeadIds() == null || dto.getLeadIds().isEmpty()) {
            return ResponseEntity.badRequest().body("At least one lead ID is required");
        }
        List<String> effectiveTags = dto.getEffectiveTags();
        if (effectiveTags.isEmpty()) {
            return ResponseEntity.badRequest().body("At least one tag is required");
        }
        try {
            return ResponseEntity.ok(leadService.bulkTag(dto.getLeadIds(), effectiveTags));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping(value = {"/import", "/csv-import", "/import-csv", "/bulk-import", "/bulk/import"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> importLeads(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "csv", required = false) MultipartFile csv,
            @RequestParam(value = "leadsFile", required = false) MultipartFile leadsFile,
            MultipartHttpServletRequest request) {
        try {
            MultipartFile targetFile = file;
            if (targetFile == null || targetFile.isEmpty()) {
                targetFile = csv;
            }
            if (targetFile == null || targetFile.isEmpty()) {
                targetFile = leadsFile;
            }
            if ((targetFile == null || targetFile.isEmpty()) && request != null) {
                var iterator = request.getFileNames();
                if (iterator.hasNext()) {
                    targetFile = request.getFile(iterator.next());
                }
            }
            if (targetFile == null || targetFile.isEmpty()) {
                return ResponseEntity.badRequest().body("CSV file is required");
            }
            return ResponseEntity.ok(leadService.importFromCsv(targetFile, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("CSV import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("CSV import failed: " + e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getLeadById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(leadService.getLeadById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{id:[0-9]+}/status")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateLeadStatus(@PathVariable Long id, @RequestBody UpdateLeadStatusDTO dto) {
        try {
            return ResponseEntity.ok(leadService.updateLeadStatus(id, dto, getCurrentUserEmail()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Step 1: Request to mark a Lead as Lost (pending Admin approval).
     */
    @RequestMapping(value = {"/{id:[0-9]+}/lost-request", "/{id:[0-9]+}/request-lost"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> requestLeadLost(@PathVariable Long id, @RequestBody LostRequestDTO dto) {
        try {
            return ResponseEntity.ok(leadService.requestLost(id, dto, getCurrentUserEmail()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Step 2: Admin approves a pending Lead Lost Request (transitions Lead to LOST).
     */
    @RequestMapping(value = {"/{id:[0-9]+}/lost/approve", "/{id:[0-9]+}/approve-lost"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> approveLeadLost(
            @PathVariable Long id,
            @RequestBody(required = false) AdminLostActionDTO dto) {
        try {
            return ResponseEntity.ok(leadService.approveLost(id, dto, getCurrentUserEmail()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Step 3: Admin revokes Lost status (or rejects pending Lost Request) and restores Lead to pipeline.
     */
    @RequestMapping(value = {"/{id:[0-9]+}/lost/revoke", "/{id:[0-9]+}/revoke-lost"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> revokeLeadLost(
            @PathVariable Long id,
            @RequestBody(required = false) AdminLostActionDTO dto) {
        try {
            return ResponseEntity.ok(leadService.revokeLost(id, dto, getCurrentUserEmail()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * List Leads with pending Lost Requests awaiting Admin approval.
     */
    @GetMapping({"/lost-requests", "/pending-lost"})
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<LeadResponse>> getPendingLostRequests() {
        return ResponseEntity.ok(leadService.getPendingLostRequests());
    }

    @PutMapping("/{id:[0-9]+}/convert")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> convertLeadToOpportunity(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(opportunityService.convertLeadToOpportunity(id, getCurrentUserEmail()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/overdue-followups")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<LeadResponse>> getOverdueFollowUps() {
        return ResponseEntity.ok(leadService.getOverdueFollowUps());
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<LeadResponse>> searchByPhone(@RequestParam String phone) {
        return ResponseEntity.ok(leadService.searchByPhone(phone));
    }

    private ResponseEntity<Map<String, Object>> buildDuplicateConflictResponse(DuplicateLeadException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("message", e.getMessage());
        if (e.getDuplicateField() != null) {
            body.put("duplicateField", e.getDuplicateField());
        }
        if (e.getExistingLeadId() != null) {
            body.put("existingLeadId", e.getExistingLeadId());
        }
        if (e.getExistingLeadNumber() != null) {
            body.put("existingLeadNumber", e.getExistingLeadNumber());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private ResponseEntity<Map<String, Object>> buildConflictMessageResponse(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }
}
