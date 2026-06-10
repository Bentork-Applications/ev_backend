package com.bentork.ev_system.service.ocpp.handler;

import com.bentork.ev_system.enums.ChargerStatus;
import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.repository.ChargerRepository;
import com.bentork.ev_system.repository.ReceiptRepository;
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
public class StopTransactionHandler implements OcppActionHandler {

    private final ISessionService sessionService;
    private final IRFIDChargingService rfidChargingService;
    private final ChargerRepository chargerRepository;
    private final ReceiptRepository receiptRepository;
    private final OcppConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public String getAction() {
        return "StopTransaction";
    }

    @Override
    public ObjectNode handle(String ocppId, JsonNode payload) {
        Session session = null;
        int transactionId = -1;
        Long sessionId = null;

        try {
            transactionId = payload.has("transactionId") ? payload.get("transactionId").asInt() : -1;
            double meterStop = payload.has("meterStop") ? payload.get("meterStop").asDouble() : 0.0;
            String reason = payload.has("reason") ? payload.get("reason").asText() : "Local";

            log.info("StopTransaction - TransactionId: {}, MeterStop: {}, Reason: {}",
                    transactionId, meterStop, reason);

            if (transactionId == -1) {
                throw new RuntimeException("Missing transactionId");
            }

            sessionId = connectionManager.getSessionIdForTransaction(transactionId);
            if (sessionId == null) {
                log.warn("No mapping found for TxId: {}, assuming TxId == SessionId", transactionId);
                sessionId = (long) transactionId;
            }

            session = sessionService.getSessionById(sessionId);
            if (session == null) {
                log.error("Session not found for ID: {}", sessionId);
                throw new RuntimeException("Session not found");
            }

            // Calculate actual energy used
            Double startKwh = session.getStartMeterReading();
            if (startKwh == null) {
                Double meterStartWh = connectionManager.getMeterStart(sessionId);
                startKwh = meterStartWh / 1000.0;
            }

            double stopKwh = meterStop / 1000.0;
            double energyKwh = stopKwh - startKwh;

            log.info("Energy calculation: MeterStart (kWh)={}, MeterStop (kWh)={}, Energy={} kWh",
                    startKwh, stopKwh, energyKwh);

            // Prevent negative energy wipeout if meterStop is 0 or invalid
            if (energyKwh <= 0 && session.getEnergyKwh() > 0) {
                log.warn("Calculated energy is <= 0 ({}). Falling back to previously accumulated energy: {} kWh",
                        energyKwh, session.getEnergyKwh());
                energyKwh = session.getEnergyKwh();
            } else if (energyKwh < 0) {
                energyKwh = 0; // Don't allow negative energy if there's no previous accumulation
            }

            session.setEnergyKwh(energyKwh);

            // Check if selectedKwh session and limit reached
            Receipt receipt = receiptRepository.findBySession(session).orElse(null);
            if (receipt != null && receipt.getSelectedKwh() != null) {
                sessionService.checkAndStopIfReachedKwh(sessionId, energyKwh);
            }

            // Stop session
            if ("RFID".equals(session.getSourceType())) {
                rfidChargingService.stopCharging(sessionId);
            } else {
                sessionService.stopSessionBySystem(sessionId);
            }

            log.info("Session stopped successfully: {} (Energy: {} kWh, Source: {})",
                    session.getId(), energyKwh, session.getSourceType());

            ObjectNode idTagInfo = objectMapper.createObjectNode();
            idTagInfo.put("status", "Accepted");

            ObjectNode response = objectMapper.createObjectNode();
            response.set("idTagInfo", idTagInfo);
            return response;

        } catch (Exception e) {
            log.error("Error stopping transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to stop transaction: " + e.getMessage(), e);
        } finally {
            // Always clean up
            if (transactionId != -1) {
                connectionManager.removeTransaction(transactionId);
            }
            if (sessionId != null) {
                connectionManager.removeMeterStart(sessionId);
            }

            // Reset charger status
            if (session != null && session.getCharger() != null) {
                try {
                    Charger charger = session.getCharger();
                    charger.setOccupied(false);
                    charger.setAvailability(true);
                    charger.setStatus(ChargerStatus.AVAILABLE.getValue());
                    chargerRepository.save(charger);
                    log.info("Charger {} status reset to AVAILABLE", charger.getOcppId());
                } catch (Exception chargerEx) {
                    log.error("Failed to reset charger {} status: {}",
                            session.getCharger().getOcppId(), chargerEx.getMessage());
                }
            }
        }
    }
}
