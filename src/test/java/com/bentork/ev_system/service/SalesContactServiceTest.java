package com.bentork.ev_system.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bentork.ev_system.dto.request.CreateContactDTO;
import com.bentork.ev_system.dto.request.UpdateContactDTO;
import com.bentork.ev_system.dto.response.ContactResponse;
import com.bentork.ev_system.model.SalesCompany;
import com.bentork.ev_system.model.SalesContact;
import com.bentork.ev_system.repository.ActivityRepository;
import com.bentork.ev_system.repository.CampaignRecipientRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.OpportunityRepository;
import com.bentork.ev_system.repository.SalesContactRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesContactServiceTest {

    @Mock
    private SalesContactRepository contactRepository;

    @Mock
    private SalesCompanyService companyService;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private OpportunityRepository opportunityRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private CampaignRecipientRepository campaignRecipientRepository;

    @InjectMocks
    private SalesContactService contactService;

    private SalesContact existingContact;
    private SalesCompany company;

    @BeforeEach
    void setUp() {
        company = new SalesCompany();
        company.setId(10L);
        company.setName("Acme Corp");

        existingContact = new SalesContact();
        existingContact.setId(1L);
        existingContact.setName("John Doe");
        existingContact.setDesignation("Director");
        existingContact.setPhone("9876543210");
        existingContact.setEmail("john@example.com");
        existingContact.setPrimary(false);
        existingContact.setCompany(company);
    }

    @Test
    void testGetContactById_Success() {
        when(contactRepository.findById(1L)).thenReturn(Optional.of(existingContact));

        ContactResponse response = contactService.getContactById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("9876543210", response.getPhone());
        assertEquals("Acme Corp", response.getCompanyName());
    }

    @Test
    void testGetContactById_NotFound() {
        when(contactRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> contactService.getContactById(99L));
    }

    @Test
    void testUpdateContact_Success() {
        when(contactRepository.findById(1L)).thenReturn(Optional.of(existingContact));
        when(contactRepository.findByPhone("9876543211")).thenReturn(Optional.empty());
        when(contactRepository.save(any(SalesContact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateContactDTO dto = new UpdateContactDTO();
        dto.setName("John Updated");
        dto.setPhone("9876543211");
        dto.setDesignation("VP Operations");
        dto.setEmail("john.new@example.com");
        dto.setIsPrimary(true);

        when(contactRepository.findByCompanyIdOrderByIsPrimaryDescNameAsc(10L))
                .thenReturn(Collections.singletonList(existingContact));

        ContactResponse response = contactService.updateContact(1L, dto);

        assertNotNull(response);
        assertEquals("John Updated", response.getName());
        assertEquals("9876543211", response.getPhone());
        assertEquals("VP Operations", response.getDesignation());
        assertEquals("john.new@example.com", response.getEmail());
        assertTrue(response.isPrimary());
        verify(contactRepository).save(existingContact);
    }

    @Test
    void testUpdateContact_DuplicatePhoneThrowsException() {
        SalesContact otherContact = new SalesContact();
        otherContact.setId(2L);
        otherContact.setPhone("9999999999");

        when(contactRepository.findById(1L)).thenReturn(Optional.of(existingContact));
        when(contactRepository.findByPhone("9999999999")).thenReturn(Optional.of(otherContact));

        UpdateContactDTO dto = new UpdateContactDTO();
        dto.setPhone("9999999999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> contactService.updateContact(1L, dto));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(contactRepository, never()).save(any());
    }

    @Test
    void testUpdateContact_ChangeCompany() {
        SalesCompany newCompany = new SalesCompany();
        newCompany.setId(20L);
        newCompany.setName("Beta Industries");

        when(contactRepository.findById(1L)).thenReturn(Optional.of(existingContact));
        when(companyService.findById(20L)).thenReturn(newCompany);
        when(contactRepository.save(any(SalesContact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateContactDTO dto = new UpdateContactDTO();
        dto.setCompanyId(20L);

        ContactResponse response = contactService.updateContact(1L, dto);

        assertNotNull(response);
        assertEquals(20L, response.getCompanyId());
        assertEquals("Beta Industries", response.getCompanyName());
    }

    @Test
    void testDeleteContact_SuccessWithCascadeNullification() {
        when(contactRepository.findById(1L)).thenReturn(Optional.of(existingContact));

        contactService.deleteContact(1L);

        verify(leadRepository).nullifyContact(1L);
        verify(opportunityRepository).nullifyContact(1L);
        verify(activityRepository).nullifyContact(1L);
        verify(campaignRecipientRepository).nullifyContact(1L);
        verify(contactRepository).delete(existingContact);
    }

    @Test
    void testDeleteContact_NotFoundThrowsException() {
        when(contactRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> contactService.deleteContact(99L));
        verify(contactRepository, never()).delete(any());
    }
}

