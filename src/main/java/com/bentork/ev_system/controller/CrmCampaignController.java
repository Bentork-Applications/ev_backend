package com.bentork.ev_system.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.AddRecipientsDTO;
import com.bentork.ev_system.dto.request.CreateCampaignDTO;
import com.bentork.ev_system.dto.request.UpdateCampaignDTO;
import com.bentork.ev_system.dto.request.UpdateCampaignStatusDTO;
import com.bentork.ev_system.dto.response.CampaignResponse;
import com.bentork.ev_system.service.CampaignService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/crm/campaigns")
@RequiredArgsConstructor
public class CrmCampaignController {

    private final CampaignService campaignService;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> createCampaign(@RequestBody CreateCampaignDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(campaignService.createCampaign(dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping({"", "/all", "/list"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<CampaignResponse>> getAllCampaigns() {
        return ResponseEntity.ok(campaignService.getAllCampaigns());
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getCampaignById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(campaignService.getCampaignById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @RequestMapping(value = {"/{id:[0-9]+}", "/{id:[0-9]+}/update", "/update/{id:[0-9]+}"}, method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateCampaign(
            @PathVariable Long id,
            @RequestBody UpdateCampaignDTO dto) {
        try {
            return ResponseEntity.ok(campaignService.updateCampaign(id, dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to update campaign: " + e.getMessage());
        }
    }

    @RequestMapping(value = {"/{id:[0-9]+}/status", "/status/{id:[0-9]+}"}, method = {RequestMethod.PUT, RequestMethod.POST})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateCampaignStatus(
            @PathVariable Long id, 
            @RequestBody UpdateCampaignStatusDTO dto) {
        try {
            return ResponseEntity.ok(campaignService.updateCampaignStatus(id, dto.getStatus()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}/recipients")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> addRecipients(
            @PathVariable Long id, 
            @RequestBody AddRecipientsDTO dto) {
        try {
            return ResponseEntity.ok(campaignService.addRecipients(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}/add-from-segment/{segmentId:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> addRecipientsFromSegment(
            @PathVariable Long id,
            @PathVariable Long segmentId) {
        try {
            return ResponseEntity.ok(campaignService.addRecipientsFromSegment(id, segmentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}/send")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> sendCampaign(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(campaignService.sendCampaign(id, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Campaign dispatch failed: " + e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}/recipients")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getCampaignRecipients(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        try {
            return ResponseEntity.ok(campaignService.getCampaignRecipients(id, page, size));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }
}
