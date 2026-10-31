package com.bentork.ev_system.model.followup;

import java.time.LocalDateTime;

import com.bentork.ev_system.model.Admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a 5-Round Sequential Follow-up Campaign.
 * Tied directly to a single LeadImportBatch from which all cascading rounds originate.
 */
@Entity
@Table(name = "lead_follow_up_campaigns", indexes = {
        @Index(name = "idx_fup_camp_status", columnList = "status"),
        @Index(name = "idx_fup_camp_batch", columnList = "batch_id"),
        @Index(name = "idx_fup_camp_owner", columnList = "owner_admin_id"),
        @Index(name = "idx_fup_camp_channel", columnList = "channel")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadFollowUpCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false, unique = true)
    private LeadImportBatch batch;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String channel = "WHATSAPP"; // "WHATSAPP", "EMAIL", "BOTH"

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "DRAFT"; // "DRAFT", "ACTIVE", "COMPLETED", "PAUSED", "CANCELLED"

    @Column(nullable = false)
    @Builder.Default
    private Integer currentRoundNumber = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalRounds = 5;

    @Column(nullable = false)
    @Builder.Default
    private Integer completedRounds = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_admin_id")
    private Admin ownerAdmin;

    private String createdByAdminEmail;

    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "DRAFT";
        }
        if (this.channel == null) {
            this.channel = "WHATSAPP";
        }
        if (this.totalRounds == null) {
            this.totalRounds = 5;
        }
        if (this.currentRoundNumber == null) {
            this.currentRoundNumber = 1;
        }
        if (this.completedRounds == null) {
            this.completedRounds = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

