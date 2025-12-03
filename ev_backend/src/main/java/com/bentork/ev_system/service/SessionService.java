package com.bentork.ev_system.service;

import com.bentork.ev_system.dto.request.SessionDTO;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.repository.ReceiptRepository;
import com.bentork.ev_system.repository.SessionRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SessionService {

	@Autowired
	private SessionRepository sessionRepository;

	@Autowired
	private ReceiptRepository receiptRepository;

	@Autowired
	private ReceiptService receiptService;

	@Autowired
	private WalletTransactionService walletTransactionService;

	@Autowired
	private AdminNotificationService adminNotificationService;

	@Autowired
	private RevenueService revenueService;

	@Autowired
	private UserNotificationService userNotificationService;

	@Autowired
    private Clock clock;

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

	/**
	 * Start session only if receipt is already PAID.
	 * Auto-stop logic applies for both plan and selected kWh sessions.
	 */
	public Session startSessionFromReceipt(Receipt receipt, String boxId) {
		try {
			log.info("Starting session from receipt: receiptId={}, userId={}, chargerId={}",
					receipt.getId(), receipt.getUser().getId(), receipt.getCharger().getId());

			if (!"PAID".equalsIgnoreCase(receipt.getStatus())) {
				log.warn("Cannot start session - Receipt not paid: receiptId={}, status={}",
						receipt.getId(), receipt.getStatus());
				throw new RuntimeException("Cannot start session without a paid receipt.");
			}

			Session session = new Session();
			session.setUser(receipt.getUser());
			session.setCharger(receipt.getCharger());
			session.setBoxId(boxId);
			session.setStartTime(LocalDateTime.now());
			session.setStatus("active");
			session.setCreatedAt(LocalDateTime.now());
			sessionRepository.save(session);
			session.setSourceType("SESSION");

			receipt.setSession(session);
			receiptRepository.save(receipt);

			// Auto-stop scheduling
			if (receipt.getPlan() != null) {
				// Stop after plan duration
				int durationMin = receipt.getPlan().getDurationMin();
				log.info("Scheduling auto-stop for plan session: sessionId={}, durationMin={}",
						session.getId(), durationMin);
				scheduleAutoStop(session.getId(), durationMin);
			} else if (receipt.getSelectedKwh() != null) {
				// Approximation: minutes required = selectedKwh / 0.075
				double minutesRequired = receipt.getSelectedKwh().doubleValue() / 0.075;
				log.info("Scheduling auto-stop for kWh session: sessionId={}, selectedKwh={}, estimatedMinutes={}",
						session.getId(), receipt.getSelectedKwh(), minutesRequired);
				scheduleAutoStop(session.getId(), (int) Math.ceil(minutesRequired));
			}

			log.info("Session started successfully: sessionId={}, userId={}, chargerId={}, boxId={}",
					session.getId(), session.getUser().getId(), session.getCharger().getId(), boxId);

			adminNotificationService.createSystemNotification(
					"User '" + session.getUser().getName() + "' started a session on charger '" +
							session.getCharger().getOcppId() + "'",
					"SESSION_STARTED");

			userNotificationService.createNotification(
					session.getUser().getId(),
					"Charging Started",
					"Your charging session has started successfully.",
					"INFO");

			return session;
		} catch (Exception e) {
			log.error("Failed to start session from receipt: receiptId={}: {}",
					receipt.getId(), e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Manual stop (by user).
	 */
	public Map<String, Object> stopSession(Long userId, SessionDTO request) {
		try {
			log.info("Manual stop requested: sessionId={}, userId={}", request.getSessionId(), userId);

			Session session = sessionRepository.findById(request.getSessionId())
					.orElseThrow(() -> new RuntimeException("Session not found"));

			if (!session.getUser().getId().equals(userId)) {
				log.warn("Unauthorized stop attempt: sessionId={}, requestedBy={}, owner={}",
						request.getSessionId(), userId, session.getUser().getId());
				throw new RuntimeException("Unauthorized to stop this session");
			}

			if (!"active".equalsIgnoreCase(session.getStatus())) {
				log.info("Session already completed: sessionId={}, status={}",
						request.getSessionId(), session.getStatus());
				return buildAlreadyCompletedResponse(session);
			}

			return finalizeSession(session, "Manual Stop");
		} catch (Exception e) {
			log.error("Failed to stop session: sessionId={}, userId={}: {}",
					request.getSessionId(), userId, e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Auto-stop (triggered by scheduler).
	 */
	public void stopSessionBySystem(Long sessionId) {
		try {
			log.info("Auto-stop triggered by system: sessionId={}", sessionId);

			Session session = sessionRepository.findById(sessionId)
					.orElseThrow(() -> new RuntimeException("Session not found"));

			if ("active".equalsIgnoreCase(session.getStatus())) {
				finalizeSession(session, "Auto Stop");
			} else {
				log.info("Session already inactive, skipping auto-stop: sessionId={}, status={}",
						sessionId, session.getStatus());
			}
		} catch (Exception e) {
			log.error("Failed to auto-stop session: sessionId={}: {}", sessionId, e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Real-time check for selectedKwh session — stop when limit reached.
	 * Call this periodically when you get meter updates from charger.
	 */
	public void checkAndStopIfReachedKwh(Long sessionId, double currentKwh) {
		try {
			Session session = sessionRepository.findById(sessionId)
					.orElseThrow(() -> new RuntimeException("Session not found"));

			Receipt receipt = receiptRepository.findBySession(session).orElse(null);
			if (receipt != null && receipt.getSelectedKwh() != null &&
					"active".equalsIgnoreCase(session.getStatus())) {
				double targetKwh = receipt.getSelectedKwh().doubleValue();

				if (log.isDebugEnabled()) {
					log.debug("Checking kWh limit: sessionId={}, currentKwh={}, targetKwh={}",
							sessionId, currentKwh, targetKwh);
				}

				if (currentKwh >= targetKwh) {
					log.info("kWh limit reached, stopping session: sessionId={}, currentKwh={}, targetKwh={}",
							sessionId, currentKwh, targetKwh);
					finalizeSession(session, "AUTO_STOP_KWH_REACHED");
				}
			}
		} catch (Exception e) {
			log.error("Failed to check kWh limit: sessionId={}, currentKwh={}: {}",
					sessionId, currentKwh, e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Finalize session (shared logic).
	 */
	private Map<String, Object> finalizeSession(Session session, String stopReason) {
		try {
			log.info("Finalizing session: sessionId={}, stopReason={}", session.getId(), stopReason);

			if (!"active".equalsIgnoreCase(session.getStatus())) {
				return buildAlreadyCompletedResponse(session);
			}

			session.setEndTime(LocalDateTime.now());
			session.setStatus("completed");

			Receipt receipt = receiptRepository.findBySession(session).orElse(null);

			double energyUsed;
			BigDecimal finalCostBD;
			boolean refundIssued = false;
			boolean extraDebited = false;

			if (receipt != null && receipt.getSelectedKwh() != null) {
				// Prepaid selected kWh session → always use selectedKwh
				energyUsed = receipt.getSelectedKwh().doubleValue();
				finalCostBD = BigDecimal.valueOf(energyUsed)
						.multiply(BigDecimal.valueOf(session.getCharger().getRate()))
						.setScale(2, RoundingMode.HALF_UP);

				log.info("Using prepaid kWh: sessionId={}, selectedKwh={}, finalCost={}",
						session.getId(), energyUsed, finalCostBD);
			} else {
				// Plan session → calculate actual energy used
				energyUsed = calculateEnergyUsed(session);
				finalCostBD = BigDecimal.valueOf(energyUsed)
						.multiply(BigDecimal.valueOf(session.getCharger().getRate()))
						.setScale(2, RoundingMode.HALF_UP);

				log.info("Calculated energy used: sessionId={}, energyUsed={}, finalCost={}",
						session.getId(), energyUsed, finalCostBD);

				if (receipt != null) {
					BigDecimal prepaid = receipt.getAmount();

					if (finalCostBD.compareTo(prepaid) < 0) {
						BigDecimal refund = prepaid.subtract(finalCostBD);
						walletTransactionService.credit(session.getUser().getId(), session.getId(),
								refund, "Plan session refund");
						refundIssued = true;

						log.info("Refund issued: sessionId={}, prepaid={}, finalCost={}, refund={}",
								session.getId(), prepaid, finalCostBD, refund);

						userNotificationService.createNotification(
								session.getUser().getId(),
								"Refund Issued",
								"Unused amount ₹" + refund + " has been refunded to your wallet.",
								"REFUND");
					} else if (finalCostBD.compareTo(prepaid) > 0) {
						BigDecimal extra = finalCostBD.subtract(prepaid);
						walletTransactionService.debit(session.getUser().getId(), session.getId(),
								extra, "Plan Session Extra Debit");
						extraDebited = true;

						log.info("Extra debit: sessionId={}, prepaid={}, finalCost={}, extra={}",
								session.getId(), prepaid, finalCostBD, extra);

						userNotificationService.createNotification(
								session.getUser().getId(),
								"Extra Debit",
								"Extra amount ₹" + extra + " has been deducted due to higher usage.",
								"Debit");
					}
				}
			}

			session.setEnergyKwh(energyUsed);
			session.setCost(finalCostBD.doubleValue());
			sessionRepository.save(session);

			if (receipt != null) {
				receiptService.finalizeReceipt(session, finalCostBD);
			}

			Duration duration = Duration.between(session.getStartTime(), session.getEndTime());
			log.info(
					"Session completed: sessionId={}, userId={}, energyUsed={}, finalCost={}, duration={} minutes, stopReason={}",
					session.getId(), session.getUser().getId(), energyUsed, finalCostBD,
					duration.toMinutes(), stopReason);

			adminNotificationService.createSystemNotification(
					"User '" + session.getUser().getName() + "' stopped session. Energy used: " +
							String.format("%.2f", energyUsed) + " kWh, Final cost: ₹" + finalCostBD,
					"Session Completed");

			userNotificationService.createNotification(
					session.getUser().getId(),
					"Charging Stopped",
					"Your session has ended (" + stopReason + "). Total cost: ₹" + finalCostBD,
					"INFO");

			revenueService.recordRevenueForSession(session,
					finalCostBD.doubleValue(), "Wallet", null, "success");

			Map<String, Object> response = new HashMap<>();
			response.put("sessionId", session.getId());
			response.put("energyUsed", energyUsed);
			response.put("finalCost", finalCostBD);
			response.put("refundIssued", refundIssued);
			response.put("extraDebited", extraDebited);
			response.put("message", "Session completed (" + stopReason + ")" +
					(refundIssued ? " - Refund issued" : extraDebited ? " - Extra debited" : ""));
			return response;
		} catch (Exception e) {
			log.error("Failed to finalize session: sessionId={}, stopReason={}: {}",
					session.getId(), stopReason, e.getMessage(), e);
			throw e;
		}
	}

	private Map<String, Object> buildAlreadyCompletedResponse(Session session) {
		Map<String, Object> response = new HashMap<>();
		response.put("sessionId", session.getId());
		response.put("energyUsed", session.getEnergyKwh());
		response.put("finalCost", session.getCost());
		response.put("message", "Session already completed. No action taken.");
		return response;
	}

	private double calculateEnergyUsed(Session session) {
		Duration duration = Duration.between(session.getStartTime(), session.getEndTime());
		long minutes = duration.toMinutes();
		return minutes * 0.075;
	}

	private void scheduleAutoStop(Long sessionId, int durationMin) {
		scheduler.schedule(() -> {
			try {
				stopSessionBySystem(sessionId);
			} catch (Exception e) {
				log.error("Auto-stop scheduler error: sessionId={}: {}", sessionId, e.getMessage(), e);
			}
		}, durationMin, TimeUnit.MINUTES);
	}

	// Count total completed sessions
	public long getTotalSessions() {
		try {
			long total = sessionRepository.findAll().stream()
					.filter(s -> "completed".equalsIgnoreCase(s.getStatus()))
					.count();

			if (log.isDebugEnabled()) {
				log.debug("Total completed sessions: {}", total);
			}

			return total;
		} catch (Exception e) {
			log.error("Failed to get total sessions: {}", e.getMessage(), e);
			throw e;
		}
	}

	// Sum total energy consumed (kWh)
	public double getTotalEnergyConsumed() {
		try {
			double totalEnergy = sessionRepository.findAll().stream()
					.filter(s -> "completed".equalsIgnoreCase(s.getStatus()))
					.mapToDouble(Session::getEnergyKwh)
					.sum();

			if (log.isDebugEnabled()) {
				log.debug("Total energy consumed: {} kWh", totalEnergy);
			}

			return totalEnergy;
		} catch (Exception e) {
			log.error("Failed to get total energy consumed: {}", e.getMessage(), e);
			throw e;
		}
	}

	// Active Sessions - Sessions that are currently in progress
	public Long getActiveSessions() {
		try {
			Long activeCount = sessionRepository.findAll().stream()
					.filter(session -> "active".equalsIgnoreCase(session.getStatus()))
					.count();

			if (log.isDebugEnabled()) {
				log.debug("Active sessions count: {}", activeCount);
			}

			return activeCount;
		} catch (Exception e) {
			log.error("Failed to get active sessions: {}", e.getMessage(), e);
			throw e;
		}
	}

	// Average Uptime - Based on successful vs total sessions
	public Double getAverageUptime() {
		try {
			long totalSessions = sessionRepository.count();

			if (totalSessions == 0) {
				log.warn("No sessions found for uptime calculation");
				return 0.0;
			}

			long completedSessions = sessionRepository.findAll().stream()
					.filter(session -> "completed".equalsIgnoreCase(session.getStatus()))
					.count();

			double uptime = (completedSessions * 100.0) / totalSessions;
			double roundedUptime = Math.round(uptime * 100.0) / 100.0;

			log.info("Average uptime calculated: {}% (completed={}, total={})",
					roundedUptime, completedSessions, totalSessions);

			return roundedUptime;
		} catch (Exception e) {
			log.error("Failed to calculate average uptime: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Get session by ID (used by OCPP WebSocket)
	 */
	public Session getSessionById(Long sessionId) {
		if (log.isDebugEnabled()) {
			log.debug("Fetching session by ID: sessionId={}", sessionId);
		}
		return sessionRepository.findById(sessionId).orElse(null);
	}

	/**
	 * Find any active session (fallback for OCPP)
	 */
	public Optional<Session> findLastActiveSession() {
		if (log.isDebugEnabled()) {
			log.debug("Finding last active session");
		}
		return sessionRepository.findFirstByStatusOrderByStartTimeDesc("active");
	}

	// Total error today count
    public Long getTodaysErrorCount() {
        try {
            LocalDate today = LocalDate.now(clock);
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.atTime(23, 59, 59, 999999999);

            log.debug("Getting all session to count todays errors");
            List<Session> allSessions = sessionRepository.findAll();

            return allSessions.stream()
                    .filter(session -> session.getCreatedAt() != null
                    && (session.getCreatedAt().isEqual(startOfDay)
                    || (session.getCreatedAt().isAfter(startOfDay)
                    && session.getCreatedAt().isBefore(endOfDay))))
                    .filter(session -> session.getStatus() != null
                    && session.getStatus().toLowerCase().contains("error"))
                    .count();

        } catch (DataAccessException e) {
            log.error("Error while accessing data: {}", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in getTodaysErrorCount ", e);
            throw new RuntimeException("Failed to calculate today's error count", e);
        }
    }

    //All session records
    public List<Session> getallSessionRecords() {
        try {
            log.debug("Getting all session records");
            List<Session> allRecords = sessionRepository.findAll();
            return allRecords;
        } catch (DataAccessException e) {
            log.error("Error while accessing data: {}", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error ", e);
            throw e;
        }
    }
}