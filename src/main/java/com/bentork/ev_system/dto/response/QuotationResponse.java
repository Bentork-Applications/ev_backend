package com.bentork.ev_system.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuotationResponse {

    private Long id;

    @JsonProperty("quoteNumber")
    @JsonAlias({"quotationNumber", "quote_number"})
    private String quoteNumber;

    private Long opportunityId;
    private String opportunityTitle;
    private Long companyId;
    private String companyName;
    private Integer version;
    private String status;
    private Double totalAmount;
    private Double discount;
    private Double finalAmount;
    private LocalDate validUntil;
    private String termsAndConditions;
    private List<QuotationItemResponse> items;
    private String createdByAdminEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Compatibility alias methods for quotationNumber.
     * Ensures both getQuoteNumber/setQuoteNumber and getQuotationNumber/setQuotationNumber compile.
     */
    public String getQuotationNumber() {
        return this.quoteNumber;
    }

    public void setQuotationNumber(String quotationNumber) {
        this.quoteNumber = quotationNumber;
    }

    @Getter
    @Setter
    public static class QuotationItemResponse {
        private Long id;
        private Long productId;
        private String productDescription;
        private Integer quantity;
        private Double unitPrice;
        private Double totalPrice;
    }
}
