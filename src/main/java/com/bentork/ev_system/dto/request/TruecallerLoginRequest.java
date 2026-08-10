package com.bentork.ev_system.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

@Data
public class TruecallerLoginRequest {

    @NotBlank(message = "Authorization code is required")
    private String authorizationCode;

    @NotBlank(message = "Code verifier is required")
    private String codeVerifier;

    private String clientType; // "MOBILE" or "WEB"

    private boolean consentToTerms;

    private boolean consentToDataProcessing;

    @AssertTrue(message = "You must explicitly acknowledge being 18 years of age or older")
    private boolean isAdult;
}
