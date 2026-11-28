package com.bentork.ev_system.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bentork.ev_system.exception.DuplicateLeadException;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.SalesContact;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.SalesContactRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuplicateMatchingServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private SalesContactRepository salesContactRepository;

    private DuplicateMatchingService matchingService;

    @BeforeEach
    void setUp() {
        matchingService = new DuplicateMatchingService(leadRepository, salesContactRepository);
    }

    @Test
    void testMatchByDirectLeadPhone() {
        Lead lead = new Lead();
        lead.setId(101L);
        lead.setLeadNumber("LD-101");
        lead.setPhone("919876543210");

        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc("919876543210"))
                .thenReturn(Optional.of(lead));

        DuplicateMatchingService.MatchResult result = matchingService.matchByPhone("9876543210");

        assertTrue(result.isMatched());
        assertEquals("DIRECT_LEAD_PHONE", result.getMatchType());
        assertNotNull(result.getMatchedLead());
        assertEquals(101L, result.getMatchedLead().getId());
    }

    @Test
    void testMatchByContactPhone() {
        Lead lead = new Lead();
        lead.setId(202L);
        SalesContact contact = new SalesContact();
        contact.setId(505L);
        contact.setPhone("919876543210");
        lead.setContact(contact);

        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc("919876543210"))
                .thenReturn(Optional.empty());
        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc("9876543210"))
                .thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneOrderByCreatedAtDesc("919876543210"))
                .thenReturn(Optional.of(lead));

        DuplicateMatchingService.MatchResult result = matchingService.matchByPhone("9876543210");

        assertTrue(result.isMatched());
        assertEquals("CONTACT_PHONE", result.getMatchType());
        assertEquals(202L, result.getMatchedLead().getId());
    }

    @Test
    void testNoMatchReturnsFalse() {
        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc("919876543210")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByPhoneOrderByCreatedAtDesc("9876543210")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneOrderByCreatedAtDesc("919876543210")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneOrderByCreatedAtDesc("9876543210")).thenReturn(Optional.empty());
        when(salesContactRepository.findByPhone("919876543210")).thenReturn(Optional.empty());

        DuplicateMatchingService.MatchResult result = matchingService.matchByPhone("9876543210");

        assertFalse(result.isMatched());
        assertEquals("NONE", result.getMatchType());
        assertNull(result.getMatchedLead());
    }

    @Test
    void testMatchByEmail_DirectLeadAndContactEmail() {
        Lead lead = new Lead();
        lead.setId(303L);
        lead.setLeadNumber("LD-303");
        lead.setEmail("fleet@bentork.com");

        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("fleet@bentork.com"))
                .thenReturn(Optional.of(lead));

        DuplicateMatchingService.MatchResult result = matchingService.matchByEmail("  Fleet@Bentork.com ");

        assertTrue(result.isMatched());
        assertEquals("DIRECT_LEAD_EMAIL", result.getMatchType());
        assertEquals(303L, result.getMatchedLead().getId());
    }

    @Test
    void testCheckDuplicateForNewLead_ThrowsForDuplicatePhone() {
        Lead existing = new Lead();
        existing.setId(10L);
        existing.setLeadNumber("LD-20261128-0010");

        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection()))
                .thenReturn(Optional.of(existing));

        DuplicateLeadException ex = assertThrows(DuplicateLeadException.class,
                () -> matchingService.checkDuplicateForNewLead("+91 98765 43210", "new@company.com", null));

        assertEquals("phone", ex.getDuplicateField());
        assertEquals(10L, ex.getExistingLeadId());
        assertEquals("LD-20261128-0010", ex.getExistingLeadNumber());
    }

    @Test
    void testCheckDuplicateForNewLead_ThrowsForDuplicateEmail() {
        Lead existing = new Lead();
        existing.setId(20L);
        existing.setLeadNumber("LD-20261128-0020");

        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(salesContactRepository.findFirstByPhoneIn(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("dup@example.com"))
                .thenReturn(Optional.of(existing));

        DuplicateLeadException ex = assertThrows(DuplicateLeadException.class,
                () -> matchingService.checkDuplicateForNewLead("9988776655", "DUP@Example.com", null));

        assertEquals("email", ex.getDuplicateField());
        assertEquals(20L, ex.getExistingLeadId());
    }

    @Test
    void testCheckDuplicateForNewLead_AllowsStandaloneContactOnFirstLead() {
        SalesContact standalone = new SalesContact();
        standalone.setId(77L);
        standalone.setPhone("919876543210");
        standalone.setEmail("standalone@example.com");

        when(leadRepository.findFirstByContactIdOrderByCreatedAtDesc(77L)).thenReturn(Optional.empty());
        when(leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(anyCollection())).thenReturn(Optional.empty());
        when(salesContactRepository.findFirstByPhoneIn(anyCollection())).thenReturn(Optional.of(standalone));
        when(leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc("standalone@example.com")).thenReturn(Optional.empty());
        when(leadRepository.findFirstByContactEmailIgnoreCaseOrderByCreatedAtDesc("standalone@example.com")).thenReturn(Optional.empty());
        when(salesContactRepository.findByEmailIgnoreCase("standalone@example.com")).thenReturn(Optional.of(standalone));

        assertDoesNotThrow(() ->
                matchingService.checkDuplicateForNewLead("9876543210", "standalone@example.com", 77L));
    }
}
