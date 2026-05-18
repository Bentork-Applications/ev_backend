package com.bentork.ev_system.service;

import com.bentork.ev_system.enums.ChargerStatus;
import com.bentork.ev_system.enums.SessionStatus;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.repository.ChargerRepository;
import com.bentork.ev_system.repository.SessionRepository;
import com.bentork.ev_system.service.interfaces.IRFIDChargingService;
import com.bentork.ev_system.service.interfaces.ISessionService;
import com.bentork.ev_system.service.ocpp.OcppConnectionManager;
import com.bentork.ev_system.service.ocpp.OcppMessageRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Slim OCPP 1.6 WebSocket Server.
 * Delegates action handling to OcppMessageRouter and individual OcppActionHandlers.
 * Manages connections via OcppConnectionManager.
 *
 * After Phase 5 decomposition: ~200 lines (down from ~966).
 */
@Service
public class OcppWebSocketServer extends WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(OcppWebSocketServer.class);
    private static final int OCPP_CALL = 2;
    private static final int OCPP_CALL_RESULT = 3;
    private static final int OCPP_CALL_ERROR = 4;

    private final OcppConnectionManager connectionManager;
    private final OcppMessageRouter messageRouter;
    private final ISessionService sessionService;
    private final IRFIDChargingService rfidChargingService;
    private final ChargerRepository chargerRepository;
    private final SessionRepository sessionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OcppWebSocketServer(
            @Value("${ocpp.server.port:8887}") int port,
            @Value("${ocpp.websocket.ping.interval:30}") int pingInterval,
            @Value("${ocpp.websocket.pong.timeout:90}") int pongTimeout,
            OcppConnectionManager connectionManager,
            OcppMessageRouter messageRouter,
            ISessionService sessionService,
            IRFIDChargingService rfidChargingService,
            ChargerRepository chargerRepository,
            SessionRepository sessionRepository) {
        super(new InetSocketAddress(port));
        this.connectionManager = connectionManager;
        this.messageRouter = messageRouter;
        this.sessionService = sessionService;
        this.rfidChargingService = rfidChargingService;
        this.chargerRepository = chargerRepository;
        this.sessionRepository = sessionRepository;

        // Configure ping-pong keep-alive for lost connection detection.
        // We use the pong.timeout as the connectionLostTimeout.
        setConnectionLostTimeout(pongTimeout);

        log.info("OCPP 1.6 WebSocket Server initialized on ws://0.0.0.0:{}", port);
        log.info("Ping-Pong keep-alive: pingInterval={}s, pongTimeout={}s",
                pingInterval, pongTimeout);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String ocppId = extractOcppIdFromHandshake(conn, handshake);
        connectionManager.registerConnection(conn, ocppId);
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
                String messageId = messageArray.get(1).asText();
                String action = messageArray.get(2).asText();
                JsonNode payload = messageArray.size() > 3 ? messageArray.get(3) : objectMapper.createObjectNode();
                String ocppId = connectionManager.getOcppId(conn);

                log.info("OCPP Call - Action: {}, MessageId: {}", action, messageId);

                try {
                    ObjectNode result = messageRouter.route(ocppId, action, payload);
                    if (result != null) {
                        sendCallResult(conn, messageId, result);
                    } else {
                        sendErrorResponse(conn, messageId, "NotSupported",
                                "Action '" + action + "' is not implemented");
                    }
                } catch (Exception e) {
                    log.error("Error handling OCPP call {}: {}", action, e.getMessage(), e);
                    sendErrorResponse(conn, messageId, "InternalError", e.getMessage());
                }
            } else {
                log.debug("Received message type: {}", messageType);
            }

        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            sendErrorResponse(conn, "unknown", "InternalError", "Failed to process message");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String ocppId = connectionManager.removeConnection(conn);
        if (ocppId != null) {
            String disconnectType = code == 1006 ? "PING-PONG TIMEOUT" : "NORMAL";
            log.warn("Charger {} disconnected [{}]. Code: {}, Remote: {}, Reason: {}. Checking for active sessions...",
                    ocppId, disconnectType, code, remote, reason);
            try {
                Charger charger = chargerRepository.findByOcppId(ocppId).orElse(null);
                if (charger != null) {
                    charger.setAvailability(false);
                    charger.setOccupied(false);
                    charger.setStatus(ChargerStatus.OFFLINE.getValue());
                    chargerRepository.save(charger);
                    log.info("Charger {} status set to OFFLINE", ocppId);

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
        log.info("Charger disconnected: {} (OCPP ID: {}, Code: {}, Remote: {}, Reason: {})",
                conn.getRemoteSocketAddress(), ocppId, code, remote, reason);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String ocppId = connectionManager.getOcppId(conn);
        log.error("WebSocket error for charger {}: {}", ocppId, ex.getMessage(), ex);
    }

    @Override
    public void onStart() {
        log.info("OCPP WebSocket server ready and listening on port {}", getPort());
    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
        String ocppId = connectionManager.getOcppId(conn);
        connectionManager.updateLastPongTime(ocppId);
        log.debug("Pong received from charger {} ({})", ocppId, conn.getRemoteSocketAddress());
    }

    // ===================== PUBLIC API =====================

    public boolean sendRemoteCommand(String ocppId, String action, ObjectNode payload) {
        WebSocket conn = connectionManager.getConnection(ocppId);
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

    public Map<String, WebSocket> getConnectedChargers() {
        return connectionManager.getConnectedChargers();
    }

    // ===================== PRIVATE HELPERS =====================

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

    private String extractOcppIdFromHandshake(WebSocket conn, ClientHandshake handshake) {
        log.info("onOpen: New connection detected. Trying to extract OCPP ID...");
        try {
            String resourceDescriptor = handshake.getResourceDescriptor();
            log.debug("onOpen: Handshake ResourceDescriptor: {}", resourceDescriptor);

            if (resourceDescriptor != null && resourceDescriptor.length() > 1) {
                String path = resourceDescriptor.substring(1).split("\\?")[0];
                if (!path.isEmpty() && !path.equals("/")) {
                    log.info("onOpen: Successfully extracted OCPP ID from path: {}", path);
                    return path;
                }
            }

            log.warn("onOpen: Could not extract ID from path. Creating safe fallback ID.");
            String remoteAddressStr = "UNKNOWN_ADDRESS";
            if (conn != null && conn.getRemoteSocketAddress() != null) {
                remoteAddressStr = conn.getRemoteSocketAddress().toString();
            }
            String fallbackId = "CHARGER-" + remoteAddressStr.replaceAll("[^a-zA-Z0-9-]", "");
            log.warn("onOpen: Using fallback ID: {}", fallbackId);
            return fallbackId;
        } catch (Exception e) {
            log.error("onOpen: CRITICAL ERROR during ID extraction", e);
            return "ID_EXTRACTION_FAILED";
        }
    }
}