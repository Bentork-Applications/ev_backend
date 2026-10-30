package com.bentork.ev_system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateContactDTO {

    private Long companyId;
    private String name;
    private String designation;
    private String phone;
    private String email;

    @JsonProperty("isPrimary")
    @JsonAlias({"primary", "isPrimary"})
    private boolean isPrimary;
}
