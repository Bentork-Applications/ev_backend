package com.bentork.ev_system.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.exception.DuplicateLeadException;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.SalesContact;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.SalesContactRepository;
import com.bentork.ev_system.util.PhoneNormalizationUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for matching imported contacts against existing canonical Leads in CRM.
 * Implements FR-05 (Duplicate matching) and BR-01 (Single canonical lead record).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateMatchingService {

    private final LeadRepository leadRepository;
    private final SalesContactRepository salesContactRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResult {
        private boolean matched;
        private String matchType; // "DIRECT_LEAD_PHONE", "CONTACT_PHONE", "NONE"
        private Lead matchedLead;
        private SalesContact matchedContact;
        private String normalizedPhone;
    }

    /**
     * Finds any existing Lead that corresponds to the given phone number.
     * Checks:
     * 1. Direct phone on Lead (both normalized E.164 and 10-digit variant)
     * 2. Phone on associated SalesContact
     */
    @Transactional(readOnly = true)
    public MatchResult matchByPhone(String rawPhone) {
        String normalized = PhoneNormalizationUtil.normalize(rawPhone);
        if (normalized == null || normalized.isEmpty()) {
            return MatchResult.builder()
                    .matched(false)
                    .matchType("NONE")
                    .normalizedPhone(null)
                    .build();
        }

        // 1. Check direct lead phone (normalized)
        Optional<Lead> leadOpt = leadRepository.findFirstByPhoneOrderByCreatedAtDesc(normalized);
        if (leadOpt.isPresent()) {
            return MatchResult.builder()
                    .matched(true)
                    .matchType("DIRECT_LEAD_PHONE")
                    .matchedLead(leadOpt.get())
                    .normalizedPhone(normalized)
                    .build();
        }

        // If normalized starts with 91 and has 12 digits, also check 10-digit variant
        if (normalized.startsWith("91") && normalized.length() == 12) {
            String tenDigit = normalized.substring(2);
            leadOpt = leadRepository.findFirstByPhoneOrderByCreatedAtDesc(tenDigit);
            if (leadOpt.isPresent()) {
                return MatchResult.builder()
                        .matched(true)
                        .matchType("DIRECT_LEAD_PHONE")
                        .matchedLead(leadOpt.get())
                        .normalizedPhone(normalized)
                        .build();
            }
        }

        // 2. Check associated SalesContact
        leadOpt = leadRepository.findFirstByContactPhoneOrderByCreatedAtDesc(normalized);
        if (leadOpt.isPresent()) {
            return MatchResult.builder()
                    .matched(true)
                    .matchType("CONTACT_PHONE")
                    .matchedLead(leadOpt.get())
                    .matchedContact(leadOpt.get().getContact())
                    .normalizedPhone(normalized)
                    .build();
        }

        if (normalized.startsWith("91") && normalized.length() == 12) {
            String tenDigit = normalized.substring(2);
            leadOpt = leadRepository.findFirstByContactPhoneOrderByCreatedAtDesc(tenDigit);
            if (leadOpt.isPresent()) {
                return MatchResult.builder()
                        .matched(true)
                        .matchType("CONTACT_PHONE")
                        .matchedLead(leadOpt.get())
                        .matchedContact(leadOpt.get().getContact())
                        .normalizedPhone(normalized)
                        .build();
            }
        }

        // 3. Check standalone SalesContact that might not have a Lead yet
        Optional<SalesContact> contactOpt = salesContactRepository.findByPhone(normalized);
        if (contactOpt.isPresent()) {
            return MatchResult.builder()
                    .matched(true)
                    .matchType("SALES_CONTACT_ONLY")
                    .matchedContact(contactOpt.get())
                    .normalizedPhone(normalized)
                    .build();
        }

        return MatchResult.builder()
                .matched(false)
                .matchType("NONE")
                .normalizedPhone(normalized)
                .build();
    }

    /**
     * Finds any existing Lead or SalesContact matching the given email (case-insensitive).
     */
    @Transactional(readOnly = true)
    public MatchResult matchByEmail(String rawEmail) {
        String normalizedEmail = normalizeEmail(rawEmail);
        if (normalizedEmail == null) {
            return MatchResult.builder()
                    .matched(false)
                    .matchType("NONE")
                    .build();
        }

        Optional<Lead> leadOpt = leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalizedEmail);
        if (leadOpt.isPresent()) {
            return MatchResult.builder()
                    .matched(true)
                    .matchType("DIRECT_LEAD_EMAIL")
                    .matchedLead(leadOpt.get())
                    .build();
        }

        leadOpt = leadRepository.findFirstByContactEmailIgnoreCaseOrderByCreatedAtDesc(normalizedEmail);
        if (leadOpt.isPresent()) {
            return MatchResult.builder()
                    .matched(true)
                    .matchType("CONTACT_EMAIL")
                    .matchedLead(leadOpt.get())
                    .matchedContact(leadOpt.get().getContact())
                    .build();
        }

        Optional<SalesContact> contactOpt = salesContactRepository.findByEmailIgnoreCase(normalizedEmail);
        if (contactOpt.isPresent()) {
            return MatchResult.builder()
                    .matched(true)
                    .matchType("SALES_CONTACT_EMAIL_ONLY")
                    .matchedContact(contactOpt.get())
                    .build();
        }

        return MatchResult.builder()
                .matched(false)
                .matchType("NONE")
                .build();
    }

    /**
     * Finds any existing Lead or SalesContact matching either phone or email.
     */
    @Transactional(readOnly = true)
    public MatchResult matchByPhoneOrEmail(String rawPhone, String rawEmail) {
        MatchResult phoneMatch = matchByPhone(rawPhone);
        if (phoneMatch.isMatched()) {
            return phoneMatch;
        }
        Set<String> variants = buildPhoneVariants(rawPhone);
        if (!variants.isEmpty()) {
            Optional<Lead> leadByVar = leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(variants);
            if (leadByVar.isPresent()) {
                return MatchResult.builder()
                        .matched(true)
                        .matchType("DIRECT_LEAD_PHONE")
                        .matchedLead(leadByVar.get())
                        .normalizedPhone(PhoneNormalizationUtil.normalize(rawPhone))
                        .build();
            }
            leadByVar = leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(variants);
            if (leadByVar.isPresent()) {
                return MatchResult.builder()
                        .matched(true)
                        .matchType("CONTACT_PHONE")
                        .matchedLead(leadByVar.get())
                        .matchedContact(leadByVar.get().getContact())
                        .normalizedPhone(PhoneNormalizationUtil.normalize(rawPhone))
                        .build();
            }
            Optional<SalesContact> contactByVar = salesContactRepository.findFirstByPhoneIn(variants);
            if (contactByVar.isPresent()) {
                return MatchResult.builder()
                        .matched(true)
                        .matchType("SALES_CONTACT_ONLY")
                        .matchedContact(contactByVar.get())
                        .normalizedPhone(PhoneNormalizationUtil.normalize(rawPhone))
                        .build();
            }
        }
        return matchByEmail(rawEmail);
    }

    /**
     * Validates that neither the mobile number nor the email already exists on an existing Lead
     * (or another SalesContact) before saving a new Lead.
     *
     * @param rawPhone raw mobile number input (nullable)
     * @param rawEmail raw email input (nullable)
     * @param attachingContactId optional existing SalesContact ID being attached via createLead
     * @throws DuplicateLeadException (mapped to HTTP 409 Conflict) if a duplicate is found
     */
    @Transactional(readOnly = true)
    public void checkDuplicateForNewLead(String rawPhone, String rawEmail, Long attachingContactId) {
        if (attachingContactId != null) {
            Optional<Lead> existingForContact = leadRepository.findFirstByContactIdOrderByCreatedAtDesc(attachingContactId);
            if (existingForContact.isPresent()) {
                Lead existing = existingForContact.get();
                throw new DuplicateLeadException(
                        "A lead already exists for this contact (" + existing.getLeadNumber() + ")",
                        "phone",
                        existing.getId(),
                        existing.getLeadNumber());
            }
        }

        Lead matchedPhoneLead = null;
        SalesContact matchedPhoneContact = null;
        Set<String> phoneVariants = buildPhoneVariants(rawPhone);
        if (!phoneVariants.isEmpty()) {
            matchedPhoneLead = leadRepository.findFirstByPhoneInOrderByCreatedAtDesc(phoneVariants).orElse(null);
            if (matchedPhoneLead == null) {
                matchedPhoneLead = leadRepository.findFirstByContactPhoneInOrderByCreatedAtDesc(phoneVariants).orElse(null);
            }
            if (matchedPhoneLead == null) {
                SalesContact contact = salesContactRepository.findFirstByPhoneIn(phoneVariants).orElse(null);
                if (contact != null && (attachingContactId == null || !contact.getId().equals(attachingContactId))) {
                    matchedPhoneContact = contact;
                }
            }
        }

        Lead matchedEmailLead = null;
        SalesContact matchedEmailContact = null;
        String normalizedEmail = normalizeEmail(rawEmail);
        if (normalizedEmail != null) {
            matchedEmailLead = leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(normalizedEmail).orElse(null);
            if (matchedEmailLead == null) {
                matchedEmailLead = leadRepository.findFirstByContactEmailIgnoreCaseOrderByCreatedAtDesc(normalizedEmail).orElse(null);
            }
            if (matchedEmailLead == null) {
                SalesContact contact = salesContactRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
                if (contact != null && (attachingContactId == null || !contact.getId().equals(attachingContactId))) {
                    matchedEmailContact = contact;
                }
            }
        }

        boolean phoneDuplicate = matchedPhoneLead != null || matchedPhoneContact != null;
        boolean emailDuplicate = matchedEmailLead != null || matchedEmailContact != null;

        if (phoneDuplicate && emailDuplicate) {
            Lead ref = matchedPhoneLead != null ? matchedPhoneLead : matchedEmailLead;
            throw new DuplicateLeadException(
                    "Lead with mobile number '" + rawPhone.trim() + "' and email '" + normalizedEmail + "' already exists",
                    "phone_and_email",
                    ref != null ? ref.getId() : null,
                    ref != null ? ref.getLeadNumber() : null);
        }

        if (phoneDuplicate) {
            throw new DuplicateLeadException(
                    "Lead with mobile number '" + rawPhone.trim() + "' already exists",
                    "phone",
                    matchedPhoneLead != null ? matchedPhoneLead.getId() : null,
                    matchedPhoneLead != null ? matchedPhoneLead.getLeadNumber() : null);
        }

        if (emailDuplicate) {
            throw new DuplicateLeadException(
                    "Lead with email '" + normalizedEmail + "' already exists",
                    "email",
                    matchedEmailLead != null ? matchedEmailLead.getId() : null,
                    matchedEmailLead != null ? matchedEmailLead.getLeadNumber() : null);
        }
    }

    public static String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }
        String trimmed = rawEmail.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizePhoneOrRaw(String rawPhone) {
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            return null;
        }
        String normalized = PhoneNormalizationUtil.normalize(rawPhone);
        return normalized != null ? normalized : rawPhone.trim();
    }

    public static Set<String> buildPhoneVariants(String rawPhone) {
        Set<String> variants = new LinkedHashSet<>();
        if (rawPhone == null || rawPhone.trim().isEmpty()) {
            return variants;
        }
        String trimmed = rawPhone.trim();
        variants.add(trimmed);
        String normalized = PhoneNormalizationUtil.normalize(trimmed);
        if (normalized != null && !normalized.isEmpty()) {
            variants.add(normalized);
            variants.add("+" + normalized);
            if (normalized.startsWith("91") && normalized.length() == 12) {
                String tenDigit = normalized.substring(2);
                variants.add(tenDigit);
                variants.add("0" + tenDigit);
                variants.add("+91 " + tenDigit);
                variants.add("+91-" + tenDigit);
            }
        }
        return variants;
    }
}
