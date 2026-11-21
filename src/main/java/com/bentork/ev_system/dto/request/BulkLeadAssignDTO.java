package com.bentork.ev_system.dto.request;

import java.util.List;

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
public class BulkLeadAssignDTO {

    @JsonAlias({"ids", "selectedLeadIds", "leads", "leadIdList"})
    private List<Long> leadIds;

    @JsonAlias({"ownerId", "adminId", "assignedTo", "ownerAdminId", "targetAdminId"})
    private Long targetOwnerAdminId;

    public Long getEffectiveTargetOwnerAdminId() {
        return targetOwnerAdminId;
    }
}
