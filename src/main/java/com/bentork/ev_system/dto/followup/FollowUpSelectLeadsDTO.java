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
public class FollowUpSelectLeadsDTO {

    private Boolean selectAllEligible;

    private List<Long> leadIds;
}

