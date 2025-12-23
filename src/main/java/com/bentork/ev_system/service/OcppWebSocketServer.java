package com.bentork.ev_system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.enums.SessionStatus;
import com.bentork.ev_system.enums.ChargerStatus;
import com.bentork.ev_system.repository.ChargerRepository;
import com.bentork.ev_system.repository.ReceiptRepository;
import com.bentork.ev_system.repository.SessionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode; // Import RoundingMode
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OcppWebSocketServer extends WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(OcppWebSocketServer.class);
    private static final int OCPP_CALL = 2;
    private static final int OCPP_CALL_RESULT = 3;
    private static final int OCPP_CALL_ERROR = 4;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RFIDChargingService rfidChargingService;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Value("${ocpp.server.port:8887}")
    private int serverPort;

    // json response in every 30s
    @Value("${ocpp.heartbeat.interval:30}")
    private int heartbeatInterval;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Thread-safe maps
    private final Map<Integer, Long> transactionToSessionMap = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> connectionToOcppIdMap = new ConcurrentHashMap<>();
    private final Map<String, WebSocket> ocppIdToConnectionMap = new ConcurrentHashMap<>();
    private final Map<Long, Double> sessionToMeterStartMap = new ConcurrentHashMap<>();

    public OcppWebSocketServer(@Value("${ocpp.server.port:8887}") int port) {
        super(new InetSocketAddress(port));
        log.info("OCPP 1.6 WebSocket Server initialized on ws://0.0.0.0:{}", port);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String ocppId = extractOcppIdFromHandshake(conn, handshake);
        connectionToOcppIdMap.put(conn, ocppId);
        ocppIdToConnectionMap.put(ocppId, conn);
        log.info("Charger connected: {} (OCPP ID: {})", conn.getRemoteSocketAddress(), ocppId);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        log.debug("Message from charger: {}", message);

        try {
            JsonNode messageArray = objectMapper.readTree(message);

            if (!messageArray.isArray() || messageArray.size() < 3) {
                log.warn("Invalid OCPP message format: {}", message);
                return;
            }

            int messageType = messageArray.get(0).asInt();

            if (messageType == OCPP_CALL) {
                handleCall(conn, messageArray);
            } else {
                log.debug("Received message type: {}", messageType);
            }

        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            sendErrorResponse(conn, "unknown", "InternalError", "Failed to process message");
        }
    }

    /**
     * Handle OCPP Call messages (type 2)
     */
    private void handleCall(WebSocket conn, JsonNode messageArray) {
        try {
            String messageId = messageArray.get(1).asText();
            String action = messageArray.get(2).asText();
            JsonNode payload = messageArray.size() > 3 ? messageArray.get(3) : objectMapper.createObjectNode();

            log.info("OCPP Call - Action: {}, MessageId: {}", action, messageId);

            switch (action) {
                case "BootNotification":
                    handleBootNotification(conn, messageId, payload);
                    break;
                case "Heartbeat":
                    handleHeartbeat(conn, messageId);
                    break;
                case "Authorize":
                    handleAuthorize(conn, messageId, payload);
                    break;
                case "StartTransaction":
                    handleStartTransaction(conn, messageId, payload);
                    break;
                case "StopTransaction":
                    handleStopTransaction(conn, messageId, payload);
                    break;
                case "StatusNotification":
                    handleStatusNotification(conn, messageId, payload);
                    break;
                case "MeterValues":
                    handleMeterValues(conn, messageId, payload);
                    break;
                default:
                    sendErrorResponse(conn, messageId, "NotSupported",
                            "Action '" + action + "' is not implemented");
                    log.warn("Unsupported action: {}", action);
            }

        } catch (Exception e) {
            log.error("Error handling OCPP call: {}", e.getMessage(), e);
            sendErrorResponse(conn, "unknown", "InternalError", e.getMessage());
        }
    }

    /**
     * Handle BootNotification
     */
    private void handleBootNotification(WebSocket conn, String messageId, JsonNode payload) {
        String ocppId = connectionToOcppIdMap.get(conn);
        log.info("BootNotification received from {}: {}", ocppId, payload);

        // Set charger status to AVAILABLE when it boots
        try {
            Charger charger = chargerRepository.findByOcppId(ocppId).orElse(null);
            if (charger != null) {
                charger.setStatus(ChargerStatus.AVAILABLE.getValue());
                charger.setAvailability(true);
                chargerRepository.save(charger);
                log.info("Charger {} status set to AVAILABLE", ocppId);
            }
        } catch (Exception e) {
            log.error("Error updating charger status on boot: {}", e.getMessage());
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "Accepted");
        response.put("currentTime", OffsetDateTime.now().toString());
        response.put("interval", heartbeatInterval);

        sendCallResult(conn, messageId, response);
    }

    /**
     * Handle Heartbeat
     */
    private void handleHeartbeat(WebSocket conn, String messageId) {
        log.debug("Heartbeat received from {}", connectionToOcppIdMap.get(conn));

        ObjectNode response = objectMapper.createObjectNode();
        response.put("currentTime", OffsetDateTime.now().toString());

        sendCallResult(conn, messageId, response);
    }

    /**
     * Handle Authorize - Validate RFID card
     */
    private void handleAuthorize(WebSocket conn, String messageId, JsonNode payload) {
        String idTag = payload.has("idTag") ? payload.get("idTag").asText() : null;
        log.info("Authorize request for idTag: {}", idTag);

        // Validate RFID card exists and is active
        boolean isValid = false;
        try {
            isValid = rfidChargingService.validateRFIDCard(idTag);
        } catch (Exception e) {
            log.warn("RFID validation failed: {}", e.getMessage());
        }

        ObjectNode idTagInfo = objectMapper.createObjectNode();
        idTagInfo.put("status", isValid ? "Accepted" : "Invalid");

        ObjectNode response = objectMapper.createObjectNode();
        response.set("idTagInfo", idTagInfo);

        sendCallResult(conn, messageId, response);
    }

    /**
     * Handle StartTransaction
     * Strategy:
     * 1. RFID Card Flow - If idTag provided, start RFID charging
     * 2. Prepaid Flow - If user already created session via app (PAID receipt
     * exists)
     * 3. Guest Flow - Create new session (fallback)
     */
    private void handleStartTransaction(WebSocket conn, String messageId, JsonNode payload) {
        try {
            String idTag = payload.has("idTag") ? payload.get("idTag").asText() : null;
            int connectorId = payload.has("connectorId") ? payload.get("connectorId").asInt() : 1;
            String ocppId = connectionToOcppIdMap.getOrDefault(conn, "UNKNOWN");
            double meterStart = payload.has("meterStart") ? payload.get("meterStart").asDouble() : 0.0;

            log.info("StartTransaction - OCPP_ID: {}, IdTag: {}, ConnectorId: {}, MeterStart: {}",
                    ocppId, idTag, connectorId, meterStart);

            // Find charger by OCPP ID
            Charger charger = chargerRepository.findByOcppId(ocppId)
                    .orElseThrow(() -> new RuntimeException("Charger not found for OCPP ID: " + ocppId));

            Session session = null;
            String sessionType = "UNKNOWN";

            // Strategy 1: RFID Card Flow
            if (idTag != null && !idTag.isEmpty()) {
                try {
                    session = rfidChargingService.startCharging(idTag, charger.getId(), ocppId);
                    sessionType = "RFID";
                    log.info("RFID session started (sessionId: {})", session.getId());
                } catch (Exception ex) {
                    log.warn("RFID flow failed: {}", ex.getMessage());
                }
            }

            // Strategy 2: Prepaid Flow (Plan/kWh Package)
            // Check for session that's already been paid (status=INITIATED or active)
            if (session == null) {
                try {
                    // Option A: Look for INITIATED session (created by /api/sessions/start but
                    // waiting for OCPP)
                    session = sessionRepository
                            .findFirstByChargerAndStatusInOrderByCreatedAtDesc(
                                    charger,
                                    java.util.Arrays.asList(SessionStatus.INITIATED.getValue(),
                                            SessionStatus.ACTIVE.getValue()))
                            .orElse(null);

                    if (session != null) {
                        // Activate the session if it's INITIATED
                        if (SessionStatus.INITIATED.matches(session.getStatus())) {
                            session.setStatus(SessionStatus.ACTIVE.getValue());
                            session.setStartTime(java.time.LocalDateTime.now());
                            sessionRepository.save(session);
                        }

                        Receipt linkedReceipt = receiptRepository.findBySession(session).orElse(null);
                        sessionType = linkedReceipt != null && linkedReceipt.getPlan() != null
                                ? "PLAN"
                                : "KWH_PACKAGE";
                        log.info("{} session activated (sessionId: {})", sessionType, session.getId());
                    }
                } catch (Exception ex) {
                    log.debug("No active/initiated session found: {}", ex.getMessage());
                }
            }

            // Strategy 3: Look for PAID receipt without session (fallback)
            if (session == null) {
                try {
                    Receipt receipt = receiptRepository
                            .findFirstByChargerAndStatusOrderByCreatedAtDesc(charger, "PAID")
                            .orElse(null);

                    if (receipt != null && receipt.getSession() == null) {
                        // Start session from this paid receipt
                        session = sessionService.startSessionFromReceipt(receipt, ocppId);
                        sessionType = receipt.getPlan() != null ? "PLAN" : "KWH_PACKAGE";
                        log.info("{} session started from receipt (sessionId: {})",
                                sessionType, session.getId());
                    }
                } catch (Exception ex) {
                    log.debug("No prepaid receipt found: {}", ex.getMessage());
                }
            }

            // Strategy 4: Guest/Walk-in Flow (disabled - require payment)
            if (session == null) {
                log.warn("No RFID or prepaid session found for charger {}", ocppId);
                throw new RuntimeException("No valid payment method found. Please use RFID card or prepay via app.");
            }

            // Store meter start value in DB (Persistence)
            double startKwh = meterStart / 1000.0;
            session.setStartMeterReading(startKwh);
            session.setLastMeterReading(startKwh);
            sessionRepository.save(session);

            // Keep map for fallback/legacy but DB is primary
            sessionToMeterStartMap.put(session.getId(), meterStart);

            // Map transaction to session
            int transactionId = session.getId().intValue();
            transactionToSessionMap.put(transactionId, session.getId());

            log.info("Transaction mapping: TxId {} -> SessionId {} (Type: {})",
                    transactionId, session.getId(), sessionType);

            // Update charger status
            charger.setOccupied(true);
            charger.setAvailability(false);
            charger.setStatus(ChargerStatus.BUSY.getValue());
            chargerRepository.save(charger);

            // Send success response
            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Accepted");

            ObjectNode response = objectMapper.createObjectNode();
            response.put("transactionId", transactionId);
            response.set("idTagInfo", idTagInfo);

            sendCallResult(conn, messageId, response);

        } catch (Exception e) {
            log.error("Error starting transaction: {}", e.getMessage(), e);
            sendErrorResponse(conn, messageId, "InternalError",
                    "Failed to start transaction: " + e.getMessage());
        }
    }

    /**
     * Handle StopTransaction
     * This integrates with your existing wallet refund/extra debit logic in
     * SessionService
     */
    private void handleStopTransaction(WebSocket conn, String messageId, JsonNode payload) {
        try {
            int transactionId = payload.has("transactionId") ? payload.get("transactionId").asInt() : -1;
            double meterStop = payload.has("meterStop") ? payload.get("meterStop").asDouble() : 0.0;
            String reason = payload.has("reason") ? payload.get("reason").asText() : "Local";

            log.info("StopTransaction - TransactionId: {}, MeterStop: {}, Reason: {}",
                    transactionId, meterStop, reason);

            if (transactionId == -1) {
                sendErrorResponse(conn, messageId, "ProtocolError", "Missing transactionId");
                return;
            }

            // Look up session from transaction map
            Long sessionId = transactionToSessionMap.get(transactionId);

            if (sessionId == null) {
                log.warn("No mapping found for TxId: {}, assuming TxId == SessionId", transactionId);
                sessionId = (long) transactionId;
            }

            // Get session
            Session session = sessionService.getSessionById(sessionId);

            if (session == null) {
                log.error("Session not found for ID: {}", sessionId);
                sendErrorResponse(conn, messageId, "InternalError", "Session not found");
                return;
            }

            // Calculate actual energy used (Wh to kWh)
            // Use DB stored start meter reading for robustness
            Double startKwh = session.getStartMeterReading();
            if (startKwh == null) {
                // Fallback to in-memory if DB is null (legacy sessions)
                Double meterStartWh = sessionToMeterStartMap.getOrDefault(sessionId, 0.0);
                startKwh = meterStartWh / 1000.0;
            }

            double stopKwh = meterStop / 1000.0;
            double energyKwh = stopKwh - startKwh;

            log.info("Energy calculation: MeterStart (kWh)={}, MeterStop (kWh)={}, Energy={} kWh",
                    startKwh, stopKwh, energyKwh);

            // Update session energy before stopping
            session.setEnergyKwh(energyKwh);

            // Check if this is selectedKwh session and limit reached
            Receipt receipt = receiptRepository.findBySession(session).orElse(null);
            if (receipt != null && receipt.getSelectedKwh() != null) {
                sessionService.checkAndStopIfReachedKwh(sessionId, energyKwh);
            }

            // Stop session - This handles all wallet logic (refund/extra debit)
            if ("RFID".equals(session.getSourceType())) {
                // For RFID sessions, use RFID service
                rfidChargingService.stopCharging(sessionId);
            } else {
                // For Plan/kWh sessions, use SessionService which handles refund/extra debit
                sessionService.stopSessionBySystem(sessionId);
            }

            // Clean up
            transactionToSessionMap.remove(transactionId);
            sessionToMeterStartMap.remove(sessionId);

            // Update charger availability
            Charger charger = session.getCharger();
            charger.setOccupied(false);
            charger.setAvailability(true);
            charger.setStatus(ChargerStatus.AVAILABLE.getValue());
            chargerRepository.save(charger);

            log.info("Session stopped successfully: {} (Energy: {} kWh, Source: {})",
                    session.getId(), energyKwh, session.getSourceType());

            // Send success response
            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Accepted");

            ObjectNode response = objectMapper.createObjectNode();
            response.set("idTagInfo", idTagInfo);

            sendCallResult(conn, messageId, response);

        } catch (Exception e) {
            log.error("Error stopping transaction: {}", e.getMessage(), e);
            sendErrorResponse(conn, messageId, "InternalError",
                    "Failed to stop transaction: " + e.getMessage());
        }
    }

    /**
     * Handle StatusNotification - Update charger availability
     */
    private void handleStatusNotification(WebSocket conn, String messageId, JsonNode payload) {
        String ocppId = connectionToOcppIdMap.get(conn);
        int connectorId = payload.has("connectorId") ? payload.get("connectorId").asInt() : 0;
        String status = payload.has("status") ? payload.get("status").asText() : "Unknown";

        log.info("StatusNotification - OCPP_ID: {}, Connector: {}, Status: {}",
                ocppId, connectorId, status);

        // Update charger availability in database
        try {
            Charger charger = chargerRepository.findByOcppId(ocppId).orElse(null);
            if (charger != null) {
                boolean isAvailable = "Available".equalsIgnoreCase(status);
                boolean isOccupied = "Occupied".equalsIgnoreCase(status) ||
                        "Charging".equalsIgnoreCase(status);

                charger.setAvailability(isAvailable);
                charger.setOccupied(isOccupied);
                chargerRepository.save(charger);

                log.debug("Updated charger {}: available={}, occupied={}",
                        charger.getId(), isAvailable, isOccupied);
            }
        } catch (Exception e) {
            log.error("Error updating charger status: {}", e.getMessage());
        }

        sendCallResult(conn, messageId, objectMapper.createObjectNode());
    }

    // REPLACE YOUR EXISTING handleMeterValues METHOD WITH THIS ONE

    /**
     * Handle MeterValues - Update energy consumption during charging
     * For RFID sessions: incremental wallet deduction
     * For Plan/kWh sessions: check if limit reached
     */
    private void handleMeterValues(WebSocket conn, String messageId, JsonNode payload) {
        try {
            String ocppId = connectionToOcppIdMap.get(conn);
            int transactionId = payload.has("transactionId") ? payload.get("transactionId").asInt() : -1;

            if (transactionId == -1) {
                log.debug("MeterValues without transactionId (heartbeat meter)");
                sendCallResult(conn, messageId, objectMapper.createObjectNode());
                return;
            }

            // Extract current energy value (This is the Absolute Meter Reading e.g.,
            // 10500.5 kWh)
            BigDecimal currentAbsKwh = extractEnergyFromMeterValues(payload);

            if (currentAbsKwh == null) {
                log.debug("MeterValues - no energy measurand found");
                sendCallResult(conn, messageId, objectMapper.createObjectNode());
                return;
            }

            // Look up session
            Long sessionId = transactionToSessionMap.get(transactionId);
            if (sessionId == null) {
                sessionId = (long) transactionId;
            }

            Session session = sessionService.getSessionById(sessionId);
            if (session == null) {
                log.warn("Session {} not found for meter update", sessionId);
                sendCallResult(conn, messageId, objectMapper.createObjectNode());
                return;
            }

            log.debug("MeterValues - SessionId: {}, CurrentAbsKwh: {}, Source: {}",
                    sessionId, currentAbsKwh, session.getSourceType());

            // Handle based on session type
            if ("RFID".equals(session.getSourceType())) {
                // RFID Flow: Incremental wallet deduction (RFID service handles deltas
                // internally)
                Session updated = rfidChargingService.updateEnergy(sessionId, currentAbsKwh);

                // If session was auto-stopped due to low balance, stop transaction
                if (SessionStatus.COMPLETED.matches(updated.getStatus())) {
                    log.warn("RFID session {} auto-stopped due to low balance", sessionId);
                    transactionToSessionMap.remove(transactionId);
                    sendRemoteStopTransaction(conn, transactionId);
                }
            } else {
                // ✅ FIX STARTS HERE: Plan/kWh Package Flow

                // 1. Get the meter reading from when the session started
                Double startKwh = session.getStartMeterReading();
                if (startKwh == null) {
                    // Fallback using map
                    Double startMeterWh = sessionToMeterStartMap.getOrDefault(sessionId, 0.0);
                    startKwh = startMeterWh / 1000.0;
                }

                // 2. Calculate actual consumption for THIS session
                // currentAbsKwh is already in kWh from helper method
                double rawConsumed = currentAbsKwh.doubleValue() - startKwh;
                // Round to 3 decimal places
                double consumedKwh = Math.round(rawConsumed * 1000.0) / 1000.0;

                // Safety: handle cases where meter might reset or glitch
                if (consumedKwh < 0) {
                    log.warn("Negative consumption detected (Meter reset?): Current={}, Start={}. Treating as 0.",
                            currentAbsKwh, startKwh);
                    consumedKwh = 0;
                }

                log.info("kWh Check: SessionId={}, AbsoluteMeter={}, StartMeter={}, Consumed={}",
                        sessionId, currentAbsKwh, startKwh, consumedKwh);

                // Update last known meter reading AND current energy usage to DB
                session.setLastMeterReading(currentAbsKwh.doubleValue());
                session.setEnergyKwh(consumedKwh); // ✅ SAVING ENERGY TO DB
                sessionRepository.save(session);

                // 3. Pass the CONSUMED value
                sessionService.checkAndStopIfReachedKwh(sessionId, consumedKwh);
            }

            sendCallResult(conn, messageId, objectMapper.createObjectNode());

        } catch (Exception e) {
            log.error("Error handling MeterValues: {}", e.getMessage(), e);
            sendCallResult(conn, messageId, objectMapper.createObjectNode());
        }
    }

    /**
     * Extract energy value from OCPP MeterValues payload
     * * --- THIS IS THE UPDATED, MORE ROBUST METHOD ---
     */
    private BigDecimal extractEnergyFromMeterValues(JsonNode payload) {
        try {
            if (!payload.has("meterValue"))
                return null;

            JsonNode meterValues = payload.get("meterValue");
            if (!meterValues.isArray())
                return null;

            for (JsonNode meterValue : meterValues) {
                if (!meterValue.has("sampledValue"))
                    continue;

                JsonNode sampledValues = meterValue.get("sampledValue");
                if (!sampledValues.isArray())
                    continue;

                for (JsonNode sample : sampledValues) {
                    // SAFER: Only proceed if measurand is present
                    if (!sample.has("measurand")) {
                        continue;
                    }

                    String measurand = sample.get("measurand").asText();

                    if ("Energy.Active.Import.Register".equals(measurand)) {
                        String valueStr = sample.get("value").asText();
                        BigDecimal value = new BigDecimal(valueStr);

                        // ROBUSTNESS: Check the unit. Default to Wh if not specified.
                        String unit = sample.has("unit") ? sample.get("unit").asText() : "Wh";

                        if ("kWh".equalsIgnoreCase(unit)) {
                            // Value is already in kWh
                            log.debug("Meter value is already in kWh: {}", value);
                            return value;
                        } else {
                            // Assume Wh, convert to kWh
                            BigDecimal valueKwh = value.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
                            log.debug("Converted Wh to kWh: {} Wh -> {} kWh", value, valueKwh);
                            return valueKwh;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing meter values: {}", e.getMessage());
        }
        return null; // Return null if no valid energy value was found
    }

    /**
     * Send remote stop command to charger (when wallet balance insufficient)
     */
    private void sendRemoteStopTransaction(WebSocket conn, int transactionId) {
        try {
            String messageId = java.util.UUID.randomUUID().toString();

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("transactionId", transactionId);

            ArrayNode message = objectMapper.createArrayNode();
            message.add(OCPP_CALL);
            message.add(messageId);
            message.add("RemoteStopTransaction");
            message.add(payload);

            String messageStr = objectMapper.writeValueAsString(message);
            conn.send(messageStr);
            log.info("Sent RemoteStopTransaction for TxId: {}", transactionId);
        } catch (Exception e) {
            log.error("Error sending RemoteStopTransaction: {}", e.getMessage());
        }
    }

    /**
     * Send OCPP CallResult (type 3)
     */
    private void sendCallResult(WebSocket conn, String messageId, ObjectNode payload) {
        try {
            ArrayNode response = objectMapper.createArrayNode();
            response.add(OCPP_CALL_RESULT);
            response.add(messageId);
            response.add(payload);

            String responseStr = objectMapper.writeValueAsString(response);
            conn.send(responseStr);
            log.debug("Sent CallResult: {}", responseStr);
        } catch (Exception e) {
            log.error("Error sending CallResult: {}", e.getMessage(), e);
        }
    }

    /**
     * Send OCPP CallError (type 4)
     */
    private void sendErrorResponse(WebSocket conn, String messageId, String errorCode, String errorDescription) {
        try {
            ArrayNode response = objectMapper.createArrayNode();
            response.add(OCPP_CALL_ERROR);
            response.add(messageId);
            response.add(errorCode);
            response.add(errorDescription);
            response.add(objectMapper.createObjectNode());

            String responseStr = objectMapper.writeValueAsString(response);
            conn.send(responseStr);
            log.debug("Sent CallError: {}", responseStr);
        } catch (Exception e) {
            log.error("Error sending CallError: {}", e.getMessage(), e);
        }
    }

    /**
     * Extract OCPP ID from WebSocket handshake
     * Expected format: ws://server:8887/CP001
     * * -- NEW, SAFER VERSION --
     */
    private String extractOcppIdFromHandshake(WebSocket conn, ClientHandshake handshake) {
        // Add a log at the VERY start to prove this method is called
        log.info("onOpen: New connection detected. Trying to extract OCPP ID...");

        try {
            String resourceDescriptor = handshake.getResourceDescriptor();
            log.debug("onOpen: Handshake ResourceDescriptor: {}", resourceDescriptor);

            // --- 1. Try to get ID from the URL path ---
            if (resourceDescriptor != null && resourceDescriptor.length() > 1) {
                // Path is something like "/OCPPCHG-12345"
                String path = resourceDescriptor.substring(1).split("\\?")[0];
                if (!path.isEmpty() && !path.equals("/")) {
                    log.info("onOpen: Successfully extracted OCPP ID from path: {}", path);
                    return path; // Success!
                }
            }

            // --- 2. If path fails, create a safe fallback ID ---
            log.warn("onOpen: Could not extract ID from path. Creating safe fallback ID.");
            String remoteAddressStr = "UNKNOWN_ADDRESS";

            // SAFETY CHECK: Make sure conn and remote address are not null
            if (conn != null && conn.getRemoteSocketAddress() != null) {
                remoteAddressStr = conn.getRemoteSocketAddress().toString();
            }

            String fallbackId = "CHARGER-" + remoteAddressStr.replaceAll("[^a-zA-Z0-9-]", "");
            log.warn("onOpen: Using fallback ID: {}", fallbackId);
            return fallbackId;

        } catch (Exception e) {
            // --- 3. Catch ALL exceptions ---
            // This stops the crash from killing the onOpen method
            log.error("onOpen: CRITICAL ERROR during ID extraction", e);

            // Return a default ID so the server doesn't crash
            return "ID_EXTRACTION_FAILED";
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String ocppId = connectionToOcppIdMap.remove(conn);
        if (ocppId != null) {
            ocppIdToConnectionMap.remove(ocppId);

            log.warn("Charger {} disconnected. Checking for active sessions to stop...", ocppId);
            try {
                Charger charger = chargerRepository.findByOcppId(ocppId).orElse(null);
                if (charger != null) {
                    charger.setAvailability(false);
                    charger.setOccupied(false);
                    charger.setStatus(ChargerStatus.OFFLINE.getValue());
                    chargerRepository.save(charger);
                    log.info("Charger {} status set to OFFLINE", ocppId);

                    // Find active or initiated session
                    Session session = sessionRepository.findFirstByChargerAndStatusInOrderByCreatedAtDesc(
                            charger,
                            java.util.Arrays.asList(SessionStatus.ACTIVE.getValue(),
                                    SessionStatus.INITIATED.getValue()))
                            .orElse(null);

                    if (session != null) {
                        log.info("Stopping active session {} due to charger disconnection", session.getId());
                        if ("RFID".equalsIgnoreCase(session.getSourceType())) {
                            rfidChargingService.stopCharging(session.getId());
                        } else {
                            sessionService.stopSessionBySystem(session.getId());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error stopping session on close: {}", e.getMessage());
            }
        }
        log.info("Charger disconnected: {} (OCPP ID: {}, Code: {}, Reason: {})",
                conn.getRemoteSocketAddress(), ocppId, code, reason);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String ocppId = connectionToOcppIdMap.getOrDefault(conn, "UNKNOWN");
        log.error("WebSocket error for charger {}: {}", ocppId, ex.getMessage(), ex);
    }

    @Override
    public void onStart() {
        log.info("OCPP WebSocket server ready and listening on port {}", serverPort);
    }

    /**
     * Send remote command to a specific charger (for admin operations)
     */
    public boolean sendRemoteCommand(String ocppId, String action, ObjectNode payload) {
        WebSocket conn = ocppIdToConnectionMap.get(ocppId);
        if (conn == null || !conn.isOpen()) {
            log.warn("Charger {} not connected", ocppId);
            return false;
        }

        try {
            String messageId = java.util.UUID.randomUUID().toString();
            ArrayNode message = objectMapper.createArrayNode();
            message.add(OCPP_CALL);
            message.add(messageId);
            message.add(action);
            message.add(payload);

            String messageStr = objectMapper.writeValueAsString(message);
            conn.send(messageStr);
            log.info("Sent remote command to {}: {} ({})", ocppId, action, messageId);
            return true;
        } catch (Exception e) {
            log.error("Error sending remote command to {}: {}", ocppId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all connected chargers
     */
    public Map<String, WebSocket> getConnectedChargers() {
        return Map.copyOf(ocppIdToConnectionMap);
    }
}