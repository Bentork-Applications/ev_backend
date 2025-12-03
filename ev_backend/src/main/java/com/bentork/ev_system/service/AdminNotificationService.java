package com.bentork.ev_system.service;

import com.bentork.ev_system.dto.request.AdminNotificationDTO;
import com.bentork.ev_system.mapper.AdminNotificationMapper;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.AdminNotification;
import com.bentork.ev_system.repository.AdminNotificationRepository;
import com.bentork.ev_system.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminNotificationService {

    @Autowired
    private AdminNotificationRepository notificationRepository;

    @Autowired
    private AdminRepository adminRepository;

    // 🔔 CREATE notification for new user registration
    public void notifyNewUserRegistration(String userName) {
        // You can improve this by looping through all admins (if multiple)
        List<Admin> admins = adminRepository.findAll();

        for (Admin admin : admins) {
            AdminNotification notification = new AdminNotification();
            notification.setAdmin(admin);
            notification.setType("NEW_USER");
            notification.setMessage("New user registered: " + userName);
            notification.setRead(false);
            notificationRepository.save(notification);
        }
    }

    // 📥 Get notifications by adminId
    public List<AdminNotificationDTO> getNotificationsByAdminId(Long adminId) {
        return notificationRepository.findByAdminId(adminId)
                .stream()
                .map(AdminNotificationMapper::toDTO)
                .collect(Collectors.toList());
    }

    // ✅ Mark notification as read
    public void markAsRead(Long notificationId) {
        AdminNotification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification != null) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    public void createSystemNotification(String message, String type) {
        List<Admin> admins = adminRepository.findAll();

        for (Admin admin : admins) {
            AdminNotification notification = new AdminNotification();
            notification.setAdmin(admin);
            notification.setMessage(message);
            notification.setType(type);
            notification.setCreatedAt(LocalDateTime.now());
            notification.setRead(false);
            notificationRepository.save(notification);
        }
    }

}
