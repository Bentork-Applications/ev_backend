package com.bentork.ev_system.service.followup;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.bentork.ev_system.dto.followup.FollowUpSelectLeadsDTO;
import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Background scheduler for automatic dispatch of due sequential follow-up rounds
 * and tracking of operational reminders.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadFollowUpRoundScheduler {

    private final LeadFollowUpRoundRepository roundRepository;
    private final FollowUpMessageDispatchService dispatchService;
    private final FollowUpSelectionService selectionService;

    /**
     * Runs every 5 minutes to trigger due automatic follow-up rounds.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void executeDueAutomaticRounds() {
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        List<LeadFollowUpRound> dueRounds = roundRepository.findByStatusAndPlannedDateLessThanEqual("SCHEDULED", today);

        for (LeadFollowUpRound round : dueRounds) {
            LeadFollowUpCampaign campaign = round.getCampaign();

            // Only dispatch if campaign is ACTIVE and round is configured for AUTOMATIC dispatch
            if (campaign == null || !"ACTIVE".equalsIgnoreCase(campaign.getStatus())) {
                continue;
            }

            if (!"AUTOMATIC".equalsIgnoreCase(round.getSendMode())) {
                continue; // MANUAL rounds wait for admin review and explicit trigger
            }

            // Check planned time if planned for today
            if (today.equals(round.getPlannedDate()) && round.getPlannedTime() != null) {
                if (nowTime.isBefore(round.getPlannedTime())) {
                    continue; // Time has not arrived yet
                }
            }

            try {
                // If leads haven't been selected yet, automatically select all eligible contacts from parent
                if (round.getSelectedLeadCount() == null || round.getSelectedLeadCount() == 0) {
                    log.info("Auto-scheduler: Selecting all eligible contacts for Campaign '{}' Round {}",
                            campaign.getName(), round.getRoundNumber());
                    selectionService.selectLeadsForRound(
                            campaign.getId(),
                            round.getRoundNumber(),
                            FollowUpSelectLeadsDTO.builder().selectAllEligible(true).build(),
                            "SYSTEM_SCHEDULER");
                }

                if (round.getMessageBody() == null || round.getMessageBody().trim().isEmpty()) {
                    log.warn("Auto-scheduler: Skipping Campaign '{}' Round {} because message body is empty",
                            campaign.getName(), round.getRoundNumber());
                    continue;
                }

                log.info("Auto-scheduler: Dispatching Campaign '{}' Round {} (ID: {})",
                        campaign.getName(), round.getRoundNumber(), round.getId());

                dispatchService.dispatchRound(campaign.getId(), round.getRoundNumber(), "SYSTEM_SCHEDULER");

            } catch (Exception e) {
                log.error("Auto-scheduler: Failed to dispatch Campaign '{}' Round {}: {}",
                        campaign.getName(), round.getRoundNumber(), e.getMessage(), e);
            }
        }
    }

    /**
     * Runs hourly to monitor upcoming and overdue scheduled rounds.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour
    public void monitorScheduledRounds() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        // Upcoming rounds due within 24 hours
        List<LeadFollowUpRound> upcoming = roundRepository.findByPlannedDateBetweenAndStatusInOrderByPlannedDateAsc(
                today, tomorrow, List.of("SCHEDULED"));

        for (LeadFollowUpRound round : upcoming) {
            log.info("Upcoming round notice: Campaign '{}' Round {} is due on {} at {}",
                    round.getCampaign() != null ? round.getCampaign().getName() : "Unknown",
                    round.getRoundNumber(), round.getPlannedDate(), round.getPlannedTime());
        }

        // Overdue rounds
        List<String> finishedStatuses = Arrays.asList("COMPLETED", "CANCELLED", "FAILED", "IN_PROGRESS");
        List<LeadFollowUpRound> overdue = roundRepository.findByPlannedDateBeforeAndStatusNotInOrderByPlannedDateAsc(
                today, finishedStatuses);

        for (LeadFollowUpRound round : overdue) {
            log.warn("Overdue round alert: Campaign '{}' Round {} was scheduled for {} but has status '{}'",
                    round.getCampaign() != null ? round.getCampaign().getName() : "Unknown",
                    round.getRoundNumber(), round.getPlannedDate(), round.getStatus());
        }
    }
}

