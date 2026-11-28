package com.bentork.ev_system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateLeadStatusDTO {

    private String status;

    @JsonAlias({"reason"})
    private String lostReason;
}
