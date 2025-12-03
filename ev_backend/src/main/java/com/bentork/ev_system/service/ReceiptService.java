package com.bentork.ev_system.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Plan;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.model.User;
import com.bentork.ev_system.repository.ReceiptRepository;

@Service
public class ReceiptService {

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private WalletTransactionService walletTransactionService;

    @Autowired
    private UserNotificationService userNotificationService;

    @Autowired
    @Lazy
    private SessionService sessionService;

    /**
     * Creates a new receipt (PENDING) for either a plan or a kWh package/custom.
     */
    public Receipt createReceipt(User user, Plan plan, Charger charger, BigDecimal selectedKwh) {
        Receipt receipt = new Receipt();
        receipt.setUser(user);
        receipt.setPlan(plan);
        receipt.setCharger(charger);

        BigDecimal amount;
        if (plan != null) {
            amount = plan.getWalletDeduction(); // prepaid amount from plan
        } else {
            amount = selectedKwh.multiply(BigDecimal.valueOf(charger.getRate())); // charger.rate is Double
            receipt.setSelectedKwh(selectedKwh);
        }

        receipt.setAmount(amount);
        receipt.setStatus("Pending");
        receipt.setCreatedAt(LocalDateTime.now());
        return receiptRepository.save(receipt);
    }

    /**
     * Debits wallet, marks receipt PAID, and starts session.
     * If charger fails to start → refund & notify user.
     */
    public Receipt payReceipt(Long receiptId, String boxId) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new RuntimeException("Receipt not found"));

        Long userId = receipt.getUser().getId();
        BigDecimal amount = receipt.getAmount();

        // ✅ Wallet balance check
        if (!walletTransactionService.hasBalance(userId, amount)) {
            userNotificationService.createNotification(
                    userId,
                    "Insufficient Wallet Balance",
                    "You need ₹" + amount + " but your wallet balance is too low. Please top-up.",
                    "Wallet Error");
            throw new RuntimeException("Insufficient wallet balance. Please top-up.");
        }

        // Debit wallet
        walletTransactionService.debit(userId, null, amount, "Charging Payment");
        receipt.setStatus("PAID");
        receipt.setUpdatedAt(LocalDateTime.now());
        receiptRepository.save(receipt);

        // Start session
        Session session = sessionService.startSessionFromReceipt(receipt, boxId);
        receipt.setSession(session);
        walletTransactionService.updateSessionIdForUser(userId, amount, session.getId());

        if ("failed".equalsIgnoreCase(session.getStatus())) {
            userNotificationService.createNotification(
                    userId,
                    "Charging Failed",
                    "Your charging session could not start. You have been refunded ₹" + amount,
                    "Charger Error");
            walletTransactionService.credit(userId, session.getId(), amount, "Charger Refund");
            receipt.setStatus("Refunded");
        }

        return receiptRepository.save(receipt);
    }

    /**
     * Finalizes receipt after session ends.
     */
    public void finalizeReceipt(Session session, BigDecimal finalCost) {
        Receipt receipt = receiptRepository.findBySession(session)
                .orElseThrow(() -> new RuntimeException("Linked receipt not found"));

        receipt.setAmount(finalCost);
        receipt.setStatus("Finalize");
        receipt.setUpdatedAt(LocalDateTime.now());
        receiptRepository.save(receipt);
    }

    public Receipt save(Receipt receipt) {
        return receiptRepository.save(receipt);
    }
}
