package com.bentork.ev_system.service.billing;

import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.service.TaxCalculationService;
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

    private final TaxCalculationService taxService;

    public PlanBillingStrategy(TaxCalculationService taxService) {
        this.taxService = taxService;
    }

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
        
        BigDecimal pst = taxService.calculatePstPerKwh(energyUsed, session.getCharger().getPstPerKwh());
        BigDecimal finalCost = energyCost.add(platformFee).add(pst);
        
        BigDecimal prepaid = receipt.getAmount();

        log.info("Plan billing: sessionId={}, prepaid={}, finalCost={}, energyUsed={}, platformFee={}, pst={}",
                session.getId(), prepaid, finalCost, energyUsed, platformFee, pst);

        BillingResult result = new BillingResult();
        result.setFinalCost(finalCost);
        result.setPrepaidAmount(prepaid);
        result.setPlatformFee(platformFee);
        result.setPstAmount(pst);

        if (finalCost.compareTo(prepaid) < 0) {
            BigDecimal refund = prepaid.subtract(finalCost);
            result.setRefundAmount(refund);
            result.setRefundIssued(true);
            result.setDescription("Unused amount ₹" + refund + " has been refunded to your wallet. (Platform fee: ₹" + platformFee + ", PST: ₹" + pst + ")");
        } else if (finalCost.compareTo(prepaid) > 0) {
            BigDecimal extra = finalCost.subtract(prepaid);
            result.setExtraDebit(extra);
            result.setExtraDebited(true);
            result.setDescription("Extra amount ₹" + extra + " has been deducted due to higher usage. (Platform fee: ₹" + platformFee + ", PST: ₹" + pst + ")");
        }
        return result;
    }
}
