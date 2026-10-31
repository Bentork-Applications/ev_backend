package com.bentork.ev_system.service.followup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundLeadRepository;
import com.bentork.ev_system.repository.followup.LeadImportBatchLeadRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces strict cascading sequential rules, parent-round lineage,
 * and phase gating across all 5 follow-up rounds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpRoundValidationService {

    private final LeadImportBatchLeadRepository batchLeadRepository;
    private final LeadFollowUpRoundLeadRepository roundLeadRepository;

    /**
     * Validates that all candidate lead IDs for a round strictly belong to the immediate parent pool:
     * - Round 1: Must belong to original LeadImportBatch.
     * - Round 2-5: Must be selected in immediately preceding round (Round N-1).
     *
     * Throws IllegalArgumentException listing any rejected lead IDs.
     */
    public void validateLeadLineage(LeadFollowUpCampaign campaign, LeadFollowUpRound round, List<Long> candidateLeadIds) {
        if (candidateLeadIds == null || candidateLeadIds.isEmpty()) {
            throw new IllegalArgumentException("Lead selection list cannot be empty");
        }

        // Prevent duplicate lead IDs within the same selection request
        Set<Long> uniqueCandidateIds = new HashSet<>(candidateLeadIds);
        if (uniqueCandidateIds.size() < candidateLeadIds.size()) {
            throw new IllegalArgumentException("Duplicate lead IDs provided within the same selection request");
        }

        int roundNumber = round.getRoundNumber();

        if (roundNumber == 1) {
            // Round 1: Candidate leads must exist in the campaign's import batch
            List<Long> batchLeadIds = batchLeadRepository.findLeadIdsByBatchId(campaign.getBatch().getId());
            Set<Long> allowedBatchSet = new HashSet<>(batchLeadIds);

            List<Long> ineligibleIds = new ArrayList<>();
            for (Long leadId : candidateLeadIds) {
                if (!allowedBatchSet.contains(leadId)) {
                    ineligibleIds.add(leadId);
                }
            }

            if (!ineligibleIds.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "Selection rejected: %d lead(s) %s do not belong to the campaign's original import batch (ID: %d)",
                        ineligibleIds.size(), ineligibleIds, campaign.getBatch().getId()));
            }

        } else {
            // Round 2 to 5: Candidate leads must have been SELECTED in the immediate parent round
            LeadFollowUpRound parentRound = round.getParentRound();
            if (parentRound == null) {
                throw new IllegalStateException(String.format(
                        "Configuration error: Round %d has no parent round associated", roundNumber));
            }

            List<Long> parentSelectedLeadIds = roundLeadRepository.findSelectedLeadIdsByRoundId(parentRound.getId());
            Set<Long> allowedParentSet = new HashSet<>(parentSelectedLeadIds);

            List<Long> ineligibleIds = new ArrayList<>();
            for (Long leadId : candidateLeadIds) {
                if (!allowedParentSet.contains(leadId)) {
                    ineligibleIds.add(leadId);
                }
            }

            if (!ineligibleIds.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "Cascading selection rejected: %d lead(s) %s were not selected in Round %d. Round %d can ONLY select leads from Round %d.",
                        ineligibleIds.size(), ineligibleIds, parentRound.getRoundNumber(), roundNumber, parentRound.getRoundNumber()));
            }
        }
    }

    /**
     * Validates that an operation on Round N can only occur if Round N-1 is COMPLETED.
     */
    public void validateSequentialAccess(LeadFollowUpRound round) {
        int roundNumber = round.getRoundNumber();
        if (roundNumber > 1) {
            LeadFollowUpRound parentRound = round.getParentRound();
            if (parentRound == null || !"COMPLETED".equalsIgnoreCase(parentRound.getStatus())) {
                String parentStatus = parentRound != null ? parentRound.getStatus() : "MISSING";
                throw new IllegalStateException(String.format(
                        "Round %d is locked. Round %d must be COMPLETED before Round %d can be started or modified (current status of Round %d: %s).",
                        roundNumber, (roundNumber - 1), roundNumber, (roundNumber - 1), parentStatus));
            }
        }
    }

    /**
     * Validates that a round is ready for dispatch:
     * - Must not already be IN_PROGRESS or COMPLETED
     * - Parent round must be COMPLETED (for rounds 2-5)
     * - Must have selected leads (selectedLeadCount > 0)
     * - Message body must not be blank
     */
    public void validateRoundReadyForSend(LeadFollowUpRound round) {
        if ("IN_PROGRESS".equalsIgnoreCase(round.getStatus())) {
            throw new IllegalStateException(String.format(
                    "Round %d dispatch is already in progress.", round.getRoundNumber()));
        }
        if ("COMPLETED".equalsIgnoreCase(round.getStatus())) {
            throw new IllegalStateException(String.format(
                    "Round %d is already completed. To retry failed messages, use the retry endpoint.", round.getRoundNumber()));
        }

        validateSequentialAccess(round);

        if (round.getSelectedLeadCount() == null || round.getSelectedLeadCount() <= 0) {
            throw new IllegalStateException(String.format(
                    "Round %d has no leads selected. Please select leads before sending.", round.getRoundNumber()));
        }

        if (round.getMessageBody() == null || round.getMessageBody().trim().isEmpty()) {
            throw new IllegalStateException(String.format(
                    "Round %d message body is empty. Please configure message content before sending.", round.getRoundNumber()));
        }

        String channel = round.getChannel();
        if (channel == null || (!channel.equalsIgnoreCase("WHATSAPP") &&
                                !channel.equalsIgnoreCase("EMAIL") &&
                                !channel.equalsIgnoreCase("BOTH"))) {
            throw new IllegalStateException(String.format(
                    "Invalid channel '%s' for Round %d. Must be WHATSAPP, EMAIL, or BOTH.", channel, round.getRoundNumber()));
        }

        if (("EMAIL".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel)) &&
                (round.getSubject() == null || round.getSubject().trim().isEmpty())) {
            throw new IllegalStateException(String.format(
                    "Round %d requires an email subject for channel %s.", round.getRoundNumber(), channel));
        }
    }
}

