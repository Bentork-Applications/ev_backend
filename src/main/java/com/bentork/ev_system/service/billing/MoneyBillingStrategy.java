package com.bentork.ev_system.service.billing;

import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Billing strategy for MONEY_BASED sessions.
 * User enters a rupee amount, gets converted to whole kWh.
 */
@Slf4j
@Component
@Order(1) // Try first
public class MoneyBillingStrategy implements BillingStrategy {

    @Override
    public boolean supports(Receipt receipt) {
        return receipt != null && "MONEY_BASED".equals(receipt.getSessionType());
    }

    @Override
    public BillingResult calculate(Session session, Receipt receipt, double energyUsed) {
        BigDecimal effectiveRate = session.getEffectiveRateApplied();
        BigDecimal allocatedKwh = session.getAllocatedKwh();
        BigDecimal chargeableAmount = session.getChargeableAmount();
        
        BigDecimal deliveredKwh = BigDecimal.valueOf(energyUsed);
        
        // Final actual cost
        BigDecimal actualCost = deliveredKwh.multiply(effectiveRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal prepaid = chargeableAmount;
        
        log.info("MONEY_BASED billing: sessionId={}, energyUsed={}, allocatedKwh={}, prepaid={}, actualCost={}",
                session.getId(), energyUsed, allocatedKwh, prepaid, actualCost);

        BillingResult result = new BillingResult();
        result.setPrepaidAmount(prepaid);
        
        if (actualCost.compareTo(prepaid) < 0) {
            BigDecimal refund = prepaid.subtract(actualCost);
            result.setFinalCost(actualCost);
            result.setRefundAmount(refund);
            result.setRefundIssued(true);
            result.setDescription("Early stop refund: ₹" + refund + " (delivered " + 
                String.format("%.2f", energyUsed) + " of " + allocatedKwh + " kWh)");
        } else {
            // Over-delivery (should be prevented by checkAndStopIfReachedKwh)
            // But we cap it at chargeableAmount
            result.setFinalCost(prepaid); 
        }
        
        return result;
    }
}
