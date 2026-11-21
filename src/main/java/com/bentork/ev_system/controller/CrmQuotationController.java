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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.CreateQuotationDTO;
import com.bentork.ev_system.dto.response.QuotationResponse;
import com.bentork.ev_system.service.QuotationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/crm/quotations")
@RequiredArgsConstructor
@Slf4j
public class CrmQuotationController {

    private final QuotationService quotationService;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> createQuotation(@RequestBody CreateQuotationDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(quotationService.createQuotation(dto, getCurrentUserEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get all quotations across opportunities and companies.
     * Supports both paginated and flat list modes.
     */
    @GetMapping({"", "/all", "/list"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getAllQuotations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean paged) {
        if (Boolean.TRUE.equals(paged)) {
            return ResponseEntity.ok(quotationService.getAllQuotationsPaged(page, size));
        }
        return ResponseEntity.ok(quotationService.getAllQuotations());
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getQuotation(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(quotationService.getQuotationById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping("/{id:[0-9]+}/send")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> markAsSent(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(quotationService.markAsSent(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id:[0-9]+}/accept")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> markAsAccepted(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(quotationService.markAsAccepted(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id:[0-9]+}/reject")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> markAsRejected(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(quotationService.markAsRejected(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/opportunity/{opportunityId:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<QuotationResponse>> getQuotationsForOpportunity(@PathVariable Long opportunityId) {
        return ResponseEntity.ok(quotationService.getQuotationsForOpportunity(opportunityId));
    }

    @GetMapping("/company/{companyId:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<QuotationResponse>> getQuotationsForCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(quotationService.getQuotationsForCompany(companyId));
    }

    @PostMapping("/{id:[0-9]+}/convert-to-order")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> convertToOrder(@PathVariable Long id) {
        try {
            Long orderId = quotationService.convertToOrder(id, getCurrentUserEmail());
            return ResponseEntity.ok("Quotation converted to Order ID: " + orderId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
