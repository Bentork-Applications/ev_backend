package com.bentork.ev_system.model;

import java.time.LocalDateTime;
import java.util.Locale;

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
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a contact person within a {@link SalesCompany}.
 * 
 * The phone number is the primary key for duplicate detection
 * during lead capture and IndiaMART sync.
 */
@Entity
@Table(name = "sales_contacts", indexes = {
        @Index(name = "idx_sales_contact_phone", columnList = "phone"),
        @Index(name = "idx_sales_contact_email", columnList = "email"),
        @Index(name = "idx_sales_contact_company", columnList = "company_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_sales_contact_phone", columnNames = "phone"),
        @UniqueConstraint(name = "uk_sales_contact_email", columnNames = "email")
})
@Getter
@Setter
public class SalesContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private SalesCompany company;

    @Column(nullable = false)
    private String name;

    private String designation; // e.g., "Purchase Manager", "Director"

    @Column(name = "phone")
    private String phone; // Primary key for duplicate detection

    private String email;

    @Column(nullable = false)
    private boolean isPrimary = false; // Primary contact for the company

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        normalizeContactFields();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        normalizeContactFields();
    }

    private void normalizeContactFields() {
        if (this.phone != null) {
            String trimmedPhone = this.phone.trim();
            this.phone = trimmedPhone.isEmpty() ? null : trimmedPhone;
        }
        if (this.email != null) {
            String trimmedEmail = this.email.trim().toLowerCase(Locale.ROOT);
            this.email = trimmedEmail.isEmpty() ? null : trimmedEmail;
        }
    }
}
