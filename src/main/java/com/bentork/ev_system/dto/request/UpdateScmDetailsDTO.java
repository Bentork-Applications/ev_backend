package com.bentork.ev_system.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class UpdateScmDetailsDTO {

    @NotEmpty(message = "At least one barcode is required")
    private List<String> barcodes;

    @NotBlank(message = "Invoice number is required")
    private String invoiceNumber;

    @NotNull(message = "Service warranty (months) is required")
    @Min(value = 0, message = "Service warranty must be 0 or more months")
    private Integer serviceWarrantyMonths;

    @NotNull(message = "Full warranty (months) is required")
    @Min(value = 0, message = "Full warranty must be 0 or more months")
    private Integer fullWarrantyMonths;

    @NotBlank(message = "Tracking ID is required")
    private String trackingId;

    // Getters and Setters

    public List<String> getBarcodes() {
        return barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = barcodes;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public Integer getServiceWarrantyMonths() {
        return serviceWarrantyMonths;
    }

    public void setServiceWarrantyMonths(Integer serviceWarrantyMonths) {
        this.serviceWarrantyMonths = serviceWarrantyMonths;
    }

    public Integer getFullWarrantyMonths() {
        return fullWarrantyMonths;
    }

    public void setFullWarrantyMonths(Integer fullWarrantyMonths) {
        this.fullWarrantyMonths = fullWarrantyMonths;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }
}
