package com.bentork.ev_system.controller;

import com.bentork.ev_system.dto.request.TruecallerLoginRequest;
import com.bentork.ev_system.dto.request.TruecallerWebhookPayload;
import com.bentork.ev_system.dto.request.UserLoginRequest;
import com.bentork.ev_system.dto.request.UserSignupRequest;
import com.bentork.ev_system.model.User;
import com.bentork.ev_system.service.TruecallerAuthService;
import com.bentork.ev_system.service.interfaces.IUserAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserAuthController {

    private final IUserAuthService userAuthService;
    private final TruecallerAuthService truecallerAuthService;

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserSignupRequest request) {
        return ResponseEntity.ok(userAuthService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@Valid @RequestBody UserLoginRequest request) {
        return ResponseEntity.ok(userAuthService.login(request));
    }

    @PostMapping("/request-otp")
    public ResponseEntity<?> requestOtp(@RequestParam String email) {
        userAuthService.sendOtp(email);
        return ResponseEntity.ok("OTP sent to your email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String email,
            @RequestParam String otp, @RequestParam String newPassword) {
        userAuthService.resetPassword(email, otp, newPassword);
        return ResponseEntity.ok("Password reset successful.");
    }

    @GetMapping("/google-login-success")
    public ResponseEntity<?> googleLoginSuccess(@RequestParam String email) {
        return ResponseEntity.ok(userAuthService.googleLogin(email));
    }

    @PostMapping("/truecaller-login")
    public ResponseEntity<?> truecallerLogin(@Valid @RequestBody TruecallerLoginRequest request) {
        return ResponseEntity.ok(truecallerAuthService.login(request));
    }

    @PostMapping("/truecaller/webhook")
    public ResponseEntity<?> truecallerWebhook(@RequestBody TruecallerWebhookPayload payload) {
        truecallerAuthService.handleWebhook(payload);
        return ResponseEntity.ok("Webhook received");
    }

    @GetMapping("/truecaller/status/{requestId}")
    public ResponseEntity<?> getTruecallerStatus(@PathVariable String requestId) {
        return ResponseEntity.ok(truecallerAuthService.getLoginStatus(requestId));
    }

    @DeleteMapping("/delete-account")
    public ResponseEntity<?> deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails) {
        userAuthService.deleteAccount(userDetails.getUsername());
        return ResponseEntity.ok("Account deleted successfully.");
    }

    @GetMapping("/total")
    public ResponseEntity<Long> getTotalUsers() {
        return ResponseEntity.ok(userAuthService.getTotalUsers());
    }

    @GetMapping("/byemail/{email}")
    public ResponseEntity<User> getUserDetailsByEmail(@PathVariable String email) throws Exception {
        return ResponseEntity.ok(userAuthService.getUserDetailsByEmail(email));
    }

    @GetMapping("/all")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userAuthService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) throws Exception {
        return ResponseEntity.ok(userAuthService.getUserById(id));
    }
}