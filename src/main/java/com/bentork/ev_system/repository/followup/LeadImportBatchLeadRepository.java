package com.bentork.ev_system.repository.followup;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.followup.LeadImportBatchLead;

@Repository
public interface LeadImportBatchLeadRepository extends JpaRepository<LeadImportBatchLead, Long> {

    List<LeadImportBatchLead> findByBatchId(Long batchId);

    long countByBatchId(Long batchId);

    boolean existsByBatchIdAndLeadId(Long batchId, Long leadId);

    @Query("SELECT ibl.lead.id FROM LeadImportBatchLead ibl WHERE ibl.batch.id = :batchId")
    List<Long> findLeadIdsByBatchId(@Param("batchId") Long batchId);

    @Query("SELECT ibl.lead FROM LeadImportBatchLead ibl WHERE ibl.batch.id = :batchId")
    Page<Lead> findLeadsByBatchId(@Param("batchId") Long batchId, Pageable pageable);

    @Query("SELECT ibl.lead FROM LeadImportBatchLead ibl WHERE ibl.batch.id = :batchId " +
           "AND (:search IS NULL OR LOWER(ibl.lead.title) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(ibl.lead.phone) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(ibl.lead.city) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(ibl.lead.state) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR ibl.lead.status = :status) " +
           "AND (:city IS NULL OR LOWER(ibl.lead.city) = LOWER(:city)) " +
           "AND (:state IS NULL OR LOWER(ibl.lead.state) = LOWER(:state))")
    Page<Lead> findLeadsByBatchIdWithFilter(
            @Param("batchId") Long batchId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("city") String city,
            @Param("state") String state,
            Pageable pageable);
}

