package com.bentork.ev_system.service.followup;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.dto.followup.FollowUpDispatchResultDTO;
import com.bentork.ev_system.dto.followup.FollowUpMessageConfigDTO;
import com.bentork.ev_system.dto.followup.FollowUpMessageHistoryResponse;
import com.bentork.ev_system.dto.followup.FollowUpProgressResponse;
import com.bentork.ev_system.dto.followup.FollowUpProgressResponse.RoundProgressItem;
import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.dto.response.WhatsAppSendResult;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpDeliveryLog;
import com.bentork.ev_system.model.followup.LeadFollowUpMessage;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.model.followup.LeadFollowUpRoundLead;
import com.bentork.ev_system.repository.ContactSuppressionRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpCampaignRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpDeliveryLogRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpMessageRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundLeadRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;
import com.bentork.ev_system.service.EmailDispatchService;
import com.bentork.ev_system.service.WhatsAppDispatchService;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles message configuration, multi-channel dispatch execution (WhatsApp + Email with attachments),
 * idempotency checks, failure retries, and round/campaign completion transitions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpMessageDispatchService {

    private final LeadFollowUpCampaignRepository campaignRepository;
    private final LeadFollowUpRoundRepository roundRepository;
    private final LeadFollowUpRoundLeadRepository roundLeadRepository;
    private final LeadFollowUpMessageRepository messageRepository;
    private final LeadFollowUpDeliveryLogRepository deliveryLogRepository;
    private final ContactSuppressionRepository suppressionRepository;

    private final FollowUpRoundValidationService validationService;
    private final FollowUpAttachmentService attachmentService;
    private final WhatsAppDispatchService whatsAppDispatchService;
    private final EmailDispatchService emailDispatchService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:hrbentork@gmail.com}")
    private String fromEmail;

    /**
     * Updates subject and message body configuration for a round.
     */
    @Transactional
    public LeadFollowUpRound saveMessageConfig(
            Long campaignId,
            Integer roundNumber,
            FollowUpMessageConfigDTO dto,
            String adminEmail) {

        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Round %d not found for Campaign %d", roundNumber, campaignId)));

        validationService.validateSequentialAccess(round);

        if ("IN_PROGRESS".equalsIgnoreCase(round.getStatus()) || "COMPLETED".equalsIgnoreCase(round.getStatus())) {
            throw new IllegalStateException(String.format(
                    "Cannot modify message configuration for Round %d because it is %s", roundNumber, round.getStatus()));
        }

        if (dto.getChannel() != null && !dto.getChannel().trim().isEmpty()) {
            String ch = dto.getChannel().trim().toUpperCase();
            if (ch.equals("WHATSAPP") || ch.equals("EMAIL") || ch.equals("BOTH")) {
                round.setChannel(ch);
            } else {
                throw new IllegalArgumentException("Invalid channel: " + dto.getChannel());
            }
        }

        if (dto.getSubject() != null) {
            round.setSubject(dto.getSubject().trim());
        }

        if (dto.getMessageBody() != null) {
            round.setMessageBody(dto.getMessageBody().trim());
        }

        if (round.getSelectedLeadCount() != null && round.getSelectedLeadCount() > 0 &&
            round.getMessageBody() != null && !round.getMessageBody().isEmpty()) {
            round.setStatus("CONFIGURED");
        }

        return roundRepository.save(round);
    }

    /**
     * Dispatches messages to all selected leads in a round.
     * Enforces idempotency to prevent duplicate sends and updates round/campaign statuses.
     */
    @Transactional
    public FollowUpDispatchResultDTO dispatchRound(Long campaignId, Integer roundNumber, String adminEmail) {
        LeadFollowUpCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Round %d not found for Campaign %d", roundNumber, campaignId)));

        // 1. Strict validation check before triggering dispatch
        validationService.validateRoundReadyForSend(round);

        // 2. Mark round as IN_PROGRESS
        round.setStatus("IN_PROGRESS");
        round.setStartedAt(LocalDateTime.now());
        round.setLastTriggeredByEmail(adminEmail);
        roundRepository.save(round);

        // 3. Load round leads and physical attachments
        List<LeadFollowUpRoundLead> selectedLeads = roundLeadRepository.findByRoundIdAndSelectionStatus(round.getId(), "SELECTED");
        List<File> physicalAttachments = attachmentService.getPhysicalFilesForRound(round.getId());

        int sentCount = 0;
        int failedCount = 0;
        int optedOutCount = 0;

        for (LeadFollowUpRoundLead roundLead : selectedLeads) {
            Lead lead = roundLead.getLead();
            if (lead == null) continue;

            String phone = (lead.getPhone() != null && !lead.getPhone().isEmpty()) ? lead.getPhone() :
                    (lead.getContact() != null ? lead.getContact().getPhone() : null);
            String email = (lead.getEmail() != null && !lead.getEmail().isEmpty()) ? lead.getEmail() :
                    (lead.getContact() != null ? lead.getContact().getEmail() : null);

            String recipientName = lead.getContact() != null ? lead.getContact().getName() : lead.getTitle();
            String companyName = lead.getCompany() != null ? lead.getCompany().getName() : "Valued Partner";

            // Check contact suppression and opt-out
            boolean isOptedOut = lead.isOptedOut() || (phone != null && suppressionRepository.existsByPhone(phone));
            if (isOptedOut) {
                roundLead.setDeliveryStatus("OPTED_OUT");
                roundLeadRepository.save(roundLead);
                optedOutCount++;
                continue;
            }

            // Generate idempotency key
            String idempotencyKey = LeadFollowUpMessage.generateIdempotencyKey(campaignId, roundNumber, lead.getId(), 0);

            // Prevent duplicate sends if already successfully sent
            Optional<LeadFollowUpMessage> existingMsgOpt = messageRepository.findByIdempotencyKey(idempotencyKey);
            if (existingMsgOpt.isPresent()) {
                LeadFollowUpMessage existing = existingMsgOpt.get();
                if ("SENT".equalsIgnoreCase(existing.getStatus()) ||
                    "DELIVERED".equalsIgnoreCase(existing.getStatus()) ||
                    "SUBMITTED".equalsIgnoreCase(existing.getStatus())) {
                    log.info("Skipping lead {} for Round {} due to existing message with status {}",
                            lead.getId(), roundNumber, existing.getStatus());
                    sentCount++;
                    continue;
                }
            }

            LeadFollowUpMessage message = existingMsgOpt.orElseGet(() -> LeadFollowUpMessage.builder()
                    .roundLead(roundLead)
                    .round(round)
                    .lead(lead)
                    .idempotencyKey(idempotencyKey)
                    .channel(round.getChannel())
                    .recipientPhone(phone)
                    .recipientEmail(email)
                    .retryCount(0)
                    .build());

            message.setStatus("QUEUED");
            message.setQueuedAt(LocalDateTime.now());
            message = messageRepository.save(message);

            boolean success = false;
            String providerMsgId = null;
            String errorCode = null;
            String errorMsg = null;

            String channel = round.getChannel();

            // WhatsApp Dispatch
            if ("WHATSAPP".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel)) {
                if (phone != null && !phone.trim().isEmpty()) {
                    WhatsAppSendResult waResult = whatsAppDispatchService.sendCampaignMessage(
                            phone, round.getMessageBody(), null, recipientName, companyName);

                    if (waResult.isSuccess()) {
                        success = true;
                        providerMsgId = waResult.getProviderMessageId();
                        logDeliveryEvent(message, "META_WHATSAPP", "SUBMISSION_ACK", "WAMID: " + providerMsgId, "200");
                    } else {
                        errorCode = waResult.getErrorCode();
                        errorMsg = waResult.getErrorMessage();
                        logDeliveryEvent(message, "META_WHATSAPP", "FAILED", errorMsg, errorCode);
                    }
                } else {
                    errorCode = "MISSING_PHONE";
                    errorMsg = "No phone number available for WhatsApp delivery";
                }
            }

            // Email Dispatch
            if ("EMAIL".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel)) {
                if (email != null && !email.trim().isEmpty()) {
                    try {
                        sendEmailWithAttachments(email, round.getSubject(), round.getMessageBody(),
                                recipientName, companyName, physicalAttachments);
                        success = true;
                        String emailId = "msg_email_" + System.currentTimeMillis();
                        if (providerMsgId == null) providerMsgId = emailId;
                        logDeliveryEvent(message, "SMTP_EMAIL", "SENT", "Email dispatched to " + email, "250");
                    } catch (Exception e) {
                        errorCode = "EMAIL_ERROR";
                        errorMsg = e.getMessage();
                        logDeliveryEvent(message, "SMTP_EMAIL", "FAILED", errorMsg, "500");
                    }
                } else if (!"BOTH".equalsIgnoreCase(channel)) {
                    errorCode = "MISSING_EMAIL";
                    errorMsg = "No email address available for Email delivery";
                }
            }

            // Update Message & Lead states
            if (success) {
                message.setStatus("SENT");
                message.setSubmittedAt(LocalDateTime.now());
                message.setSentAt(LocalDateTime.now());
                message.setProviderMessageId(providerMsgId);
                message.setErrorMessage(null);
                roundLead.setDeliveryStatus("SENT");
                sentCount++;
            } else {
                message.setStatus("FAILED");
                message.setFailedAt(LocalDateTime.now());
                message.setErrorCode(errorCode);
                message.setErrorMessage(errorMsg);
                roundLead.setDeliveryStatus("FAILED");
                failedCount++;
            }

            messageRepository.save(message);
            roundLeadRepository.save(roundLead);
        }

        // 4. Update round statistics and mark COMPLETED per actual delivery policy
        round.setSentCount(sentCount);
        round.setDeliveredCount(sentCount);
        round.setFailedCount(failedCount);
        round.setCompletedAt(LocalDateTime.now());
        round.setStatus("COMPLETED");
        roundRepository.save(round);

        // 5. Update Campaign Progress & Completion
        campaign.setCompletedRounds(campaign.getCompletedRounds() + 1);

        Integer nextRoundUnlocked = null;
        if (roundNumber == 5) {
            campaign.setStatus("COMPLETED");
            campaign.setCompletedAt(LocalDateTime.now());
            log.info("Campaign '{}' (ID: {}) successfully COMPLETED after Round 5 dispatch!",
                    campaign.getName(), campaign.getId());
        } else {
            campaign.setCurrentRoundNumber(roundNumber + 1);
            nextRoundUnlocked = roundNumber + 1;
        }
        campaignRepository.save(campaign);

        log.info("Round {} completed for Campaign {}: sent={}, failed={}, optedOut={}",
                roundNumber, campaignId, sentCount, failedCount, optedOutCount);

        return FollowUpDispatchResultDTO.builder()
                .campaignId(campaignId)
                .roundNumber(roundNumber)
                .status("COMPLETED")
                .totalSelected(selectedLeads.size())
                .sentCount(sentCount)
                .failedCount(failedCount)
                .optedOutCount(optedOutCount)
                .startedAt(round.getStartedAt())
                .completedAt(round.getCompletedAt())
                .nextRoundUnlocked(nextRoundUnlocked)
                .message(String.format("Round %d execution finished: %d sent, %d failed", roundNumber, sentCount, failedCount))
                .build();
    }

    /**
     * Retries delivery for failed messages in a round safely without duplicate sends.
     */
    @Transactional
    public FollowUpDispatchResultDTO retryFailedMessages(
            Long campaignId, Integer roundNumber, List<Long> specificMessageIds, String adminEmail) {

        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("Round not found"));

        List<LeadFollowUpMessage> failedMessages = messageRepository.findByRoundIdAndStatus(round.getId(), "FAILED");
        if (specificMessageIds != null && !specificMessageIds.isEmpty()) {
            Set<Long> filterSet = new java.util.HashSet<>(specificMessageIds);
            failedMessages = failedMessages.stream()
                    .filter(m -> filterSet.contains(m.getId()))
                    .collect(Collectors.toList());
        }

        if (failedMessages.isEmpty()) {
            return FollowUpDispatchResultDTO.builder()
                    .campaignId(campaignId)
                    .roundNumber(roundNumber)
                    .status(round.getStatus())
                    .totalSelected(round.getSelectedLeadCount())
                    .sentCount(round.getSentCount())
                    .failedCount(round.getFailedCount())
                    .message("No failed messages to retry for Round " + roundNumber)
                    .build();
        }

        List<File> physicalAttachments = attachmentService.getPhysicalFilesForRound(round.getId());
        int retriedSent = 0;
        int stillFailed = 0;

        for (LeadFollowUpMessage msg : failedMessages) {
            Lead lead = msg.getLead();
            LeadFollowUpRoundLead roundLead = msg.getRoundLead();

            int attempt = (msg.getRetryCount() != null ? msg.getRetryCount() : 0) + 1;
            msg.setRetryCount(attempt);

            String recipientName = lead.getContact() != null ? lead.getContact().getName() : lead.getTitle();
            String companyName = lead.getCompany() != null ? lead.getCompany().getName() : "Valued Partner";

            boolean success = false;
            String channel = round.getChannel();

            if ("WHATSAPP".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel)) {
                if (msg.getRecipientPhone() != null) {
                    WhatsAppSendResult waResult = whatsAppDispatchService.sendCampaignMessage(
                            msg.getRecipientPhone(), round.getMessageBody(), null, recipientName, companyName);
                    if (waResult.isSuccess()) {
                        success = true;
                        msg.setProviderMessageId(waResult.getProviderMessageId());
                    }
                }
            }

            if ("EMAIL".equalsIgnoreCase(channel) || "BOTH".equalsIgnoreCase(channel)) {
                if (msg.getRecipientEmail() != null) {
                    try {
                        sendEmailWithAttachments(msg.getRecipientEmail(), round.getSubject(),
                                round.getMessageBody(), recipientName, companyName, physicalAttachments);
                        success = true;
                    } catch (Exception ignored) {}
                }
            }

            if (success) {
                msg.setStatus("SENT");
                msg.setSentAt(LocalDateTime.now());
                msg.setErrorMessage(null);
                roundLead.setDeliveryStatus("SENT");
                retriedSent++;
                logDeliveryEvent(msg, "RETRY", "SENT", "Retry attempt " + attempt + " succeeded", "200");
            } else {
                msg.setFailedAt(LocalDateTime.now());
                stillFailed++;
                logDeliveryEvent(msg, "RETRY", "FAILED", "Retry attempt " + attempt + " failed", "500");
            }

            messageRepository.save(msg);
            roundLeadRepository.save(roundLead);
        }

        // Recalculate round stats
        long totalFailed = messageRepository.countByRoundIdAndStatus(round.getId(), "FAILED");
        long totalSent = messageRepository.countByRoundIdAndStatus(round.getId(), "SENT");
        round.setSentCount((int) totalSent);
        round.setFailedCount((int) totalFailed);
        roundRepository.save(round);

        return FollowUpDispatchResultDTO.builder()
                .campaignId(campaignId)
                .roundNumber(roundNumber)
                .status(round.getStatus())
                .totalSelected(round.getSelectedLeadCount())
                .sentCount((int) totalSent)
                .failedCount((int) totalFailed)
                .message(String.format("Retry complete: %d retried messages sent, %d still failed", retriedSent, stillFailed))
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<FollowUpMessageHistoryResponse> getRoundMessageHistory(
            Long campaignId, Integer roundNumber, int page, int size) {

        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("Round not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<LeadFollowUpMessage> msgPage = messageRepository.findByRoundIdOrderByCreatedAtDesc(round.getId(), pageable);

        List<FollowUpMessageHistoryResponse> list = msgPage.getContent().stream()
                .map(m -> FollowUpMessageHistoryResponse.builder()
                        .id(m.getId())
                        .leadId(m.getLead().getId())
                        .leadNumber(m.getLead().getLeadNumber())
                        .contactName(m.getLead().getContact() != null ? m.getLead().getContact().getName() : m.getLead().getTitle())
                        .recipientPhone(m.getRecipientPhone())
                        .recipientEmail(m.getRecipientEmail())
                        .channel(m.getChannel())
                        .status(m.getStatus())
                        .providerMessageId(m.getProviderMessageId())
                        .errorCode(m.getErrorCode())
                        .errorMessage(m.getErrorMessage())
                        .retryCount(m.getRetryCount())
                        .queuedAt(m.getQueuedAt())
                        .sentAt(m.getSentAt())
                        .deliveredAt(m.getDeliveredAt())
                        .failedAt(m.getFailedAt())
                        .build())
                .collect(Collectors.toList());

        return PagedResponse.of(list, msgPage.getNumber(), msgPage.getSize(), msgPage.getTotalElements(), msgPage.getTotalPages(), msgPage.isLast());
    }

    @Transactional(readOnly = true)
    public FollowUpProgressResponse getCampaignProgress(Long campaignId) {
        LeadFollowUpCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found with id: " + campaignId));

        List<LeadFollowUpRound> rounds = roundRepository.findByCampaignIdOrderByRoundNumberAsc(campaignId);
        List<RoundProgressItem> items = new ArrayList<>();

        int batchTotal = campaign.getBatch() != null ? campaign.getBatch().getValidRows() : 0;
        int previousSelected = batchTotal;

        for (LeadFollowUpRound r : rounds) {
            items.add(RoundProgressItem.builder()
                    .roundNumber(r.getRoundNumber())
                    .status(r.getStatus())
                    .eligibleCount(previousSelected)
                    .selectedCount(r.getSelectedLeadCount())
                    .sentCount(r.getSentCount())
                    .deliveredCount(r.getDeliveredCount())
                    .failedCount(r.getFailedCount())
                    .build());

            previousSelected = r.getSelectedLeadCount() != null ? r.getSelectedLeadCount() : 0;
        }

        String retention = "0.0%";
        if (batchTotal > 0 && !rounds.isEmpty()) {
            LeadFollowUpRound lastRound = rounds.get(rounds.size() - 1);
            int lastSelected = lastRound.getSelectedLeadCount() != null ? lastRound.getSelectedLeadCount() : 0;
            double pct = ((double) lastSelected / batchTotal) * 100.0;
            retention = String.format("%.1f%%", pct);
        }

        return FollowUpProgressResponse.builder()
                .campaignId(campaignId)
                .campaignName(campaign.getName())
                .status(campaign.getStatus())
                .totalBatchLeads(batchTotal)
                .waterfall(items)
                .overallRetentionRate(retention)
                .build();
    }

    private void sendEmailWithAttachments(
            String toEmail, String subject, String bodyTemplate, String contactName, String companyName, List<File> files) throws Exception {

        String resolvedSubject = (subject != null && !subject.isEmpty()) ? subject : "Follow-up from Bentork EV";
        String resolvedBody = emailDispatchService.formatTemplate(bodyTemplate, contactName, companyName);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(toEmail.trim());
        helper.setSubject(resolvedSubject);

        boolean isHtml = resolvedBody.contains("<") && resolvedBody.contains(">");
        helper.setText(resolvedBody, isHtml);

        if (files != null) {
            for (File file : files) {
                if (file.exists()) {
                    helper.addAttachment(file.getName(), file);
                }
            }
        }

        mailSender.send(mimeMessage);
        log.info("Dispatched follow-up email with {} attachment(s) to {}", (files != null ? files.size() : 0), toEmail);
    }

    private void logDeliveryEvent(LeadFollowUpMessage message, String provider, String event, String payload, String responseCode) {
        LeadFollowUpDeliveryLog logEntity = LeadFollowUpDeliveryLog.builder()
                .message(message)
                .provider(provider)
                .event(event)
                .rawPayload(payload)
                .responseCode(responseCode)
                .createdAt(LocalDateTime.now())
                .build();
        deliveryLogRepository.save(logEntity);
    }
}

