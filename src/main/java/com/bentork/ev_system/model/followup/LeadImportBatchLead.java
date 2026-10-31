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
 * Links a canonical CRM Lead to a LeadImportBatch.
 * Preserves the original imported batch relationship without creating duplicate Lead records.
 */
@Entity
@Table(name = "lead_import_batch_leads", indexes = {
        @Index(name = "idx_batch_lead_batch", columnList = "batch_id"),
        @Index(name = "idx_batch_lead_lead", columnList = "lead_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_batch_lead", columnNames = {"batch_id", "lead_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadImportBatchLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private LeadImportBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    private Integer rowNumber;

    private String rawName;

    private String rawPhone;

    private String rawEmail;

    private String rawCompany;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

