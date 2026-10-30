package com.bentork.ev_system.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.Lead;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByOwnerAdminIdOrderByCreatedAtDesc(Long adminId);

    List<Lead> findByStatusOrderByCreatedAtDesc(String status);

    List<Lead> findBySourceOrderByCreatedAtDesc(String source);

    List<Lead> findAllByOrderByCreatedAtDesc();

    Optional<Lead> findByIndiaMartLeadId(String indiaMartLeadId);

    Optional<Lead> findByLeadNumber(String leadNumber);

    List<Lead> findByPhone(String phone);

    Optional<Lead> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    List<Lead> findByContactPhone(String phone);

    Optional<Lead> findFirstByContactPhoneOrderByCreatedAtDesc(String phone);

    List<Lead> findByContactPhoneContaining(String phone);

    List<Lead> findByTitleContainingIgnoreCaseOrNotesContainingIgnoreCase(String title, String notes);

    // Follow-up queries
    List<Lead> findByNextFollowUpDateBetween(LocalDate start, LocalDate end);

    List<Lead> findByNextFollowUpDateBeforeAndStatusNotIn(LocalDate date, List<String> excludedStatuses);

    List<Lead> findByNextFollowUpDateAndOwnerAdminId(LocalDate date, Long adminId);

    // Dashboard & Analytics queries
    long countByStatus(String status);

    long countBySource(String source);

    long countByOwnerAdminId(Long adminId);

    long countByOwnerAdminIdAndStatus(Long adminId, String status);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);

    long countByOwnerAdminIdAndCreatedAtBetween(Long adminId, LocalDateTime start, LocalDateTime end);

    long countByOwnerAdminIdAndStatusAndCreatedAtBetween(Long adminId, String status, LocalDateTime start, LocalDateTime end);

    List<Lead> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Lead> findByOwnerAdminIdAndCreatedAtBetween(Long adminId, LocalDateTime start, LocalDateTime end);

    List<Lead> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Lead> findByContactIdOrderByCreatedAtDesc(Long contactId);

    // ==================== PAGINATED QUERIES ====================

    Page<Lead> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Lead> findByOwnerAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);

    Page<Lead> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Lead> findBySourceOrderByCreatedAtDesc(String source, Pageable pageable);

    // Tag-based queries
    List<Lead> findByTagsContainingIgnoreCase(String tag);

    @Modifying
    @Query("UPDATE Lead l SET l.contact = null WHERE l.contact.id = :contactId")
    void nullifyContact(@Param("contactId") Long contactId);
}

