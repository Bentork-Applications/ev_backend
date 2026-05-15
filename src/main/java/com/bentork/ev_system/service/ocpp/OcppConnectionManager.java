package com.bentork.ev_system.service.ocpp;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages OCPP WebSocket connections and transaction/session mappings.
 * Extracted from OcppWebSocketServer to centralize connection state.
 */
@Service
public class OcppConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(OcppConnectionManager.class);

    private final Map<WebSocket, String> connectionToOcppIdMap = new ConcurrentHashMap<>();
    private final Map<String, WebSocket> ocppIdToConnectionMap = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPongTimeMap = new ConcurrentHashMap<>();
    private final Map<Integer, Long> transactionToSessionMap = new ConcurrentHashMap<>();
    private final Map<Long, Double> sessionToMeterStartMap = new ConcurrentHashMap<>();

    public void registerConnection(WebSocket conn, String ocppId) {
        connectionToOcppIdMap.put(conn, ocppId);
        ocppIdToConnectionMap.put(ocppId, conn);
        log.info("Charger connected: {} (OCPP ID: {})", conn.getRemoteSocketAddress(), ocppId);
    }

    public String removeConnection(WebSocket conn) {
        String ocppId = connectionToOcppIdMap.remove(conn);
        if (ocppId != null) {
            ocppIdToConnectionMap.remove(ocppId);
            lastPongTimeMap.remove(ocppId);
        }
        return ocppId;
    }

    public String getOcppId(WebSocket conn) {
        return connectionToOcppIdMap.getOrDefault(conn, "UNKNOWN");
    }

    public WebSocket getConnection(String ocppId) {
        return ocppIdToConnectionMap.get(ocppId);
    }

    public Map<String, WebSocket> getConnectedChargers() {
        return Map.copyOf(ocppIdToConnectionMap);
    }

    // Ping-Pong tracking

    /**
     * Records the last time a pong was received from a charger.
     */
    public void updateLastPongTime(String ocppId) {
        lastPongTimeMap.put(ocppId, Instant.now());
    }

    /**
     * Returns the last pong timestamp for a charger, or null if never received.
     */
    public Instant getLastPongTime(String ocppId) {
        return lastPongTimeMap.get(ocppId);
    }

    /**
     * Returns a snapshot of all charger last-pong timestamps.
     */
    public Map<String, Instant> getAllLastPongTimes() {
        return Map.copyOf(lastPongTimeMap);
    }

    // Transaction-Session mapping
    public void mapTransaction(int transactionId, Long sessionId) {
        transactionToSessionMap.put(transactionId, sessionId);
    }

    public Long getSessionIdForTransaction(int transactionId) {
        return transactionToSessionMap.get(transactionId);
    }

    public void removeTransaction(int transactionId) {
        transactionToSessionMap.remove(transactionId);
    }

    // Meter start tracking
    public void setMeterStart(Long sessionId, double meterStart) {
        sessionToMeterStartMap.put(sessionId, meterStart);
    }

    public double getMeterStart(Long sessionId) {
        return sessionToMeterStartMap.getOrDefault(sessionId, 0.0);
    }

    public void removeMeterStart(Long sessionId) {
        sessionToMeterStartMap.remove(sessionId);
    }
}
