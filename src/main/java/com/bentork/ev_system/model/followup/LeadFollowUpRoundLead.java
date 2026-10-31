package com.bentork.ev_system.model.followup;

import java.time.LocalDateTime;

import com.bentork.ev_system.model.Lead;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks lead inclusion and delivery state within a specific follow-up round.
 * A lead can legitimately appear across sequential rounds when selected from the previous round.
 */
@Entity
@Table(name = "lead_follow_up_round_leads", indexes = {
        @Index(name = "idx_rnd_lead_round", columnList = "round_id"),
        @Index(name = "idx_rnd_lead_campaign", columnList = "campaign_id"),
        @Index(name = "idx_rnd_lead_lead", columnList = "lead_id"),
        @Index(name = "idx_rnd_lead_delivery_status", columnList = "round_id, deliveryStatus")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_round_lead", columnNames = {"round_id", "lead_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadFollowUpRoundLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private LeadFollowUpRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private LeadFollowUpCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String selectionStatus = "SELECTED"; // "SELECTED", "EXCLUDED"

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String deliveryStatus = "PENDING"; // "PENDING", "QUEUED", "SENT", "DELIVERED", "FAILED", "OPTED_OUT"

    private LocalDateTime selectedAt;

    private String selectedByEmail;

    @PrePersist
    protected void onCreate() {
        if (this.selectedAt == null) {
            this.selectedAt = LocalDateTime.now();
        }
        if (this.selectionStatus == null) {
            this.selectionStatus = "SELECTED";
        }
        if (this.deliveryStatus == null) {
            this.deliveryStatus = "PENDING";
        }
    }
}

