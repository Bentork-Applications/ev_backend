package com.bentork.ev_system.service;

import com.bentork.ev_system.dto.request.SessionDTO;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.repository.ReceiptRepository;
import com.bentork.ev_system.repository.SessionRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy; // ✅ CORRECT IMPORT
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

	@Autowired
	@Lazy // ✅ FIXED: This prevents circular dependency
	private OcppWebSocketServer ocppWebSocketServer;

	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

	// REPLACE YOUR EXISTING startSessionFromReceipt METHOD WITH THIS ONE

	/**
	 * Start session only if receipt is already PAID.
	 * Sends RemoteStartTransaction to physical charger.
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

			// Check if charger is already in use
			// Check if charger is already in use
			List<String> activeStatuses = List.of("active");
			Optional<Session> busySession = sessionRepository.findFirstByChargerAndStatusInOrderByCreatedAtDesc(
					receipt.getCharger(), activeStatuses);

			if (busySession.isPresent()) {
				log.warn("Charger {} is busy with session {}", receipt.getCharger().getId(), busySession.get().getId());
				throw new RuntimeException("Charger is currently in use. Please wait.");
			}

			// Create session in database first
			Session session = new Session();
			session.setUser(receipt.getUser());
			session.setCharger(receipt.getCharger());
			session.setBoxId(boxId);
			session.setStartTime(LocalDateTime.now());
			session.setStatus("INITIATED"); // Will become 'active' when charger responds
			session.setCreatedAt(LocalDateTime.now());
			session.setSourceType("SESSION");
			sessionRepository.save(session);

			// Link receipt to session
			receipt.setSession(session);
			receiptRepository.save(receipt);

			log.info("Session created in DB: sessionId={}, status=INITIATED", session.getId());

			// Send RemoteStartTransaction with correct idTag format
			String ocppId = session.getCharger().getOcppId();
			if (ocppId != null && !ocppId.isEmpty()) {
				try {
					com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
					// Use "SESSION_" prefix so handleStartTransaction recognizes it
					payload.put("idTag", "SESSION_" + session.getId());
					payload.put("connectorId", 1);

					log.info("Sending RemoteStartTransaction to {}: idTag=SESSION_{}, connectorId=1",
							ocppId, session.getId());

					boolean sent = ocppWebSocketServer.sendRemoteCommand(ocppId, "RemoteStartTransaction", payload);

					if (sent) {
						log.info("✅ RemoteStartTransaction sent successfully to charger: {}", ocppId);

						userNotificationService.createNotification(
								session.getUser().getId(),
								"Charging Command Sent",
								"Start command sent to charger. Please ensure cable is connected.",
								"INFO");
					} else {
						log.error("❌ Failed to send RemoteStartTransaction: Charger {} not connected", ocppId);
						handleOfflineSession(session, receipt);
					}
				} catch (Exception e) {
					log.error("❌ Error sending RemoteStartTransaction to {}: {}", ocppId, e.getMessage(), e);

					userNotificationService.createNotification(
							session.getUser().getId(),
							"Start Command Failed",
							"Failed to send start command to charger. Error: " + e.getMessage(),
							"ERROR");
				}
			} else {
				log.error("❌ Cannot send RemoteStartTransaction: charger OCPP ID is null/empty");
				handleOfflineSession(session, receipt);
			}

			// Schedule auto-stop
			if (receipt.getPlan() != null) {
				// For TIME based plans, we stop exactly when time is up
				int durationMin = receipt.getPlan().getDurationMin();
				log.info("Scheduling auto-stop for TIME plan: sessionId={}, durationMin={}",
						session.getId(), durationMin);
				scheduleAutoStop(session.getId(), durationMin);

			} else if (receipt.getSelectedKwh() != null) {
				// ✅ FIX: For kWh packages, do NOT stop based on estimated time.
				// Charging speed varies (grid, car onboard charger, etc).
				// We rely on MeterValues to stop accurately.
				// We set a 24-hour "Safety Fallback" just in case.

				int safetyBufferMinutes = 24 * 60;

				log.info(
						"Scheduling safety fallback for kWh session: sessionId={}, selectedKwh={}, safetyTimeout={} mins",
						session.getId(), receipt.getSelectedKwh(), safetyBufferMinutes);

				scheduleAutoStop(session.getId(), safetyBufferMinutes);
			}

			adminNotificationService.createSystemNotification(
					"User '" + session.getUser().getName() + "' initiated session on charger '" +
							session.getCharger().getOcppId() + "'",
					"SESSION_INITIATED");

			return session;

		} catch (Exception e) {
			log.error("Failed to start session from receipt: receiptId={}: {}",
					receipt.getId(), e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Manual stop (by user) - Also sends RemoteStopTransaction to charger
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

			// ✅ NEW: Send RemoteStopTransaction to physical charger
			try {
				String ocppId = session.getCharger().getOcppId();
				int transactionId = session.getId().intValue();

				com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
				payload.put("transactionId", transactionId);

				boolean sent = ocppWebSocketServer.sendRemoteCommand(ocppId, "RemoteStopTransaction", payload);

				if (sent) {
					log.info("✅ RemoteStopTransaction sent to charger: {}, txId: {}", ocppId, transactionId);
				} else {
					log.warn("⚠️ Failed to send RemoteStopTransaction, but continuing with session finalization");
				}
			} catch (Exception e) {
				log.error("Error sending RemoteStopTransaction: {}", e.getMessage());
				// Continue with session finalization even if remote stop fails
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
				// ✅ NEW: Send RemoteStopTransaction for auto-stop too
				try {
					String ocppId = session.getCharger().getOcppId();
					int transactionId = session.getId().intValue();

					com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
					payload.put("transactionId", transactionId);

					ocppWebSocketServer.sendRemoteCommand(ocppId, "RemoteStopTransaction", payload);
					log.info("✅ RemoteStopTransaction sent for auto-stop: sessionId={}", sessionId);
				} catch (Exception e) {
					log.error("Error sending RemoteStopTransaction for auto-stop: {}", e.getMessage());
				}

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

					// Send remote stop
					try {
						String ocppId = session.getCharger().getOcppId();
						com.fasterxml.jackson.databind.node.ObjectNode payload = objectMapper.createObjectNode();
						payload.put("transactionId", session.getId().intValue());
						ocppWebSocketServer.sendRemoteCommand(ocppId, "RemoteStopTransaction", payload);
					} catch (Exception e) {
						log.error("Error sending RemoteStopTransaction for kWh limit: {}", e.getMessage());
					}

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
				energyUsed = receipt.getSelectedKwh().doubleValue();
				finalCostBD = BigDecimal.valueOf(energyUsed)
						.multiply(BigDecimal.valueOf(session.getCharger().getRate()))
						.setScale(2, RoundingMode.HALF_UP);

				log.info("Using prepaid kWh: sessionId={}, selectedKwh={}, finalCost={}",
						session.getId(), energyUsed, finalCostBD);
			} else {
				// 1. Prioritize actual meter reading from OCPP if available
				if (session.getEnergyKwh() > 0.001) {
					energyUsed = session.getEnergyKwh();
					log.info("Using actual meter reading: sessionId={}, energyUsed={}", session.getId(), energyUsed);
				} else {
					// 2. Fallback to calculation if no meter values
					energyUsed = calculateEnergyUsed(session);
					log.info("Calculated energy used (fallback): sessionId={}, energyUsed={}",
							session.getId(), energyUsed);
				}

				finalCostBD = BigDecimal.valueOf(energyUsed)
						.multiply(BigDecimal.valueOf(session.getCharger().getRate()))
						.setScale(2, RoundingMode.HALF_UP);

				log.info("Final cost calculation: sessionId={}, energyUsed={}, finalCost={}",
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
					session.getId(), session.getUser().getId(), String.format("%.3f", energyUsed), finalCostBD,
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

		// FIX: Enforce configuration. Do not fallback to hardcoded values.
		Double chargerSpeedKw = session.getCharger().getKwOutput();
		if (chargerSpeedKw == null || chargerSpeedKw <= 0) {
			String errorMsg = String.format(
					"CRITICAL CONFIG ERROR: Charger %s (ID: %d) has NO kwOutput configured. Cannot calculate energy.",
					session.getCharger().getOcppId(), session.getCharger().getId());
			log.error(errorMsg);
			throw new IllegalStateException(errorMsg);
		}

		double hours = minutes / 60.0;
		double energy = hours * chargerSpeedKw;

		// Round to 3 decimal places for precision
		return Math.round(energy * 1000.0) / 1000.0;
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

	private void handleOfflineSession(Session session, Receipt receipt) {
		log.warn("Handling offline session failure for sessionId={}", session.getId());

		// Refund logic
		if (receipt.getAmount() != null && receipt.getAmount().compareTo(BigDecimal.ZERO) > 0) {
			walletTransactionService.credit(
					session.getUser().getId(),
					session.getId(),
					receipt.getAmount(),
					"Refund: Charger Offline");
			log.info("Refunded {} to user {} due to offline charger",
					receipt.getAmount(), session.getUser().getId());
		}

		userNotificationService.createNotification(
				session.getUser().getId(),
				"Charger Offline",
				"Cannot start charging - charger is offline. Amount refunded.",
				"ERROR");

		session.setStatus("FAILED");
		session.setEndTime(LocalDateTime.now());
		sessionRepository.save(session);

		throw new RuntimeException("Charger is offline. Session failed and amount refunded.");
	}

	// ... rest of your methods (getTotalSessions, etc.) remain the same ...

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

	public Session getSessionById(Long sessionId) {
		if (log.isDebugEnabled()) {
			log.debug("Fetching session by ID: sessionId={}", sessionId);
		}
		return sessionRepository.findById(sessionId).orElse(null);
	}

	public Optional<Session> findLastActiveSession() {
		if (log.isDebugEnabled()) {
			log.debug("Finding last active session");
		}
		return sessionRepository.findFirstByStatusOrderByStartTimeDesc("active");
	}

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