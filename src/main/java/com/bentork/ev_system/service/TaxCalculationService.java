package com.bentork.ev_system.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaxCalculationService {

    @Value("${tax.gst.rate}")
    private BigDecimal gstRate;

    @Value("${tax.pst.rate}")
    private BigDecimal pstRate;

    public BigDecimal calculateGst(BigDecimal amount) {
        return amount.multiply(gstRate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePst(BigDecimal amount) {
        return amount.multiply(pstRate)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
