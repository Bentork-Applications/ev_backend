package com.bentork.ev_system.repository.followup;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.followup.LeadImportBatch;

@Repository
public interface LeadImportBatchRepository extends JpaRepository<LeadImportBatch, Long> {

    Optional<LeadImportBatch> findByBatchNumber(String batchNumber);

    boolean existsByFileChecksum(String fileChecksum);
}

