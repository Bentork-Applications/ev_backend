package com.bentork.ev_system.dto.followup;

import java.util.List;

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
public class FollowUpRetryDTO {

    private List<Long> messageIds; // Optional: specific message IDs to retry; if empty/null, retries all failed in the round
}

