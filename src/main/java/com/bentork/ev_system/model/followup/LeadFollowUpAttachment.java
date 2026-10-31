package com.bentork.ev_system.model.followup;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores file attachments associated with a follow-up round.
 * Internal storage paths are protected and never exposed publicly.
 */
@Entity
@Table(name = "lead_follow_up_attachments", indexes = {
        @Index(name = "idx_fup_att_round", columnList = "round_id"),
        @Index(name = "idx_fup_att_checksum", columnList = "checksumSha256")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadFollowUpAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private LeadFollowUpRound round;

    @Column(nullable = false)
    private String fileName; // Stored disk filename (e.g. UUID)

    @Column(nullable = false)
    private String originalFileName; // Uploaded display name

    private String contentType; // e.g. "application/pdf"

    private Long fileSizeBytes;

    @Column(nullable = false, length = 500)
    private String storageRelativePath; // Internal private storage path

    @Column(length = 64)
    private String checksumSha256;

    private String uploadedByEmail;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

