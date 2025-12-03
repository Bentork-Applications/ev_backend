package com.bentork.ev_system.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.config.JwtUtil;
import com.bentork.ev_system.dto.request.SessionDTO;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Plan;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.model.User;
import com.bentork.ev_system.repository.ChargerRepository;
import com.bentork.ev_system.repository.PlanRepository;
import com.bentork.ev_system.repository.UserRepository;
import com.bentork.ev_system.service.ReceiptService;
import com.bentork.ev_system.service.SessionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private ReceiptService receiptService;

	@Autowired
	private PlanRepository planRepository;

	@Autowired
	private ChargerRepository chargerRepository;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private UserRepository userRepository;

	/**
	 * Start session using prepaid plan OR kWh package/custom.
	 * Creates receipt → debits wallet → starts session.
	 * Returns receiptId, sessionId, and amount debited.
	 */
	@PostMapping("/start")
	public ResponseEntity<Map<String, Object>> startSession(
			@RequestBody SessionDTO request,
			@RequestHeader("Authorization") String authHeader) {

		log.info("POST /api/sessions/start - Starting session, chargerId={}, planId={}, selectedKwh={}",
				request.getChargerId(), request.getPlanId(), request.getSelectedKwh());

		try {
			String token = authHeader.substring(7);
			String email = jwtUtil.extractUsername(token);
			User user = userRepository.findByEmail(email)
					.orElseThrow(() -> new RuntimeException("User not found"));

			Charger charger = chargerRepository.findById(request.getChargerId())
					.orElseThrow(() -> new RuntimeException("Charger not found"));

			Plan plan = null;
			if (request.getPlanId() != null) {
				plan = planRepository.findById(request.getPlanId())
						.orElseThrow(() -> new RuntimeException("Plan not found"));
			}

			Receipt receipt = receiptService.createReceipt(user, plan, charger, request.getSelectedKwh());
			Receipt paidReceipt = receiptService.payReceipt(receipt.getId(), request.getBoxId());
			Session session = paidReceipt.getSession();

			log.info("POST /api/sessions/start - Success, sessionId={}, receiptId={}, userId={}, amountDebited={}",
					session.getId(), paidReceipt.getId(), user.getId(), paidReceipt.getAmount());

			return ResponseEntity.ok(Map.of(
					"receiptId", paidReceipt.getId(),
					"sessionId", session.getId(),
					"amountDebited", paidReceipt.getAmount(),
					"message", "Session started successfully."));
		} catch (RuntimeException e) {
			log.error("POST /api/sessions/start - Failed: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("POST /api/sessions/start - Failed: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to start session"));
		}
	}

	/**
	 * Stop session, finalize cost and return energy used, refund/extra flags.
	 */
	@PostMapping("/stop")
	public ResponseEntity<Map<String, Object>> stopSession(
			@RequestBody SessionDTO request,
			@RequestHeader("Authorization") String authHeader) {

		log.info("POST /api/sessions/stop - Stopping session, sessionId={}", request.getSessionId());

		try {
			String token = authHeader.substring(7);
			String email = jwtUtil.extractUsername(token);
			User user = userRepository.findByEmail(email)
					.orElseThrow(() -> new RuntimeException("User not found"));

			Map<String, Object> result = sessionService.stopSession(user.getId(), request);

			log.info("POST /api/sessions/stop - Success, sessionId={}, energyUsed={}, finalCost={}",
					request.getSessionId(), result.get("energyUsed"), result.get("finalCost"));

			return ResponseEntity.ok(result);
		} catch (RuntimeException e) {
			log.error("POST /api/sessions/stop - Failed, sessionId={}: {}",
					request.getSessionId(), e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			log.error("POST /api/sessions/stop - Failed: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to stop session"));
		}
	}

	// @PreAuthorize("hasAuthority('ADMIN')")
	@GetMapping("/total")
	public ResponseEntity<Long> getTotalSessions(@RequestHeader("Authorization") String authHeader) {
		log.info("GET /api/sessions/total - Request received");

		try {
			// ensureAdmin(authHeader);
			Long total = sessionService.getTotalSessions();
			log.info("GET /api/sessions/total - Success, total={}", total);
			return ResponseEntity.ok(total);
		} catch (Exception e) {
			log.error("GET /api/sessions/total - Failed: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	// @PreAuthorize("hasAuthority('ADMIN')")
	@GetMapping("/energy")
	public ResponseEntity<Double> getTotalEnergy(@RequestHeader("Authorization") String authHeader) {
		log.info("GET /api/sessions/energy - Request received");

		try {
			// ensureAdmin(authHeader);
			Double totalEnergy = sessionService.getTotalEnergyConsumed();
			log.info("GET /api/sessions/energy - Success, totalEnergy={} kWh", totalEnergy);
			return ResponseEntity.ok(totalEnergy);
		} catch (Exception e) {
			log.error("GET /api/sessions/energy - Failed: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	// Active Sessions
	@GetMapping("/active")
	public ResponseEntity<Long> getActiveSessions(@RequestHeader("Authorization") String authHeader) {
		log.info("GET /api/sessions/active - Request received");

		try {
			Long active = sessionService.getActiveSessions();
			log.info("GET /api/sessions/active - Success, active={}", active);
			return ResponseEntity.ok(active);
		} catch (Exception e) {
			log.error("GET /api/sessions/active - Failed: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	// Average Uptime
	@GetMapping("/uptime")
	public ResponseEntity<Double> getAverageUptime(@RequestHeader("Authorization") String authHeader) {
		log.info("GET /api/sessions/uptime - Request received");

		try {
			Double uptime = sessionService.getAverageUptime();
			log.info("GET /api/sessions/uptime - Success, uptime={}%", uptime);
			return ResponseEntity.ok(uptime);
		} catch (Exception e) {
			log.error("GET /api/sessions/uptime - Failed: {}", e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Get specific session energy consumed (kWh) by Session ID.
	 * Returns 0.0 if session just started or no energy recorded yet.
	 */
	@GetMapping("/{sessionId}/energy")
	public ResponseEntity<Double> getSessionEnergy(
			@PathVariable Long sessionId,
			@RequestHeader("Authorization") String authHeader) {

		log.info("GET /api/sessions/{}/energy - Request received", sessionId);

		try {
			Session session = sessionService.getSessionById(sessionId);
			if (session == null) {
				log.warn("GET /api/sessions/{}/energy - Session not found", sessionId);
				return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
			}

			// No null check needed because 'double' is primitive
			double energy = session.getEnergyKwh();

			log.info("GET /api/sessions/{}/energy - Success, energy={} kWh", sessionId, energy);
			return ResponseEntity.ok(energy);

		} catch (Exception e) {
			log.error("GET /api/sessions/{}/energy - Failed: {}", sessionId, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Get specific session status by Session ID.
	 */
	@GetMapping("/{sessionId}/status")
	public ResponseEntity<String> getSessionStatus(
			@PathVariable Long sessionId,
			@RequestHeader("Authorization") String authHeader) {

		log.info("GET /api/sessions/{}/status - Request received", sessionId);

		try {
			Session session = sessionService.getSessionById(sessionId);
			if (session == null) {
				log.warn("GET /api/sessions/{}/status - Session not found", sessionId);
				return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
			}

			String status = session.getStatus();

			log.info("GET /api/sessions/{}/status - Success, status={}", sessionId, status);
			return ResponseEntity.ok(status);

		} catch (Exception e) {
			log.error("GET /api/sessions/{}/status - Failed: {}", sessionId, e.getMessage(), e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error fetching status");
		}
	}
    
	//ERROR TODAY
    @GetMapping("/error/today")
    public ResponseEntity<Long> getTodaysError(@RequestHeader("Authorization") String authHeader) {
        try {
            log.info("Calling session service to get todays total errors");
            Long count = sessionService.getTodaysErrorCount();
            return ResponseEntity.ok(count);
        } catch (DataAccessException e) {
            log.error("Error while accessing data: {}", e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Global error: {}", e);
            return ResponseEntity.internalServerError().build();
        }
    }

	// list of session
    @GetMapping("/all/records")
    public ResponseEntity<List<Session>> getAllSessionRecords(@RequestHeader("Authorization") String authHeader) {
        try {
            log.info("Calling session service to get all session records");
            List<Session> allRecords = sessionService.getallSessionRecords();
            return ResponseEntity.ok(allRecords);
        } catch (DataAccessException e) {
            log.error("Error while accessing data: {}", e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Global error: {}", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}