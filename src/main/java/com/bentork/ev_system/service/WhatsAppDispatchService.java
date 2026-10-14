package com.bentork.ev_system.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for dispatching WhatsApp messages for CRM Campaigns.
 * Supports Meta Cloud API in production, and an automated Simulation/Dev mode when keys are not configured.
 */
@Service
@Slf4j
public class WhatsAppDispatchService {

    @Value("${crm.whatsapp.enabled:false}")
    private boolean whatsAppEnabled;

    @Value("${crm.whatsapp.api-url:https://graph.facebook.com/v19.0}")
    private String apiUrl;

    @Value("${crm.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${crm.whatsapp.access-token:}")
    private String accessToken;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Send a WhatsApp message to a recipient.
     *
     * @param toPhone recipient mobile number
     * @param messageTemplate text or template message
     * @param contactName recipient's name
     * @param companyName recipient's company name
     */
    public void sendWhatsAppMessage(String toPhone, String messageTemplate, String contactName, String companyName) {
        if (toPhone == null || toPhone.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient phone number is missing");
        }

        String normalizedPhone = normalizePhoneNumber(toPhone.trim());
        String resolvedBody = formatTemplate(messageTemplate, contactName, companyName);

        if (whatsAppEnabled && phoneNumberId != null && !phoneNumberId.isEmpty() && accessToken != null && !accessToken.isEmpty()) {
            sendViaMetaCloudApi(normalizedPhone, resolvedBody);
        } else {
            // Simulated / Mock dispatch for development and staging
            log.info("[SIMULATED WHATSAPP DISPATCH] To: {}, Message: '{}'", normalizedPhone, resolvedBody);
        }
    }

    /**
     * Dispatch live message via Meta WhatsApp Business Cloud API.
     */
    private void sendViaMetaCloudApi(String phone, String text) {
        try {
            String url = apiUrl + "/" + phoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            Map<String, Object> body = new HashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("recipient_type", "individual");
            body.put("to", phone);
            body.put("type", "text");

            Map<String, String> textObj = new HashMap<>();
            textObj.put("preview_url", "false");
            textObj.put("body", text);
            body.put("text", textObj);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Live WhatsApp message sent successfully to {}", phone);
            } else {
                throw new RuntimeException("WhatsApp API responded with status " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", phone, e.getMessage());
            throw new RuntimeException("WhatsApp delivery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Normalize Indian phone numbers to E.164 without '+' (e.g. 919876543210).
     */
    public String normalizePhoneNumber(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "91" + digits; // Default India country code
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            return "91" + digits.substring(1);
        }
        return digits;
    }

    /**
     * Substitute dynamic personalization placeholders.
     */
    public String formatTemplate(String template, String contactName, String companyName) {
        if (template == null) {
            return "";
        }
        String safeContact = (contactName != null && !contactName.isEmpty()) ? contactName : "Customer";
        String safeCompany = (companyName != null && !companyName.isEmpty()) ? companyName : "Valued Partner";

        return template
                .replace("{{contactName}}", safeContact)
                .replace("{{contact_name}}", safeContact)
                .replace("{{companyName}}", safeCompany)
                .replace("{{company_name}}", safeCompany);
    }
}
