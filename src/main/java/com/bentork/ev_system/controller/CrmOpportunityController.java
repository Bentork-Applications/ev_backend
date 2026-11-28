package com.bentork.ev_system.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.AdminLostActionDTO;
import com.bentork.ev_system.dto.request.CreateOpportunityDTO;
import com.bentork.ev_system.dto.request.LostRequestDTO;
import com.bentork.ev_system.dto.request.UpdateOpportunityStageDTO;
import com.bentork.ev_system.dto.response.OpportunityResponse;
import com.bentork.ev_system.service.OpportunityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/crm/opportunities")
@RequiredArgsConstructor
@Slf4j
public class CrmOpportunityController {

    private final OpportunityService opportunityService;

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> createOpportunity(@RequestBody CreateOpportunityDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(opportunityService.createOpportunity(dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/kanban")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<OpportunityResponse>> getKanbanData() {
        return ResponseEntity.ok(opportunityService.getKanbanData());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<OpportunityResponse>> getAllOpportunities() {
        return ResponseEntity.ok(opportunityService.getAllOpportunities());
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getOpportunityById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(opportunityService.getOpportunityById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{id:[0-9]+}/stage")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateStage(@PathVariable Long id, @RequestBody UpdateOpportunityStageDTO dto) {
        try {
            return ResponseEntity.ok(opportunityService.updateStage(id, dto, getCurrentUserEmail()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Step 1: Request to mark an Opportunity as Lost (pending Admin approval).
     */
    @RequestMapping(value = {"/{id:[0-9]+}/lost-request", "/{id:[0-9]+}/request-lost"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> requestOpportunityLost(@PathVariable Long id, @RequestBody LostRequestDTO dto) {
        try {
            return ResponseEntity.ok(opportunityService.requestLost(id, dto, getCurrentUserEmail()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Step 2: Admin approves a pending Opportunity Lost Request (transitions stage to LOST).
     */
    @RequestMapping(value = {"/{id:[0-9]+}/lost/approve", "/{id:[0-9]+}/approve-lost"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> approveOpportunityLost(
            @PathVariable Long id,
            @RequestBody(required = false) AdminLostActionDTO dto) {
        try {
            return ResponseEntity.ok(opportunityService.approveLost(id, dto, getCurrentUserEmail()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Step 3: Admin revokes Lost stage (or rejects pending Lost Request) and restores Opportunity to pipeline.
     */
    @RequestMapping(value = {"/{id:[0-9]+}/lost/revoke", "/{id:[0-9]+}/revoke-lost"}, method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> revokeOpportunityLost(
            @PathVariable Long id,
            @RequestBody(required = false) AdminLostActionDTO dto) {
        try {
            return ResponseEntity.ok(opportunityService.revokeLost(id, dto, getCurrentUserEmail()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * List Opportunities with pending Lost Requests awaiting Admin approval.
     */
    @GetMapping({"/lost-requests", "/pending-lost"})
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<OpportunityResponse>> getPendingLostRequests() {
        return ResponseEntity.ok(opportunityService.getPendingLostRequests());
    }

    @GetMapping("/pipeline-value")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<Double> getPipelineValue() {
        return ResponseEntity.ok(opportunityService.getWeightedPipelineValue());
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }
}
