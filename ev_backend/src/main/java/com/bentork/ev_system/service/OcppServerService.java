package com.bentork.ev_system.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OcppServerService {

    @Autowired
    private OcppWebSocketServer ocppWebSocketServer;

    @PostConstruct
    public void init() {
        try {
            ocppWebSocketServer.start();
            System.out.println("🚀 OCPP 1.6 WebSocket Server initialized and listening on port 8887");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
