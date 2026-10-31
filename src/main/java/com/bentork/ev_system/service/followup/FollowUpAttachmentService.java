package com.bentork.ev_system.service.followup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bentork.ev_system.dto.followup.FollowUpAttachmentResponse;
import com.bentork.ev_system.model.followup.LeadFollowUpAttachment;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.repository.followup.LeadFollowUpAttachmentRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles private physical storage and metadata management for round file attachments.
 * Internal storage paths are never exposed publicly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpAttachmentService {

    private final LeadFollowUpAttachmentRepository attachmentRepository;
    private final LeadFollowUpRoundRepository roundRepository;
    private final FollowUpRoundValidationService validationService;

    @Value("${crm.followup.storage-dir:./uploads/crm/followup-attachments}")
    private String baseStorageDir;

    /**
     * Uploads and stores one or more attachments for a round.
     */
    @Transactional
    public List<FollowUpAttachmentResponse> storeAttachments(
            Long campaignId, Integer roundNumber, List<MultipartFile> files, String adminEmail) {

        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Round %d not found for Campaign %d", roundNumber, campaignId)));

        validationService.validateSequentialAccess(round);

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files provided for attachment upload");
        }

        List<LeadFollowUpAttachment> savedEntities = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
            String extension = "";
            int dotIdx = originalName.lastIndexOf('.');
            if (dotIdx > 0) {
                extension = originalName.substring(dotIdx);
            }

            String storedFileName = UUID.randomUUID().toString() + extension;
            String relativeDir = String.format("camp_%d/rnd_%d", campaignId, roundNumber);
            Path targetDir = Paths.get(baseStorageDir, relativeDir);

            try {
                Files.createDirectories(targetDir);
                Path targetFilePath = targetDir.resolve(storedFileName);

                String checksum = calculateSha256(file.getInputStream());
                Files.copy(file.getInputStream(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);

                LeadFollowUpAttachment attachment = LeadFollowUpAttachment.builder()
                        .round(round)
                        .fileName(storedFileName)
                        .originalFileName(originalName)
                        .contentType(file.getContentType())
                        .fileSizeBytes(file.getSize())
                        .storageRelativePath(relativeDir + "/" + storedFileName)
                        .checksumSha256(checksum)
                        .uploadedByEmail(adminEmail)
                        .createdAt(LocalDateTime.now())
                        .build();

                savedEntities.add(attachmentRepository.save(attachment));
                log.info("Stored attachment '{}' for Campaign {} Round {}", originalName, campaignId, roundNumber);

            } catch (Exception e) {
                log.error("Failed to store attachment '{}': {}", originalName, e.getMessage());
                throw new RuntimeException("Could not store file: " + originalName + " - " + e.getMessage(), e);
            }
        }

        return savedEntities.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FollowUpAttachmentResponse> getAttachmentsForRound(Long campaignId, Integer roundNumber) {
        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("Round not found"));

        return attachmentRepository.findByRoundIdOrderByCreatedAtAsc(round.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAttachment(Long campaignId, Integer roundNumber, Long attachmentId, String adminEmail) {
        LeadFollowUpRound round = roundRepository.findByCampaignIdAndRoundNumber(campaignId, roundNumber)
                .orElseThrow(() -> new IllegalArgumentException("Round not found"));

        validationService.validateSequentialAccess(round);

        LeadFollowUpAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found with id: " + attachmentId));

        if (!attachment.getRound().getId().equals(round.getId())) {
            throw new IllegalArgumentException("Attachment does not belong to specified round");
        }

        try {
            Path filePath = Paths.get(baseStorageDir, attachment.getStorageRelativePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Could not delete physical file for attachment {}: {}", attachmentId, e.getMessage());
        }

        attachmentRepository.delete(attachment);
        log.info("Deleted attachment {} from Round {} by {}", attachmentId, roundNumber, adminEmail);
    }

    @Transactional(readOnly = true)
    public Resource getAttachmentResource(Long attachmentId) {
        LeadFollowUpAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));

        Path filePath = Paths.get(baseStorageDir, attachment.getStorageRelativePath());
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("Attachment file not found on disk");
        }
        return new FileSystemResource(file);
    }

    @Transactional(readOnly = true)
    public List<File> getPhysicalFilesForRound(Long roundId) {
        List<LeadFollowUpAttachment> attachments = attachmentRepository.findByRoundIdOrderByCreatedAtAsc(roundId);
        List<File> files = new ArrayList<>();
        for (LeadFollowUpAttachment att : attachments) {
            Path p = Paths.get(baseStorageDir, att.getStorageRelativePath());
            File f = p.toFile();
            if (f.exists()) {
                files.add(f);
            }
        }
        return files;
    }

    public FollowUpAttachmentResponse mapToResponse(LeadFollowUpAttachment att) {
        return FollowUpAttachmentResponse.builder()
                .id(att.getId())
                .roundId(att.getRound().getId())
                .fileName(att.getFileName())
                .originalFileName(att.getOriginalFileName())
                .contentType(att.getContentType())
                .fileSizeBytes(att.getFileSizeBytes())
                .createdAt(att.getCreatedAt())
                .build();
    }

    private String calculateSha256(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}

