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
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.SupportRequestDTO;
import com.bentork.ev_system.dto.response.SupportRequestResponse;
import com.bentork.ev_system.service.SupportRequestService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/support-requests")
@RequiredArgsConstructor
@Slf4j
public class SupportRequestController {

    private final SupportRequestService supportRequestService;

    // ==================== USER ENDPOINTS ====================

    @PostMapping("/user")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<SupportRequestResponse> createRequestAsUser(@RequestBody SupportRequestDTO requestDTO) {
        String email = getCurrentUserEmail();
        log.info("User {} submitting support request", email);
        SupportRequestResponse response = supportRequestService.createRequest(requestDTO, "END_USER", email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/my-requests")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<SupportRequestResponse>> getUserRequests() {
        String email = getCurrentUserEmail();
        log.info("Fetching support requests for user {}", email);
        return ResponseEntity.ok(supportRequestService.getRequestsBySubmitter(email));
    }

    // ==================== DEALER ENDPOINTS ====================

    @PostMapping("/dealer")
    @PreAuthorize("hasAuthority('DEALER')")
    public ResponseEntity<SupportRequestResponse> createRequestAsDealer(@RequestBody SupportRequestDTO requestDTO) {
        String email = getCurrentUserEmail();
        log.info("Dealer {} submitting support request", email);
        SupportRequestResponse response = supportRequestService.createRequest(requestDTO, "DEALER", email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/dealer/my-requests")
    @PreAuthorize("hasAuthority('DEALER')")
    public ResponseEntity<List<SupportRequestResponse>> getDealerRequests() {
        String email = getCurrentUserEmail();
        log.info("Fetching support requests for dealer {}", email);
        return ResponseEntity.ok(supportRequestService.getRequestsBySubmitter(email));
    }

    // ==================== ADMIN ENDPOINTS ====================

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<SupportRequestResponse>> getAllRequests() {
        log.info("Admin fetching all support requests");
        return ResponseEntity.ok(supportRequestService.getAllRequests());
    }

    @GetMapping("/admin/status/{status}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<SupportRequestResponse>> getRequestsByStatus(@PathVariable String status) {
        log.info("Admin fetching support requests by status: {}", status);
        return ResponseEntity.ok(supportRequestService.getRequestsByStatus(status));
    }

    @GetMapping("/admin/customer-type/{type}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<SupportRequestResponse>> getRequestsByCustomerType(@PathVariable String type) {
        log.info("Admin fetching support requests by customer type: {}", type);
        return ResponseEntity.ok(supportRequestService.getRequestsByCustomerType(type));
    }

    @PutMapping("/admin/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> updateRequestStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null) {
            return ResponseEntity.badRequest().body("Status is required");
        }
        
        log.info("Admin updating support request {} status to {}", id, status);
        try {
            SupportRequestResponse response = supportRequestService.updateStatus(id, status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update support request status", e);
            return ResponseEntity.internalServerError().body("Failed to update status");
        }
    }

    // ==================== HELPER METHODS ====================

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
