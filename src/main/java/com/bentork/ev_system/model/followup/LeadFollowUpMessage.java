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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores individual message dispatch attempts, provider response identifiers,
 * delivery timestamps, and idempotency keys to guarantee duplicate-send protection.
 */
@Entity
@Table(name = "lead_follow_up_messages", indexes = {
        @Index(name = "idx_fup_msg_round_lead", columnList = "round_lead_id"),
        @Index(name = "idx_fup_msg_round", columnList = "round_id"),
        @Index(name = "idx_fup_msg_lead", columnList = "lead_id"),
        @Index(name = "idx_fup_msg_idempotency", columnList = "idempotencyKey"),
        @Index(name = "idx_fup_msg_provider_id", columnList = "providerMessageId"),
        @Index(name = "idx_fup_msg_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadFollowUpMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_lead_id", nullable = false)
    private LeadFollowUpRoundLead roundLead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private LeadFollowUpRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String channel; // "WHATSAPP", "EMAIL"

    private String recipientPhone;

    private String recipientEmail;

    private String providerMessageId; // WAMID from Meta Cloud API or SMTP messageId

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "QUEUED"; // "QUEUED", "SUBMITTED", "SENT", "DELIVERED", "FAILED"

    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    private LocalDateTime queuedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime sentAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime failedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "QUEUED";
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static String generateIdempotencyKey(Long campaignId, Integer roundNumber, Long leadId, Integer attempt) {
        return String.format("followup_c%d_r%d_l%d_att%d",
                campaignId, roundNumber, leadId, (attempt != null ? attempt : 0));
    }
}

