package com.bentork.ev_system.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpportunityResponse {

    private Long id;
    private String opportunityNumber;
    private String title;
    private String stage;
    private Double value;
    private LocalDate expectedCloseDate;
    private Integer probability;
    private Long companyId;
    private String companyName;
    private Long contactId;
    private String contactName;
    private Long leadId;
    private String leadNumber;
    private Long ownerAdminId;
    private String ownerAdminName;
    private Long linkedOrderId;
    private String lostReason;
    private String previousStage;
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
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
