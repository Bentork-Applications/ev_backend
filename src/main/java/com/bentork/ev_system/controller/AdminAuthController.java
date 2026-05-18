package com.bentork.ev_system.controller;

import com.bentork.ev_system.dto.request.AdminLoginRequest;
import com.bentork.ev_system.dto.request.AdminSignupRequest;
import com.bentork.ev_system.dto.request.UpdateRoleRequest;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.service.interfaces.IAdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final IAdminAuthService adminAuthService;

    @PostMapping("/signup")
    public ResponseEntity<?> registerAdmin(@Valid @RequestBody AdminSignupRequest request) {
        return ResponseEntity.ok(adminAuthService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginAdmin(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }
    @GetMapping("/alladmin")
    public ResponseEntity<List<Admin>> getAllAdmin() {
        return ResponseEntity.ok(adminAuthService.getAllAdmins());
    }

    @PutMapping("/update-role")
    public ResponseEntity<?> updateRole(@RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(adminAuthService.updateRole(request));
    }

    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestParam String email) {
        adminAuthService.sendOtp(email);
        return ResponseEntity.ok("OTP sent to your email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String email,
            @RequestParam String otp, @RequestParam String newPassword) {
        adminAuthService.resetPassword(email, otp, newPassword);
        return ResponseEntity.ok("Password reset successful.");
    }
}