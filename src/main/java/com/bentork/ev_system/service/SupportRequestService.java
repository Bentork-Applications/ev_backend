package com.bentork.ev_system.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bentork.ev_system.dto.request.SupportRequestDTO;
import com.bentork.ev_system.dto.response.SupportRequestResponse;
import com.bentork.ev_system.enums.RequestStatus;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.SupportRequest;
import com.bentork.ev_system.model.User;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.SupportRequestRepository;
import com.bentork.ev_system.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportRequestService {

    private final SupportRequestRepository supportRequestRepository;
    private final PushNotificationService pushNotificationService;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public SupportRequestResponse createRequest(SupportRequestDTO dto, String customerType, String submitterEmail) {
        SupportRequest request = new SupportRequest();
        request.setCustomerFullName(dto.getCustomerFullName());
        request.setProduct(dto.getProduct());
        request.setIssueDescription(dto.getIssueDescription());
        request.setStatus(RequestStatus.PENDING.getValue());
        request.setCustomerType(customerType);
        request.setSubmitterEmail(submitterEmail);

        SupportRequest savedRequest = supportRequestRepository.save(request);
        log.info("Created support request with ID {} for {} ({})", savedRequest.getId(), submitterEmail, customerType);
        
        return mapToResponse(savedRequest);
    }

    public List<SupportRequestResponse> getRequestsBySubmitter(String email) {
        return supportRequestRepository.findBySubmitterEmailOrderByCreatedAtDesc(email).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<SupportRequestResponse> getAllRequests() {
        return supportRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<SupportRequestResponse> getRequestsByStatus(String status) {
        return supportRequestRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<SupportRequestResponse> getRequestsByCustomerType(String customerType) {
        return supportRequestRepository.findByCustomerTypeOrderByCreatedAtDesc(customerType).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SupportRequestResponse updateStatus(Long id, String newStatusString) {
        SupportRequest request = supportRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Support request not found with ID: " + id));

        RequestStatus currentStatus = RequestStatus.fromString(request.getStatus());
        RequestStatus newStatus = RequestStatus.fromString(newStatusString);

        if (newStatus == null) {
            throw new IllegalArgumentException("Invalid status: " + newStatusString);
        }

        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalArgumentException("Invalid status transition from " + currentStatus + " to " + newStatus);
        }

        request.setStatus(newStatus.getValue());
        SupportRequest updatedRequest = supportRequestRepository.save(request);
        log.info("Updated support request {} status to {}", id, newStatus.getValue());

        // Send push notification to the submitter
        sendPushNotification(updatedRequest);

        return mapToResponse(updatedRequest);
    }

    private void sendPushNotification(SupportRequest request) {
        String token = null;

        if ("END_USER".equals(request.getCustomerType())) {
            Optional<User> userOpt = userRepository.findByEmail(request.getSubmitterEmail());
            if (userOpt.isPresent()) {
                token = userOpt.get().getFcmToken();
            }
        } else if ("DEALER".equals(request.getCustomerType())) {
            Optional<Admin> adminOpt = adminRepository.findByEmail(request.getSubmitterEmail());
            if (adminOpt.isPresent()) {
                token = adminOpt.get().getFcmToken();
            }
        }

        if (token != null && !token.isEmpty()) {
            String title = "Support Request Update";
            String body = "Your support request regarding " + request.getProduct() + " is now: " + request.getStatus().replace("_", " ").toUpperCase();
            pushNotificationService.sendNotification(token, title, body);
        } else {
            log.warn("Could not send push notification for support request {}: No FCM token found for {}", request.getId(), request.getSubmitterEmail());
        }
    }

    private boolean isValidTransition(RequestStatus current, RequestStatus next) {
        if (current == RequestStatus.PENDING && next == RequestStatus.APPROVED) return true;
        if (current == RequestStatus.APPROVED && next == RequestStatus.IN_PROGRESS) return true;
        if (current == RequestStatus.IN_PROGRESS && next == RequestStatus.COMPLETED) return true;
        return false;
    }

    private SupportRequestResponse mapToResponse(SupportRequest request) {
        SupportRequestResponse response = new SupportRequestResponse();
        response.setId(request.getId());
        response.setCustomerFullName(request.getCustomerFullName());
        response.setProduct(request.getProduct());
        response.setIssueDescription(request.getIssueDescription());
        response.setStatus(request.getStatus());
        response.setCustomerType(request.getCustomerType());
        response.setSubmitterEmail(request.getSubmitterEmail());
        response.setCreatedAt(request.getCreatedAt());
        response.setUpdatedAt(request.getUpdatedAt());
        return response;
    }
}
