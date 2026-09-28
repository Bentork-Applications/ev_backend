package com.bentork.ev_system.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRequirementDTO {

    private Long leadId;
    private Long companyId;
    private String productCategory;
    private String voltage;
    private String capacity;
    private String chemistry;
    private Integer quantity;
    private String timeline;
    private Double budget;
    private String notes;
}
