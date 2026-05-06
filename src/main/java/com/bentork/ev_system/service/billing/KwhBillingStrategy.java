package com.bentork.ev_system.service.billing;

import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Billing strategy for kWh-based (power package) sessions.
 * User selects a specific amount of kWh to purchase upfront.
 */
@Slf4j
@Component
public class KwhBillingStrategy implements BillingStrategy {

    @Override
    public boolean supports(Receipt receipt) {
        return receipt != null && receipt.getSelectedKwh() != null;
    }

    @Override
    public BillingResult calculate(Session session, Receipt receipt, double energyUsed) {
        BigDecimal rate = BigDecimal.valueOf(session.getCharger().getRate());
        double selectedKwh = receipt.getSelectedKwh().doubleValue();

        BigDecimal finalCost = BigDecimal.valueOf(energyUsed).multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal prepaid = BigDecimal.valueOf(selectedKwh).multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("kWh billing: sessionId={}, selectedKwh={}, actualKwh={}, prepaid={}, finalCost={}",
                session.getId(), selectedKwh, energyUsed, prepaid, finalCost);

        BillingResult result = new BillingResult();
        result.setFinalCost(finalCost);
        result.setPrepaidAmount(prepaid);

        if (finalCost.compareTo(prepaid) < 0) {
            BigDecimal refund = prepaid.subtract(finalCost);
            result.setRefundAmount(refund);
            result.setRefundIssued(true);
            result.setDescription("Unused energy refund: ₹" + refund + " (Used " +
                    String.format("%.2f", energyUsed) + " kWh of " + String.format("%.2f", selectedKwh) + " kWh selected)");
        } else if (finalCost.compareTo(prepaid) > 0) {
            BigDecimal extra = finalCost.subtract(prepaid);
            result.setExtraDebit(extra);
            result.setExtraDebited(true);
            result.setDescription("Extra amount ₹" + extra + " deducted. (Used " +
                    String.format("%.2f", energyUsed) + " kWh, exceeded " + String.format("%.2f", selectedKwh) + " kWh selected)");
        }
        return result;
    }
}
