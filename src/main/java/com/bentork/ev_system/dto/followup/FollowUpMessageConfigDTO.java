package com.bentork.ev_system.dto.followup;

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
public class FollowUpMessageConfigDTO {

    private String channel; // "WHATSAPP", "EMAIL", "BOTH"

    private String subject; // Email subject or campaign notification title

    private String messageBody; // Message content with {{contactName}}, {{companyName}}, etc.
}

