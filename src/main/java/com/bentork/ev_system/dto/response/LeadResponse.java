package com.bentork.ev_system.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeadResponse {

    private Long id;
    private String leadNumber;
    private String title;
    private String source;
    private String status;
    private Long companyId;
    private String companyName;
    private Long contactId;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Long ownerAdminId;
    private String ownerAdminName;
    private String city;
    private String state;
    private Double estimatedValue;
    private String notes;
    private LocalDate nextFollowUpDate;
    private LocalTime nextFollowUpTime;
    private String indiaMartLeadId;
    private String tags;
    private Long convertedToOpportunityId;
    private String previousStatus;
    private String lostReason;
    private String lostApprovalStatus;
    private Long lostRequestedByAdminId;
    private String lostRequestedByAdminName;
    private LocalDateTime lostRequestedAt;
    private Long lostApprovedByAdminId;
    private String lostApprovedByAdminName;
    private LocalDateTime lostApprovedAt;
    private Long lostRevokedByAdminId;
    private String lostRevokedByAdminName;
    private LocalDateTime lostRevokedAt;
    private String lostRevokeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
