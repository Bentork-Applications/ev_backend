package com.bentork.ev_system.repository.followup;

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
import com.bentork.ev_system.model.followup.LeadFollowUpRoundLead;

@Repository
public interface LeadFollowUpRoundLeadRepository extends JpaRepository<LeadFollowUpRoundLead, Long> {

    List<LeadFollowUpRoundLead> findByRoundId(Long roundId);

    List<LeadFollowUpRoundLead> findByRoundIdAndSelectionStatus(Long roundId, String selectionStatus);

    List<LeadFollowUpRoundLead> findByRoundIdAndDeliveryStatus(Long roundId, String deliveryStatus);

    Optional<LeadFollowUpRoundLead> findByRoundIdAndLeadId(Long roundId, Long leadId);

    boolean existsByRoundIdAndLeadIdAndSelectionStatus(Long roundId, Long leadId, String selectionStatus);

    @Query("SELECT rl.lead.id FROM LeadFollowUpRoundLead rl WHERE rl.round.id = :roundId AND rl.selectionStatus = 'SELECTED'")
    List<Long> findSelectedLeadIdsByRoundId(@Param("roundId") Long roundId);

    long countByRoundIdAndSelectionStatus(Long roundId, String selectionStatus);

    @Modifying
    @Query("DELETE FROM LeadFollowUpRoundLead rl WHERE rl.round.id = :roundId")
    void deleteByRoundId(@Param("roundId") Long roundId);

    // Query leads from the parent round that were SELECTED
    @Query("SELECT rl.lead FROM LeadFollowUpRoundLead rl WHERE rl.round.id = :parentRoundId AND rl.selectionStatus = 'SELECTED' " +
           "AND (:search IS NULL OR LOWER(rl.lead.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(rl.lead.phone) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(rl.lead.city) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(rl.lead.state) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR rl.lead.status = :status) " +
           "AND (:city IS NULL OR LOWER(rl.lead.city) = LOWER(:city)) " +
           "AND (:state IS NULL OR LOWER(rl.lead.state) = LOWER(:state))")
    Page<Lead> findSelectedLeadsFromParentRoundWithFilter(
            @Param("parentRoundId") Long parentRoundId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("city") String city,
            @Param("state") String state,
            Pageable pageable);
}

