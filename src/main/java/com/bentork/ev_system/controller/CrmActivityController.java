package com.bentork.ev_system.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.CompleteActivityDTO;
import com.bentork.ev_system.dto.request.LogActivityDTO;
import com.bentork.ev_system.dto.request.RescheduleActivityDTO;
import com.bentork.ev_system.dto.response.ActivityOverviewStatsDTO;
import com.bentork.ev_system.dto.response.ActivityResponse;
import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.service.ActivityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/crm/activities")
@RequiredArgsConstructor
@Slf4j
public class CrmActivityController {

    private final ActivityService activityService;
    private final AdminRepository adminRepository;

    @PostMapping({"", "/log", "/create"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> logActivity(@RequestBody LogActivityDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(activityService.logActivity(dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Paginated and filterable query for all activities across the system.
     * Supports /all, /list, and root path /api/crm/activities.
     */
    @GetMapping({"", "/all", "/list"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getAllActivities(
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String callOutcome,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) Boolean myOnly,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "activityDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Boolean paged) {

        String effectiveType = activityType != null ? activityType : type;
        String effectiveOutcome = callOutcome != null ? callOutcome : outcome;
        String effectiveSearch = search != null ? search : query;

        Long effectiveAdminId = adminId;
        if (Boolean.TRUE.equals(myOnly)) {
            effectiveAdminId = getCurrentAdminId();
        }

        PagedResponse<ActivityResponse> result = activityService.getAllActivitiesPaged(
                effectiveType, status, effectiveOutcome, effectiveAdminId, startDate, endDate, effectiveSearch, page, size, sortBy, sortDir);

        if (Boolean.FALSE.equals(paged)) {
            return ResponseEntity.ok(result.getContent());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Dedicated Follow-up schedule retrieval partitioned by bucket:
     * - "overdue": past follow-up date, or today with past time, where status = 'pending'
     * - "today": follow-up date is today and status = 'pending', sorted chronologically by time
     * - "upcoming": follow-up date is after today and status = 'pending'
     * - "completed": follow-ups that have been completed
     * - "all": all activities that have a follow-up date
     */
    @GetMapping({"/follow-ups", "/followups", "/follow-up", "/followup"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getFollowUps(
            @RequestParam(defaultValue = "all") String bucket,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) Boolean myOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean paged) {

        String effectiveBucket = (bucket != null && !bucket.equals("all")) ? bucket : (filter != null ? filter : "all");
        Long effectiveAdminId = adminId;
        if (Boolean.TRUE.equals(myOnly)) {
            effectiveAdminId = getCurrentAdminId();
        }

        PagedResponse<ActivityResponse> result = activityService.getFollowUpsPaged(effectiveBucket, effectiveAdminId, page, size);
        if (Boolean.FALSE.equals(paged)) {
            return ResponseEntity.ok(result.getContent());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Overview KPI cards summary metrics:
     * - Calls made today & total calls
     * - Call outcomes breakdown (interested, callback, meeting scheduled, etc.)
     * - Today's meetings
     * - Follow-ups count by bucket: overdue, today, upcoming, completed
     */
    @GetMapping({"/summary", "/stats", "/overview"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<ActivityOverviewStatsDTO> getActivitySummary(
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) Boolean myOnly) {

        Long effectiveAdminId = adminId;
        if (Boolean.TRUE.equals(myOnly)) {
            effectiveAdminId = getCurrentAdminId();
        }

        return ResponseEntity.ok(activityService.getActivityOverviewStats(effectiveAdminId));
    }

    /**
     * Reschedule an existing follow-up with a new date and optional time.
     * Supports both PUT and POST methods.
     */
    @RequestMapping(value = {"/{id:[0-9]+}/reschedule", "/reschedule/{id:[0-9]+}"}, method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> rescheduleActivity(
            @PathVariable Long id,
            @RequestBody RescheduleActivityDTO dto) {
        try {
            return ResponseEntity.ok(activityService.rescheduleActivity(id, dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Mark an activity as completed. Can accept optional completion notes and outcome in the body.
     * Supports both PUT and POST methods.
     */
    @RequestMapping(value = {"/{id:[0-9]+}/complete", "/complete/{id:[0-9]+}"}, method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> completeActivity(
            @PathVariable Long id,
            @RequestBody(required = false) CompleteActivityDTO dto) {
        try {
            return ResponseEntity.ok(activityService.completeActivityWithDetails(id, dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Update the status of an activity. Valid statuses: "pending", "completed".
     */
    @RequestMapping(value = {"/{id:[0-9]+}/status", "/status/{id:[0-9]+}"}, method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateActivityStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String status = body != null ? body.get("status") : null;
            if (status == null || status.isEmpty()) {
                return ResponseEntity.badRequest().body("Status is required");
            }
            return ResponseEntity.ok(activityService.updateActivityStatus(id, status, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getActivityById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(activityService.getActivityById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/lead/{leadId:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<ActivityResponse>> getActivitiesForLead(@PathVariable Long leadId) {
        return ResponseEntity.ok(activityService.getActivitiesForLead(leadId));
    }

    @GetMapping("/company/{companyId:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<ActivityResponse>> getActivitiesForCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(activityService.getActivitiesForCompany(companyId));
    }

    @GetMapping("/opportunity/{opportunityId:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<ActivityResponse>> getActivitiesForOpportunity(@PathVariable Long opportunityId) {
        return ResponseEntity.ok(activityService.getActivitiesForOpportunity(opportunityId));
    }

    @GetMapping("/my-today")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<ActivityResponse>> getTodayActivities() {
        return ResponseEntity.ok(activityService.getTodayActivities(getCurrentUserEmail()));
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private Long getCurrentAdminId() {
        Admin admin = adminRepository.findByEmail(getCurrentUserEmail())
                .orElseThrow(() -> new IllegalArgumentException("Current admin not found"));
        return admin.getId();
    }
}
