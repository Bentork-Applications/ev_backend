package com.bentork.ev_system.repository.followup;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;

@Repository
public interface LeadFollowUpCampaignRepository extends JpaRepository<LeadFollowUpCampaign, Long> {

    Optional<LeadFollowUpCampaign> findByBatchId(Long batchId);

    Page<LeadFollowUpCampaign> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<LeadFollowUpCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

