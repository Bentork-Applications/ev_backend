package com.bentork.ev_system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminLostActionDTO {

    @JsonAlias({"lostReason", "revokeReason", "notes", "remark"})
    private String reason;

    @JsonAlias({"targetStage", "targetStatus", "restoreStage", "restoreStatus", "stage", "status"})
    private String targetPipeline;

    public String getEffectiveReason() {
        return reason != null && !reason.trim().isEmpty() ? reason.trim() : null;
    }

    public String getEffectiveTargetPipeline() {
        return targetPipeline != null && !targetPipeline.trim().isEmpty() ? targetPipeline.trim() : null;
    }
}
