package com.bentork.ev_system.service;

import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.service.interfaces.IChargerCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Encapsulates OCPP remote command logic (RemoteStartTransaction, RemoteStopTransaction).
 * Depends on OcppWebSocketServer for transport but is itself a thin adapter,
 * breaking the circular dependency that previously existed between
 * SessionService ↔ OcppWebSocketServer.
 */
@Slf4j
@Service
public class ChargerCommandService implements IChargerCommandService {

    private final OcppWebSocketServer ocppWebSocketServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChargerCommandService(@Lazy OcppWebSocketServer ocppWebSocketServer) {
        this.ocppWebSocketServer = ocppWebSocketServer;
    }

    @Override
    public boolean sendRemoteStart(Session session) {
        String ocppId = session.getCharger().getOcppId();
        if (ocppId == null || ocppId.isEmpty()) {
            log.error("Cannot send RemoteStartTransaction: charger OCPP ID is null/empty");
            return false;
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("idTag", "SESSION_" + session.getId());
            payload.put("connectorId", 1);

            log.info("Sending RemoteStartTransaction to {}: idTag=SESSION_{}, connectorId=1",
                    ocppId, session.getId());

            boolean sent = ocppWebSocketServer.sendRemoteCommand(ocppId, "RemoteStartTransaction", payload);

            if (sent) {
                log.info("✅ RemoteStartTransaction sent successfully to charger: {}", ocppId);
            } else {
                log.error("❌ Failed to send RemoteStartTransaction: Charger {} not connected", ocppId);
            }
            return sent;
        } catch (Exception e) {
            log.error("❌ Error sending RemoteStartTransaction to {}: {}", ocppId, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendRemoteStop(Session session) {
        String ocppId = session.getCharger().getOcppId();

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("transactionId", session.getId().intValue());

            boolean sent = ocppWebSocketServer.sendRemoteCommand(ocppId, "RemoteStopTransaction", payload);

            if (sent) {
                log.info("✅ RemoteStopTransaction sent to charger: {}, txId: {}", ocppId, session.getId());
            } else {
                log.warn("⚠️ Failed to send RemoteStopTransaction to charger: {}", ocppId);
            }
            return sent;
        } catch (Exception e) {
            log.error("Error sending RemoteStopTransaction to {}: {}", ocppId, e.getMessage());
            return false;
        }
    }
}
