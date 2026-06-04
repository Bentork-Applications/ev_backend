package com.bentork.ev_system.dto.request;

import lombok.Data;

@Data
public class TruecallerWebhookPayload {
    private String requestId;
    private String accessToken;
    private String endpoint; // Some truecaller webhooks send this
    private String payload; // Raw base64 payload in some SDKs
    private String signature; // Request signature for verification
    
    // For simpler setups, sometimes it directly sends profile data
    private String phoneNumber;
    private String firstName;
    private String lastName;
}
