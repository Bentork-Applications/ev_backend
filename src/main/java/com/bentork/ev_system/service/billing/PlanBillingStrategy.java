package com.bentork.ev_system.service.billing;

import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Billing strategy for time-based plan sessions.
 * User selects a plan with a fixed duration and pays upfront.
 */
@Slf4j
@Component
public class PlanBillingStrategy implements BillingStrategy {

    @Override
    public boolean supports(Receipt receipt) {
        return receipt != null && receipt.getPlan() != null;
    }

    @Override
    public BillingResult calculate(Session session, Receipt receipt, double energyUsed) {
        BigDecimal rate = BigDecimal.valueOf(session.getCharger().getRate());

        // Platform fee based on ACTUAL energy used (plans have no selectedKwh)
        Double feePerKwh = session.getCharger().getPlatformFeePerKwh() != null
                ? session.getCharger().getPlatformFeePerKwh() : 0.0;
        BigDecimal platformFee = BigDecimal.valueOf(energyUsed)
                .multiply(BigDecimal.valueOf(feePerKwh))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal energyCost = BigDecimal.valueOf(energyUsed).multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalCost = energyCost.add(platformFee);
        BigDecimal prepaid = receipt.getAmount();

        log.info("Plan billing: sessionId={}, prepaid={}, finalCost={}, energyUsed={}, platformFee={}",
                session.getId(), prepaid, finalCost, energyUsed, platformFee);

        BillingResult result = new BillingResult();
        result.setFinalCost(finalCost);
        result.setPrepaidAmount(prepaid);
        result.setPlatformFee(platformFee);

        if (finalCost.compareTo(prepaid) < 0) {
            BigDecimal refund = prepaid.subtract(finalCost);
            result.setRefundAmount(refund);
            result.setRefundIssued(true);
            result.setDescription("Unused amount ₹" + refund + " has been refunded to your wallet. (Platform fee: ₹" + platformFee + ")");
        } else if (finalCost.compareTo(prepaid) > 0) {
            BigDecimal extra = finalCost.subtract(prepaid);
            result.setExtraDebit(extra);
            result.setExtraDebited(true);
            result.setDescription("Extra amount ₹" + extra + " has been deducted due to higher usage. (Platform fee: ₹" + platformFee + ")");
        }
        return result;
    }
}
