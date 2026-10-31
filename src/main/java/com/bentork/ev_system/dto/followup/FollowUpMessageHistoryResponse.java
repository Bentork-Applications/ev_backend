package com.bentork.ev_system.dto.followup;

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
public class FollowUpMessageHistoryResponse {

    private Long id;
    private Long leadId;
    private String leadNumber;
    private String contactName;
    private String recipientPhone;
    private String recipientEmail;
    private String channel;
    private String status;
    private String providerMessageId;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime queuedAt;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime failedAt;
}

