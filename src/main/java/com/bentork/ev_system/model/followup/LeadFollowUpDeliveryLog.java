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
 * Append-only audit log tracking provider responses and delivery lifecycle events.
 */
@Entity
@Table(name = "lead_follow_up_delivery_logs", indexes = {
        @Index(name = "idx_fup_log_message", columnList = "message_id"),
        @Index(name = "idx_fup_log_event", columnList = "event"),
        @Index(name = "idx_fup_log_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadFollowUpDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private LeadFollowUpMessage message;

    @Column(nullable = false, length = 50)
    private String provider; // "META_WHATSAPP", "SMTP_EMAIL"

    @Column(nullable = false, length = 50)
    private String event; // "SUBMISSION_ACK", "SENT", "DELIVERED", "FAILED", "RETRY"

    private String responseCode;

    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

