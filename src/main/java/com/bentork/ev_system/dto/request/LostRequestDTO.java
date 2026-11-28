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
public class LostRequestDTO {

    @JsonAlias({"reason", "notes", "remark"})
    private String lostReason;

    public String getEffectiveLostReason() {
        return lostReason != null ? lostReason.trim() : null;
    }
}
