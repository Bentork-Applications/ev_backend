package com.bentork.ev_system.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class BatteryDataResponse {

    private Long id;
    private String customerName;
    private String productDetails;
    private String invoiceNumber;
    private String barcode;
    private String address;

    // Full Warranty (Replacement)
    private LocalDate warrantyStartDate;
    private LocalDate warrantyEndDate;
    private boolean fullWarrantyActive;

    // Service Warranty (Repair/Servicing)
    private LocalDate serviceWarrantyStartDate;
    private LocalDate serviceWarrantyEndDate;
    private boolean serviceWarrantyActive;

    // Overall warranty status: "full_warranty", "service_warranty", or "expired"
    private String activeWarrantyType;

    private String createdByAdminEmail;
    private LocalDateTime createdAt;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getProductDetails() {
        return productDetails;
    }

    public void setProductDetails(String productDetails) {
        this.productDetails = productDetails;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }


    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }



    public LocalDate getWarrantyStartDate() {
        return warrantyStartDate;
    }

    public void setWarrantyStartDate(LocalDate warrantyStartDate) {
        this.warrantyStartDate = warrantyStartDate;
    }

    public LocalDate getWarrantyEndDate() {
        return warrantyEndDate;
    }

    public void setWarrantyEndDate(LocalDate warrantyEndDate) {
        this.warrantyEndDate = warrantyEndDate;
    }

    public boolean isFullWarrantyActive() {
        return fullWarrantyActive;
    }

    public void setFullWarrantyActive(boolean fullWarrantyActive) {
        this.fullWarrantyActive = fullWarrantyActive;
    }

    public LocalDate getServiceWarrantyStartDate() {
        return serviceWarrantyStartDate;
    }

    public void setServiceWarrantyStartDate(LocalDate serviceWarrantyStartDate) {
        this.serviceWarrantyStartDate = serviceWarrantyStartDate;
    }

    public LocalDate getServiceWarrantyEndDate() {
        return serviceWarrantyEndDate;
    }

    public void setServiceWarrantyEndDate(LocalDate serviceWarrantyEndDate) {
        this.serviceWarrantyEndDate = serviceWarrantyEndDate;
    }

    public boolean isServiceWarrantyActive() {
        return serviceWarrantyActive;
    }

    public void setServiceWarrantyActive(boolean serviceWarrantyActive) {
        this.serviceWarrantyActive = serviceWarrantyActive;
    }

    public String getActiveWarrantyType() {
        return activeWarrantyType;
    }

    public void setActiveWarrantyType(String activeWarrantyType) {
        this.activeWarrantyType = activeWarrantyType;
    }

    public String getCreatedByAdminEmail() {
        return createdByAdminEmail;
    }

    public void setCreatedByAdminEmail(String createdByAdminEmail) {
        this.createdByAdminEmail = createdByAdminEmail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
