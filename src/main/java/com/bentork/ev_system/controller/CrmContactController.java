package com.bentork.ev_system.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.dto.request.CreateContactDTO;
import com.bentork.ev_system.dto.request.UpdateContactDTO;
import com.bentork.ev_system.dto.response.ContactResponse;
import com.bentork.ev_system.service.SalesContactService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/crm/contacts")
@RequiredArgsConstructor
@Slf4j
public class CrmContactController {

    private final SalesContactService contactService;

    @PostMapping({"", "/create"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> createContact(@RequestBody CreateContactDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(contactService.createContact(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Get all contacts across companies and standalone contacts.
     * Supports pagination, company filter, and query search.
     */
    @GetMapping({"", "/all", "/list"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getAllContacts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean paged,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String search) {
        if (companyId != null) {
            return ResponseEntity.ok(contactService.getContactsByCompany(companyId));
        }
        if (search != null && !search.trim().isEmpty()) {
            return ResponseEntity.ok(contactService.searchContacts(search.trim()));
        }
        if (Boolean.TRUE.equals(paged)) {
            return ResponseEntity.ok(contactService.getAllContactsPaged(page, size));
        }
        return ResponseEntity.ok(contactService.getAllContacts());
    }

    @GetMapping("/company/{companyId:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<List<ContactResponse>> getContactsByCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(contactService.getContactsByCompany(companyId));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> searchByPhone(@RequestParam String phone) {
        ContactResponse contact = contactService.searchByPhone(phone);
        if (contact == null) {
            return ResponseEntity.ok(java.util.Collections.emptyMap());
        }
        return ResponseEntity.ok(contact);
    }

    @GetMapping("/{id:[0-9]+}")
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getContactById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(contactService.getContactById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PutMapping({"/{id:[0-9]+}", "/{id:[0-9]+}/update", "/update/{id:[0-9]+}"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateContact(@PathVariable Long id, @RequestBody UpdateContactDTO dto) {
        try {
            return ResponseEntity.ok(contactService.updateContact(id, dto));
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping({"/{id:[0-9]+}", "/delete/{id:[0-9]+}"})
    @PreAuthorize("hasAnyAuthority('SALES_ADMIN', 'ADMIN')")
    public ResponseEntity<?> deleteContact(@PathVariable Long id) {
        try {
            contactService.deleteContact(id);
            return ResponseEntity.ok("Contact deleted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    private String getCurrentUserEmail() {
        return org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
    }
}
