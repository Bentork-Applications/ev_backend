package com.bentork.ev_system.service;

import com.bentork.ev_system.enums.SessionStatus;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.repository.ReceiptRepository;
import com.bentork.ev_system.repository.SessionRepository;
import com.bentork.ev_system.service.billing.BillingResult;
import com.bentork.ev_system.service.billing.BillingStrategyFactory;
import com.bentork.ev_system.service.billing.BillingStrategy;
import com.bentork.ev_system.service.interfaces.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles session finalization using the Strategy Pattern for billing.
 * Delegates billing calculations to BillingStrategy implementations
 * selected via BillingStrategyFactory.
 *
 * After Phase 6: billing logic is ~15 lines instead of ~80 lines of if/else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionFinalizationService implements ISessionFinalizationService {

    private final SessionRepository sessionRepository;
    private final ReceiptRepository receiptRepository;
    private final IReceiptService receiptService;
    private final IWalletTransactionService walletTransactionService;
    private final IAdminNotificationService adminNotificationService;
    private final IRevenueService revenueService;
    private final IUserNotificationService userNotificationService;
    private final ICoinService coinService;
    private final ReferralService referralService;
    private final IEnergyCalculationService energyCalculationService;
    private final BillingStrategyFactory billingStrategyFactory;

    @Override
    public Map<String, Object> finalizeSession(Session session, String stopReason) {
        try {
            log.info("Finalizing session: sessionId={}, stopReason={}", session.getId(), stopReason);

            if (!SessionStatus.ACTIVE.matches(session.getStatus())) {
                return buildAlreadyCompletedResponse(session);
            }

            session.setEndTime(LocalDateTime.now());
            session.setStatus(SessionStatus.COMPLETED.getValue());

            Receipt receipt = receiptRepository.findBySession(session).orElse(null);
            double energyUsed = energyCalculationService.resolveEnergy(session);

            // ========== STRATEGY PATTERN: Billing Calculation ==========
            BillingResult billing;
            BillingStrategy strategy = billingStrategyFactory.getStrategy(receipt);

            if (strategy != null) {
                billing = strategy.calculate(session, receipt, energyUsed);
            } else {
                // Fallback: no receipt, calculate cost without refund/debit logic
                BigDecimal finalCost = BigDecimal.valueOf(energyUsed)
                        .multiply(BigDecimal.valueOf(session.getCharger().getRate()))
                        .setScale(2, RoundingMode.HALF_UP);
                billing = new BillingResult();
                billing.setFinalCost(finalCost);
                log.info("No billing strategy matched (no receipt): sessionId={}, finalCost={}",
                        session.getId(), finalCost);
            }

            // ========== Apply Wallet Refund/Debit ==========
            if (billing.isRefundIssued() && billing.getRefundAmount() != null) {
                walletTransactionService.credit(session.getUser().getId(), session.getId(),
                        billing.getRefundAmount(),
                        receipt.getSelectedKwh() != null ? "power session refund - unused energy" : "Plan session refund");
                log.info("Refund issued: sessionId={}, refund={}", session.getId(), billing.getRefundAmount());

                userNotificationService.createNotification(
                        session.getUser().getId(), "Refund Issued",
                        billing.getDescription(), "REFUND");
            } else if (billing.isExtraDebited() && billing.getExtraDebit() != null) {
                walletTransactionService.debit(session.getUser().getId(), session.getId(),
                        billing.getExtraDebit(),
                        receipt.getSelectedKwh() != null ? "kWh Session Extra Debit - exceeded selected energy" : "Plan Session Extra Debit");
                log.info("Extra debit: sessionId={}, extra={}", session.getId(), billing.getExtraDebit());

                userNotificationService.createNotification(
                        session.getUser().getId(), "Extra Debit",
                        billing.getDescription(), "Debit");
            }

            // ========== Persist Session ==========
            session.setEnergyKwh(energyUsed);
            session.setCost(billing.getFinalCost().doubleValue());
            if (billing.getPlatformFee() != null) {
                session.setPlatformFee(billing.getPlatformFee().doubleValue());
            }
            sessionRepository.save(session);

            if (receipt != null) {
                receiptService.finalizeReceipt(session, billing.getFinalCost());
            }

            // ========== Coin & Referral Rewards ==========
            try {
                coinService.awardChargingCoins(session.getUser().getId(), energyUsed, session.getId());
                referralService.processFirstSessionCompletion(session.getUser().getId(), session.getId());
            } catch (Exception coinEx) {
                log.error("Failed to process coin/referral rewards for sessionId={}: {}",
                        session.getId(), coinEx.getMessage(), coinEx);
            }

            // ========== Notifications & Revenue ==========
            Duration duration = Duration.between(session.getStartTime(), session.getEndTime());
            log.info("Session completed: sessionId={}, userId={}, energyUsed={}, finalCost={}, duration={} minutes, stopReason={}",
                    session.getId(), session.getUser().getId(), String.format("%.3f", energyUsed),
                    billing.getFinalCost(), duration.toMinutes(), stopReason);

            adminNotificationService.createSystemNotification(
                    "User '" + session.getUser().getName() + "' stopped session. Energy used: " +
                            String.format("%.2f", energyUsed) + " kWh, Final cost: ₹" + billing.getFinalCost(),
                    "Session Completed");

            userNotificationService.createNotification(
                    session.getUser().getId(), "Charging Stopped",
                    "Your session has ended (" + stopReason + "). Total cost: ₹" + billing.getFinalCost(),
                    "INFO");

            revenueService.recordRevenueForSession(session,
                    billing.getFinalCost().doubleValue(), "Wallet", null, "success");

            // ========== Build Response ==========
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", session.getId());
            response.put("energyUsed", energyUsed);
            response.put("finalCost", billing.getFinalCost());
            response.put("platformFee", billing.getPlatformFee());
            response.put("refundIssued", billing.isRefundIssued());
            response.put("extraDebited", billing.isExtraDebited());
            response.put("message", "Session completed (" + stopReason + ")" +
                    (billing.isRefundIssued() ? " - Refund issued" : billing.isExtraDebited() ? " - Extra debited" : ""));
            return response;

        } catch (Exception e) {
            log.error("Failed to finalize session: sessionId={}, stopReason={}: {}",
                    session.getId(), stopReason, e.getMessage(), e);
            try {
                session.setStatus(SessionStatus.FAILED.getValue());
                session.setEndTime(LocalDateTime.now());
                sessionRepository.save(session);
                log.info("Session {} marked as FAILED due to finalization error", session.getId());
            } catch (Exception saveEx) {
                log.error("CRITICAL: Failed to save session failure status for session {}: {}",
                        session.getId(), saveEx.getMessage());
            }
            throw e;
        }
    }

    @Override
    public Map<String, Object> buildAlreadyCompletedResponse(Session session) {
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getId());
        response.put("energyUsed", session.getEnergyKwh());
        response.put("finalCost", session.getCost());
        response.put("message", "Session already completed. No action taken.");
        return response;
    }
}
