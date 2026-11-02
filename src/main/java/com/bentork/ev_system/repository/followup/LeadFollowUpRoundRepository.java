package com.bentork.ev_system.repository.followup;

import java.time.LocalDate;
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

    List<LeadFollowUpRound> findByStatusAndPlannedDateLessThanEqual(String status, LocalDate date);

    List<LeadFollowUpRound> findByPlannedDateBetweenAndStatusInOrderByPlannedDateAsc(
            LocalDate startDate, LocalDate endDate, List<String> statuses);

    List<LeadFollowUpRound> findByPlannedDateBeforeAndStatusNotInOrderByPlannedDateAsc(
            LocalDate date, List<String> statuses);
}

