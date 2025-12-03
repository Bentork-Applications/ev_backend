package com.bentork.ev_system.controller;

import com.bentork.ev_system.config.JwtUtil;
import com.bentork.ev_system.dto.request.RevenueDTO;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.service.RevenueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/revenue")
public class RevenueController {

    private final RevenueService revenueService;
    private final JwtUtil jwtUtil;
    private final AdminRepository adminRepository;

    public RevenueController(RevenueService revenueService, JwtUtil jwtUtil, AdminRepository adminRepository) {
        this.revenueService = revenueService;
        this.jwtUtil = jwtUtil;
        this.adminRepository = adminRepository;
    }

    private void ensureAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authorization failed - Missing or invalid Authorization header");
            throw new RuntimeException("Missing/invalid Authorization header");
        }
        String email = jwtUtil.extractUsername(authHeader.substring(7));
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Authorization failed - Admin not found: email={}", email);
                    return new RuntimeException("Admin not found");
                });

        if (log.isDebugEnabled()) {
            log.debug("Admin authorized: email={}", email);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<RevenueDTO>> all(@RequestHeader("Authorization") String auth) {
        log.info("GET /api/revenue/all - Request received");

        try {
            ensureAdmin(auth);
            List<RevenueDTO> revenues = revenueService.getAllRevenue();
            log.info("GET /api/revenue/all - Success, returned {} revenue records", revenues.size());
            return ResponseEntity.ok(revenues);
        } catch (RuntimeException e) {
            log.error("GET /api/revenue/all - Failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("GET /api/revenue/all - Failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<RevenueDTO> byId(@PathVariable Long id,
            @RequestHeader("Authorization") String auth) {
        log.info("GET /api/revenue/{} - Request received", id);

        try {
            ensureAdmin(auth);
            RevenueDTO revenue = revenueService.getById(id);
            log.info("GET /api/revenue/{} - Success, amount={}", id, revenue.getAmount());
            return ResponseEntity.ok(revenue);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("GET /api/revenue/{} - Revenue not found", id);
                return ResponseEntity.notFound().build();
            }
            log.error("GET /api/revenue/{} - Authorization failed: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("GET /api/revenue/{} - Failed: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id,
            @RequestHeader("Authorization") String auth) {
        log.info("DELETE /api/revenue/delete/{} - Request received", id);

        try {
            ensureAdmin(auth);
            revenueService.delete(id);
            log.info("DELETE /api/revenue/delete/{} - Success", id);
            return ResponseEntity.ok("Revenue deleted");
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("DELETE /api/revenue/delete/{} - Revenue not found", id);
                return ResponseEntity.notFound().build();
            }
            log.error("DELETE /api/revenue/delete/{} - Authorization failed: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("DELETE /api/revenue/delete/{} - Failed: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Total Revenue
    @GetMapping("/total")
    public ResponseEntity<BigDecimal> getTotalRevenue(
            @RequestHeader("Authorization") String auth) {
        log.info("GET /api/revenue/total - Request received");

        try {
            ensureAdmin(auth);
            BigDecimal total = revenueService.getTotalRevenue();
            log.info("GET /api/revenue/total - Success, total={}", total);
            return ResponseEntity.ok(total);
        } catch (RuntimeException e) {
            log.error("GET /api/revenue/total - Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("GET /api/revenue/total - Failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Pending Revenue
    @GetMapping("/pending")
    public ResponseEntity<BigDecimal> getPendingRevenue(
            @RequestHeader("Authorization") String auth) {
        log.info("GET /api/revenue/pending - Request received");

        try {
            ensureAdmin(auth);
            BigDecimal pending = revenueService.getPendingRevenue();
            log.info("GET /api/revenue/pending - Success, pending={}", pending);
            return ResponseEntity.ok(pending);
        } catch (RuntimeException e) {
            log.error("GET /api/revenue/pending - Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("GET /api/revenue/pending - Failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Total Transactions
    @GetMapping("/transactions/total")
    public ResponseEntity<Long> getTotalTransactions(
            @RequestHeader("Authorization") String auth) {
        log.info("GET /api/revenue/transactions/total - Request received");

        try {
            ensureAdmin(auth);
            Long total = revenueService.getTotalTransactions();
            log.info("GET /api/revenue/transactions/total - Success, total={}", total);
            return ResponseEntity.ok(total);
        } catch (RuntimeException e) {
            log.error("GET /api/revenue/transactions/total - Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("GET /api/revenue/transactions/total - Failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Success Rate
    @GetMapping("/success-rate")
    public ResponseEntity<Double> getSuccessRate(
            @RequestHeader("Authorization") String auth) {
        log.info("GET /api/revenue/success-rate - Request received");

        try {
            ensureAdmin(auth);
            Double successRate = revenueService.getSuccessRate();
            log.info("GET /api/revenue/success-rate - Success, successRate={}%", successRate);
            return ResponseEntity.ok(successRate);
        } catch (RuntimeException e) {
            log.error("GET /api/revenue/success-rate - Authorization failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("GET /api/revenue/success-rate - Failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}