package com.bentork.ev_system.repository.followup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bentork.ev_system.model.followup.LeadFollowUpRound;

@Repository
public interface LeadFollowUpRoundRepository extends JpaRepository<LeadFollowUpRound, Long> {

    List<LeadFollowUpRound> findByCampaignIdOrderByRoundNumberAsc(Long campaignId);

    Optional<LeadFollowUpRound> findByCampaignIdAndRoundNumber(Long campaignId, Integer roundNumber);

    long countByCampaignId(Long campaignId);
}

