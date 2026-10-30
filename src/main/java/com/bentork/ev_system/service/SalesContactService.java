package com.bentork.ev_system.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesContactService {

    private final SalesContactRepository contactRepository;
    private final SalesCompanyService companyService;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final ActivityRepository activityRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;

    @Transactional
    public ContactResponse createContact(CreateContactDTO dto) {
        if (contactRepository.existsByPhone(dto.getPhone())) {
            throw new IllegalArgumentException("Contact with phone '" + dto.getPhone() + "' already exists");
        }

        SalesContact contact = new SalesContact();
        contact.setName(dto.getName());
        contact.setDesignation(dto.getDesignation());
        contact.setPhone(dto.getPhone());
        contact.setEmail(dto.getEmail());
        contact.setPrimary(dto.isPrimary());

        if (dto.getCompanyId() != null) {
            SalesCompany company = companyService.findById(dto.getCompanyId());
            contact.setCompany(company);
        }

        if (contact.isPrimary() && contact.getCompany() != null) {
            List<SalesContact> companyContacts = contactRepository
                    .findByCompanyIdOrderByIsPrimaryDescNameAsc(contact.getCompany().getId());
            for (SalesContact other : companyContacts) {
                if (other.isPrimary()) {
                    other.setPrimary(false);
                    contactRepository.save(other);
                }
            }
        }

        SalesContact saved = contactRepository.save(contact);
        log.info("SalesContact '{}' created with phone {}", saved.getName(), saved.getPhone());
        return mapToResponse(saved);
    }

    public List<ContactResponse> getContactsByCompany(Long companyId) {
        return contactRepository.findByCompanyIdOrderByIsPrimaryDescNameAsc(companyId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ContactResponse> getAllContacts() {
        return contactRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ContactResponse searchByPhone(String phone) {
        SalesContact contact = contactRepository.findByPhone(phone)
                .orElse(null);
        return contact != null ? mapToResponse(contact) : null;
    }

    public List<ContactResponse> searchContacts(String query) {
        return contactRepository.findByNameContainingIgnoreCase(query).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SalesContact findById(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found with ID: " + id));
    }

    public ContactResponse getContactById(Long id) {
        return mapToResponse(findById(id));
    }

    @Transactional
    public ContactResponse updateContact(Long id, UpdateContactDTO dto) {
        SalesContact contact = findById(id);

        if (dto.getPhone() != null && !dto.getPhone().trim().isEmpty()) {
            String newPhone = dto.getPhone().trim();
            if (!newPhone.equals(contact.getPhone())) {
                contactRepository.findByPhone(newPhone).ifPresent(existing -> {
                    if (!existing.getId().equals(contact.getId())) {
                        throw new IllegalArgumentException("Contact with phone '" + newPhone + "' already exists");
                    }
                });
                contact.setPhone(newPhone);
            }
        }

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            contact.setName(dto.getName().trim());
        }

        if (dto.getDesignation() != null) {
            String designation = dto.getDesignation().trim();
            contact.setDesignation(designation.isEmpty() ? null : designation);
        }

        if (dto.getEmail() != null) {
            String email = dto.getEmail().trim();
            contact.setEmail(email.isEmpty() ? null : email);
        }

        if (dto.getCompanyId() != null) {
            if (dto.getCompanyId() <= 0) {
                contact.setCompany(null);
            } else {
                SalesCompany company = companyService.findById(dto.getCompanyId());
                contact.setCompany(company);
            }
        }

        if (dto.getIsPrimary() != null) {
            contact.setPrimary(dto.getIsPrimary());
        }

        if (contact.isPrimary() && contact.getCompany() != null) {
            List<SalesContact> companyContacts = contactRepository
                    .findByCompanyIdOrderByIsPrimaryDescNameAsc(contact.getCompany().getId());
            for (SalesContact other : companyContacts) {
                if (!other.getId().equals(contact.getId()) && other.isPrimary()) {
                    other.setPrimary(false);
                    contactRepository.save(other);
                }
            }
        }

        SalesContact saved = contactRepository.save(contact);
        log.info("SalesContact with ID {} updated successfully", saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public ContactResponse updateContact(Long id, CreateContactDTO dto) {
        UpdateContactDTO updateDto = new UpdateContactDTO();
        updateDto.setCompanyId(dto.getCompanyId());
        updateDto.setName(dto.getName());
        updateDto.setDesignation(dto.getDesignation());
        updateDto.setPhone(dto.getPhone());
        updateDto.setEmail(dto.getEmail());
        updateDto.setIsPrimary(dto.isPrimary());
        return updateContact(id, updateDto);
    }

    @Transactional
    public void deleteContact(Long id) {
        SalesContact contact = findById(id);

        // Nullify foreign key references in dependent entities to prevent constraint violations
        leadRepository.nullifyContact(id);
        opportunityRepository.nullifyContact(id);
        activityRepository.nullifyContact(id);
        campaignRecipientRepository.nullifyContact(id);

        contactRepository.delete(contact);
        log.info("SalesContact '{}' (ID: {}) deleted successfully", contact.getName(), id);
    }

    // ==================== HELPERS ====================

    public ContactResponse mapToResponse(SalesContact contact) {
        ContactResponse response = new ContactResponse();
        response.setId(contact.getId());
        response.setName(contact.getName());
        response.setDesignation(contact.getDesignation());
        response.setPhone(contact.getPhone());
        response.setEmail(contact.getEmail());
        response.setPrimary(contact.isPrimary());
        response.setCreatedAt(contact.getCreatedAt());

        if (contact.getCompany() != null) {
            response.setCompanyId(contact.getCompany().getId());
            response.setCompanyName(contact.getCompany().getName());
        }

        return response;
    }
}
