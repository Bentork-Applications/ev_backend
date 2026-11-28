package com.bentork.ev_system.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateLeadDTO {

    private String title;
    private String source;
    private Long companyId;
    private Long contactId;

    @JsonAlias({"contactPhone", "contact_phone", "mobile", "mobileNumber"})
    private String phone;

    @JsonAlias({"contactEmail", "contact_email"})
    private String email;

    @JsonAlias({"contact_name"})
    private String contactName;

    private String city;
    private String state;
    private Double estimatedValue;
    private String notes;
    private String nextFollowUpDate; // yyyy-MM-dd
    private String nextFollowUpTime; // HH:mm or HH:mm:ss
}
