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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single round in the 5-round sequential follow-up campaign.
 * Maintains parent round reference to enforce strictly cascading lead selections.
 */
@Entity
@Table(name = "lead_follow_up_rounds", indexes = {
        @Index(name = "idx_fup_rnd_campaign", columnList = "campaign_id"),
        @Index(name = "idx_fup_rnd_parent", columnList = "parent_round_id"),
        @Index(name = "idx_fup_rnd_status", columnList = "status"),
        @Index(name = "idx_fup_rnd_number", columnList = "campaign_id, roundNumber")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_campaign_round_num", columnNames = {"campaign_id", "roundNumber"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadFollowUpRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private LeadFollowUpCampaign campaign;

    @Column(nullable = false)
    private Integer roundNumber; // 1, 2, 3, 4, 5

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_round_id")
    private LeadFollowUpRound parentRound; // Null for Round 1, points to immediately previous round for 2-5

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String channel = "WHATSAPP"; // "WHATSAPP", "EMAIL", "BOTH"

    private String subject; // Email subject or round title

    @Column(columnDefinition = "TEXT")
    private String messageBody; // Message body with placeholders

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // "PENDING", "CONFIGURED", "IN_PROGRESS", "COMPLETED", "FAILED"

    @Column(nullable = false)
    @Builder.Default
    private Integer selectedLeadCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer sentCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer deliveredCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer failedCount = 0;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private String lastTriggeredByEmail;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING";
        }
        if (this.channel == null) {
            this.channel = "WHATSAPP";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

