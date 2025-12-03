package com.bentork.ev_system.controller;

import com.bentork.ev_system.dto.request.AdminNotificationDTO;
import com.bentork.ev_system.service.AdminNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    @Autowired
    private AdminNotificationService notificationService;

    // Get all notifications for a given admin
    @GetMapping("/{adminId}")
    public List<AdminNotificationDTO> getNotifications(@PathVariable Long adminId) {
        return notificationService.getNotificationsByAdminId(adminId);
    }

    // Mark a notification as read
    @PutMapping("/mark-read/{id}")
    public void markNotificationAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
    }
}
