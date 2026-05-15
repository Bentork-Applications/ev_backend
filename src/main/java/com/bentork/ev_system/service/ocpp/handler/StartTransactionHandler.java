package com.bentork.ev_system.service.ocpp.handler;

import com.bentork.ev_system.enums.ChargerStatus;
import com.bentork.ev_system.exception.domain.ChargerNotFoundException;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.repository.ChargerRepository;
import com.bentork.ev_system.repository.ReceiptRepository;
import com.bentork.ev_system.repository.SessionRepository;
import com.bentork.ev_system.service.interfaces.IRFIDChargingService;
import com.bentork.ev_system.service.interfaces.ISessionService;
import com.bentork.ev_system.service.ocpp.OcppActionHandler;
import com.bentork.ev_system.service.ocpp.OcppConnectionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartTransactionHandler implements OcppActionHandler {

    private final ISessionService sessionService;
    private final IRFIDChargingService rfidChargingService;
    private final ChargerRepository chargerRepository;
    private final ReceiptRepository receiptRepository;
    private final SessionRepository sessionRepository;
    private final OcppConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getAction() {
        return "StartTransaction";
    }

    @Override
    public ObjectNode handle(String ocppId, JsonNode payload) {
        try {
            String idTag = payload.has("idTag") ? payload.get("idTag").asText() : null;
            int connectorId = payload.has("connectorId") ? payload.get("connectorId").asInt() : 1;
            double meterStart = payload.has("meterStart") ? payload.get("meterStart").asDouble() : 0.0;

            log.info("StartTransaction - OCPP_ID: {}, IdTag: {}, ConnectorId: {}, MeterStart: {}",
                    ocppId, idTag, connectorId, meterStart);

            Charger charger = chargerRepository.findByOcppId(ocppId)
                    .orElseThrow(() -> new ChargerNotFoundException(ocppId));

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
            if (session == null) {
                try {
                    session = sessionService.activateOrRejectSession(ocppId);
                    if (session != null) {
                        Receipt linkedReceipt = receiptRepository.findBySession(session).orElse(null);
                        sessionType = linkedReceipt != null && linkedReceipt.getPlan() != null ? "PLAN" : "KWH_PACKAGE";
                        log.info("{} session activated under lock (sessionId: {})", sessionType, session.getId());
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
                        session = sessionService.startSessionFromReceipt(receipt, ocppId);
                        sessionType = receipt.getPlan() != null ? "PLAN" : "KWH_PACKAGE";
                        log.info("{} session started from receipt (sessionId: {})", sessionType, session.getId());
                    }
                } catch (Exception ex) {
                    log.debug("No prepaid receipt found: {}", ex.getMessage());
                }
            }

            // Strategy 4: No valid payment method
            if (session == null) {
                log.warn("No RFID or prepaid session found for charger {}", ocppId);
                throw new RuntimeException("No valid payment method found. Please use RFID card or prepay via app.");
            }

            // Store meter start value
            double startKwh = meterStart / 1000.0;
            session.setStartMeterReading(startKwh);
            session.setLastMeterReading(startKwh);
            sessionRepository.save(session);

            connectionManager.setMeterStart(session.getId(), meterStart);

            // Map transaction to session
            int transactionId = session.getId().intValue();
            connectionManager.mapTransaction(transactionId, session.getId());

            log.info("Transaction mapping: TxId {} -> SessionId {} (Type: {})",
                    transactionId, session.getId(), sessionType);

            // Update charger status
            charger.setOccupied(true);
            charger.setAvailability(false);
            charger.setStatus(ChargerStatus.BUSY.getValue());
            chargerRepository.save(charger);

            // Build response
            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Accepted");

            ObjectNode response = objectMapper.createObjectNode();
            response.put("transactionId", transactionId);
            response.set("idTagInfo", idTagInfo);
            return response;

        } catch (Exception e) {
            log.error("Error starting transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start transaction: " + e.getMessage(), e);
        }
    }
}
