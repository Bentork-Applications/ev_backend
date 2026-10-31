package com.bentork.ev_system.model.followup;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the single Excel or CSV import batch for a 5-round sequential follow-up campaign.
 * All five follow-up rounds operate strictly on this imported lead batch.
 */
@Entity
@Table(name = "lead_import_batches", indexes = {
        @Index(name = "idx_batch_number", columnList = "batchNumber"),
        @Index(name = "idx_batch_uploaded_by", columnList = "uploadedByAdminEmail"),
        @Index(name = "idx_batch_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String batchNumber; // e.g. "BATCH-20261030-001"

    @Column(nullable = false)
    private String originalFileName;

    @Column(length = 64)
    private String fileChecksum; // SHA-256 for audit and duplicate file detection

    private Long fileSizeBytes;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer validRows = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer invalidRows = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer newLeadsCreated = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer existingLeadsMatched = 0;

    private String uploadedByAdminEmail;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

