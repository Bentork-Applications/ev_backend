package com.bentork.ev_system.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaxCalculationService {

    @Value("${tax.gst.rate}")
    private BigDecimal gstRate;

    public BigDecimal calculateGst(BigDecimal amount) {
        return amount.multiply(gstRate)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates PST based on per-kWh fixed rate set on each charger.
     * Formula: max(1, floor(energyUsed)) × pstPerKwh
     *
     * Examples (pstPerKwh = ₹5):
     *   0.02 kWh → max(1, 0) = 1 → ₹5
     *   1.50 kWh → max(1, 1) = 1 → ₹5
     *   2.01 kWh → max(1, 2) = 2 → ₹10
     *   3.00 kWh → max(1, 3) = 3 → ₹15
     */
    public BigDecimal calculatePstPerKwh(double energyUsed, Double pstPerKwh) {
        if (pstPerKwh == null || pstPerKwh <= 0) {
            return BigDecimal.ZERO;
        }
        int units = Math.max(1, (int) Math.floor(energyUsed));
        return BigDecimal.valueOf(units)
                .multiply(BigDecimal.valueOf(pstPerKwh))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
