package com.bentork.ev_system.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.PageRequest;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.bentork.ev_system.model.User;
import com.bentork.ev_system.model.WalletTransaction;
import com.bentork.ev_system.repository.UserRepository;
import com.bentork.ev_system.repository.WalletTransactionRepository;

@Service
public class WalletTransactionService {

    @Autowired
    private WalletTransactionRepository repo;

    @Autowired
    private UserRepository userRepo;

    public List<WalletTransaction> getTransactionHistory(Long userId, String type, boolean viewAll) {
        Sort sort = Sort.by("createdAt").descending();

        // If viewAll is true, get UNPAGED list. If false, get page 0 with 10 items.
        Pageable pageable = viewAll ? Pageable.unpaged() : PageRequest.of(0, 10, sort);

        if (type != null && !type.isEmpty()) {
            return repo.findByUserIdAndType(userId, type, pageable).getContent();
        } else {
            return repo.findByUserId(userId, pageable).getContent();
        }
    }

    /**
     * Save a WalletTransaction and update user's wallet balance if the transaction
     * status is "success".
     * (This preserves your existing behaviour.)
     */
    public WalletTransaction save(WalletTransaction tx) {
        WalletTransaction saved = repo.save(tx);

        if ("success".equalsIgnoreCase(saved.getStatus())) {
            userRepo.findById(saved.getUserId()).ifPresent(user -> {
                BigDecimal balance = user.getWalletBalance() != null ? user.getWalletBalance()
                        : BigDecimal.ZERO;
                if ("credit".equalsIgnoreCase(saved.getType())) {
                    user.setWalletBalance(balance.add(saved.getAmount()));
                } else {
                    user.setWalletBalance(balance.subtract(saved.getAmount()));
                }
                userRepo.save(user);
            });
        }

        return saved;
    }

    /**
     * Check whether a user has at least `amount` in wallet.
     */
    public boolean hasBalance(Long userId, BigDecimal amount) {
        if (amount == null)
            return true; // treat null as no-check
        Optional<User> userOpt = userRepo.findById(userId);
        return userOpt.map(u -> {
            BigDecimal balance = u.getWalletBalance() != null ? u.getWalletBalance()
                    : BigDecimal.ZERO;
            return balance.compareTo(amount) >= 0;
        }).orElse(false);
    }

    /**
     * Return user's current wallet balance (or ZERO if user not found / null).
     */
    public BigDecimal getBalance(Long userId) {
        return userRepo.findById(userId)
                .map(u -> u.getWalletBalance() != null ? u.getWalletBalance() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Create and save a debit transaction. Throws RuntimeException when balance is
     * insufficient.
     * sessionId is optional and used only to include in transactionRef for tracing.
     */
    public WalletTransaction debit(Long userId, Long sessionId, BigDecimal amount, String method) {
        if (amount == null)
            throw new IllegalArgumentException("amount cannot be null");
        if (!hasBalance(userId, amount)) {
            throw new RuntimeException("Insufficient wallet balance");
        }

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setSessionId(sessionId);
        tx.setAmount(amount);
        tx.setType("debit");
        tx.setMethod(method != null ? method : "debit");
        tx.setStatus("success"); // mark success so save(...) will update user's balance
        tx.setTransactionRef((sessionId != null ? "sess-" + sessionId + "-" : "") + UUID.randomUUID().toString());

        return save(tx);
    }

    /**
     * Create and save a credit transaction (refund or top-up).
     */
    public WalletTransaction credit(Long userId, Long sessionId, BigDecimal amount, String method) {
        if (amount == null)
            throw new IllegalArgumentException("amount cannot be null");

        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setSessionId(sessionId);
        tx.setAmount(amount);
        tx.setType("credit");
        tx.setMethod(method != null ? method : "credit");
        tx.setStatus("success");
        tx.setTransactionRef((sessionId != null ? "sess-" + sessionId + "-" : "") + UUID.randomUUID().toString());

        return save(tx);
    }

    public void updateSessionIdForUser(Long userId, BigDecimal amount, Long sessionId) {
        // Get the last wallet transaction for this user that matches amount and has no
        // sessionId
        WalletTransaction tx = repo.findTopByUserIdAndAmountAndSessionIdIsNullOrderByCreatedAtDesc(userId, amount)
                .orElseThrow(() -> new RuntimeException("No matching wallet transaction found to attach sessionId"));
        tx.setSessionId(sessionId);
        repo.save(tx);
    }

}
