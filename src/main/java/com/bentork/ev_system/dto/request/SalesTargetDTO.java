package com.bentork.ev_system.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesTargetDTO {

    @NotNull(message = "Year is required")
    @Min(value = 2020, message = "Year must be 2020 or later")
    @Max(value = 2035, message = "Year must be 2035 or earlier")
    private Integer year;

    @NotNull(message = "Month is required")
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    private Integer month;

    private Double targetRevenue = 0.0;

    private Integer targetLeads = 0;

    private Integer targetDeals = 0;

    private Integer targetUnits = 0;

    // Optional admin ID for personal target; null for overall company target
    private Long adminId;

    private String notes;
}
