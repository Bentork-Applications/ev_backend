package com.bentork.ev_system.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.SalesTargetDTO;
import com.bentork.ev_system.dto.response.SalesTargetResponse;
import com.bentork.ev_system.dto.response.SalesTargetsOverviewResponse;
import com.bentork.ev_system.service.CrmTargetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller providing sales target and target achievement APIs for the Sales CRM.
 * Mapped to both /crm/targets and /api/crm/targets for client compatibility.
 */
@RestController
@RequestMapping({"/api/crm/targets", "/crm/targets"})
@RequiredArgsConstructor
@Slf4j
public class CrmTargetController {

    private final CrmTargetService crmTargetService;

    /**
     * GET /crm/targets or /api/crm/targets
     * Returns monthly sales targets and target achievements overview.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<SalesTargetsOverviewResponse> getSalesTargets(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long adminId) {
        log.info("Fetching sales targets overview for year: {}, month: {}, adminId: {}", year, month, adminId);
        return ResponseEntity.ok(crmTargetService.getSalesTargetsOverview(year, month, adminId));
    }

    /**
     * GET /crm/targets/current or /api/crm/targets/current
     * Returns targets and achievements for the current active month.
     */
    @GetMapping("/current")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<SalesTargetResponse> getCurrentMonthTarget(
            @RequestParam(required = false) Long adminId) {
        LocalDate now = LocalDate.now();
        return ResponseEntity.ok(crmTargetService.getTargetForMonth(now.getYear(), now.getMonthValue(), adminId));
    }

    /**
     * GET /crm/targets/{month} or /api/crm/targets/{month}
     * Returns targets and achievements for a specific month (1-12).
     */
    @GetMapping("/{month}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<SalesTargetResponse> getTargetForSpecificMonth(
            @PathVariable int month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long adminId) {
        int targetYear = (year != null && year > 0) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(crmTargetService.getTargetForMonth(targetYear, month, adminId));
    }

    /**
     * POST /crm/targets or /api/crm/targets
     * Create or update target for a specific month.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> createOrUpdateTarget(@Valid @RequestBody SalesTargetDTO dto) {
        String adminEmail = getCurrentUserEmail();
        log.info("Setting sales target for period {}-{} by admin {}", dto.getYear(), dto.getMonth(), adminEmail);
        try {
            SalesTargetResponse response = crmTargetService.createOrUpdateTarget(dto, adminEmail);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * PUT /crm/targets or /api/crm/targets
     * Update target for a specific month.
     */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateTarget(@Valid @RequestBody SalesTargetDTO dto) {
        return createOrUpdateTarget(dto);
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "system";
    }
}
