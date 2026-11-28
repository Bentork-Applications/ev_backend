package com.bentork.ev_system.service.interfaces;

import com.bentork.ev_system.dto.request.AdminNotificationDTO;
import com.bentork.ev_system.model.Admin;

import java.util.List;

/**
 * Interface for admin notification operations.
 */
public interface IAdminNotificationService {
    void createSystemNotification(String message, String type);
    void notifyNewUserRegistration(String userName);
    List<AdminNotificationDTO> getNotificationsByAdminId(Long adminId);
    void markAsRead(Long notificationId);
    void notifyAdminsOfLostRequest(String entityType, Long entityId, String entityNumber, String title,
            String previousStage, String lostReason, Admin requestedBy);
    void notifyAdminOfLostDecision(Admin targetAdmin, String decision, String entityType, String entityNumber,
            String title, String targetStage, String reason, Admin decidedBy);
}
