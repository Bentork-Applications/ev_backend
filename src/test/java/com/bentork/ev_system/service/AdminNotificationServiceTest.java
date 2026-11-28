package com.bentork.ev_system.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.AdminNotification;
import com.bentork.ev_system.repository.AdminNotificationRepository;
import com.bentork.ev_system.repository.AdminRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock
    private AdminNotificationRepository notificationRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PushNotificationService pushService;

    private AdminNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new AdminNotificationService(notificationRepository, adminRepository, pushService);
    }

    @Test
    @DisplayName("notifyAdminsOfLostRequest notifies only active ADMIN role users and triggers push")
    void testNotifyAdminsOfLostRequest_OnlySuperAdminsNotified() {
        Admin superAdmin = new Admin();
        superAdmin.setId(1L);
        superAdmin.setName("Main Admin");
        superAdmin.setRole("ADMIN");
        superAdmin.setFcmToken("fcm-admin-1");
        superAdmin.setActive(true);

        Admin salesExec = new Admin();
        salesExec.setId(2L);
        salesExec.setName("Sales Rep");
        salesExec.setRole("SALES_ADMIN");
        salesExec.setActive(true);

        when(adminRepository.findByActiveTrue()).thenReturn(List.of(superAdmin, salesExec));

        notificationService.notifyAdminsOfLostRequest(
                "Lead", 10L, "LD-20261128-0010", "48V Pack", "contacted", "Lost to competitor", salesExec);

        ArgumentCaptor<AdminNotification> captor = ArgumentCaptor.forClass(AdminNotification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        AdminNotification saved = captor.getValue();
        assertEquals(1L, saved.getAdmin().getId());
        assertEquals("CRM_LEAD_LOST_REQUEST", saved.getType());
        assertTrue(saved.getMessage().contains("LD-20261128-0010"));
        assertTrue(saved.getMessage().contains("Lost to competitor"));
        assertFalse(saved.isRead());

        verify(pushService, times(1)).sendNotification(eq("fcm-admin-1"), anyString(), anyString());
    }

    @Test
    @DisplayName("notifyAdminOfLostDecision creates decision notification for requesting sales rep")
    void testNotifyAdminOfLostDecision() {
        Admin superAdmin = new Admin();
        superAdmin.setId(1L);
        superAdmin.setName("Main Admin");

        Admin salesExec = new Admin();
        salesExec.setId(2L);
        salesExec.setName("Sales Rep");
        salesExec.setFcmToken("fcm-sales-2");

        notificationService.notifyAdminOfLostDecision(
                salesExec, "REVOKED", "Opportunity", "OPP-100", "Fleet Deal", "proposal", "Reopened", superAdmin);

        ArgumentCaptor<AdminNotification> captor = ArgumentCaptor.forClass(AdminNotification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        AdminNotification saved = captor.getValue();
        assertEquals(2L, saved.getAdmin().getId());
        assertEquals("CRM_OPPORTUNITY_LOST_REVOKED", saved.getType());
        assertTrue(saved.getMessage().contains("OPP-100"));
        assertTrue(saved.getMessage().contains("proposal"));
        verify(pushService, times(1)).sendNotification(eq("fcm-sales-2"), anyString(), anyString());
    }
}
