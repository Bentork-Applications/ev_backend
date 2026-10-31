package com.bentork.ev_system.dto.followup;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowUpEligibleLeadDTO {

    private Long leadId;
    private String leadNumber;
    private String title;
    private String name;
    private String phone;
    private String email;
    private String company;
    private String city;
    private String state;
    private String leadStatus;
    private Double estimatedValue;
    private LocalDate followUpDate;
    private Long assignedUserId;
    private String assignedUserName;
    private boolean selectedInCurrentRound;
    private boolean selectedInParentRound;
    private String previousRoundDeliveryStatus;
    private LocalDateTime createdAt;
}

