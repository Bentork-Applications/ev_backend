package com.bentork.ev_system.controller;

import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bentork.ev_system.config.JwtUtil;
import com.bentork.ev_system.dto.request.AdminLoginRequest;
import com.bentork.ev_system.dto.request.AdminSignupRequest;
import com.bentork.ev_system.dto.request.GoogleLoginResponse;
import com.bentork.ev_system.dto.request.JwtResponse;
import com.bentork.ev_system.dto.request.UserLoginRequest;
import com.bentork.ev_system.dto.request.UserSignupRequest;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.User;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.UserRepository;
import com.bentork.ev_system.service.AdminNotificationService;
import com.bentork.ev_system.service.OtpDeliveryService;
import com.bentork.ev_system.service.OtpService;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private UserRepository userRepo;
    @Autowired
    private AdminRepository adminRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private OtpDeliveryService otpDeliveryService;
    @Autowired
    private OtpService otpService;
    @Autowired
    private AdminNotificationService adminNotificationService;

    @PostMapping("/user/signup")
    public ResponseEntity<?> registerUser(@RequestBody UserSignupRequest request) {
        if (userRepo.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already in use");
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setMobile(request.getMobile());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepo.save(user);

        // 🔔 Admin notification: User Registered
        adminNotificationService.notifyNewUserRegistration(user.getName());

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/user/login")
    public ResponseEntity<?> loginUser(@RequestBody UserLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmailOrMobile(), request.getPassword()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);
        return ResponseEntity.ok(new JwtResponse(token));
    }

    // Total users - admin only
    @GetMapping("/users/total")
    public ResponseEntity<Long> getTotalUsers(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(userRepo.count());
    }

    // user details from email - authentication using token (admin, user)
    @GetMapping("/user/byemail/{email}")
    public ResponseEntity<User> getUserDetailsByEmail(@RequestHeader("Authorization") String authHeader,
            @PathVariable String email) throws UserPrincipalNotFoundException {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UserPrincipalNotFoundException("User with email '" + email + "' not found."));
        return ResponseEntity.ok(user);
    }

    // Delete user
    @DeleteMapping("/user/delete/{id}")
    public ResponseEntity<?> deleteUserById(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {
        try {
            userRepo.deleteById(id);
            return ResponseEntity.ok("User Deleted Successfully");
        } catch (DataAccessException e) {
            throw new RuntimeException("Database error while fetching user", e);
        }
    }

    @PostMapping("/admin/signup")
    public ResponseEntity<?> registerAdmin(@RequestBody AdminSignupRequest request) {
        if (adminRepo.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Admin email already in use");
        }
        Admin admin = new Admin();
        admin.setName(request.getName());
        admin.setEmail(request.getEmail());
        admin.setMobile(request.getMobile());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setRole("ADMIN");
        adminRepo.save(admin);
        return ResponseEntity.ok("Admin registered successfully");
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> loginAdmin(@RequestBody AdminLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmailOrMobile(), request.getPassword()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);
        return ResponseEntity.ok(new JwtResponse(token));
    }

    // Total Admin - admin only
    @GetMapping("/admin/all/total")
    public ResponseEntity<Long> getAllAdminTotal(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(adminRepo.count());
    }

    // Total Admin Only - admin only
    @GetMapping("/admin/total")
    public ResponseEntity<Long> getTotalAdmin(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(adminRepo.countAdminByRole("ADMIN"));
    }

    // Total Dealer - admin only
    @GetMapping("/dealer/total")
    public ResponseEntity<Long> getTotalDealer(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(adminRepo.countAdminByRole("DEALER"));
    }

    // list of admin present, including dealer
    @GetMapping("/admin/alladmin")
    public ResponseEntity<List<Admin>> getAllAdmin(@RequestHeader("Authorization") String authHeader) {
        try {
            List<Admin> admins = adminRepo.findAll();
            return ResponseEntity.ok(admins);
        } catch (DataAccessException e) {
            throw new RuntimeException("Database error while fetching admins", e);
        }
    }

    @PostMapping("/user/request-otp")
    public ResponseEntity<?> requestOtp(@RequestParam String email) {
        Optional<User> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty())
            return ResponseEntity.badRequest().body("Email not found");

        String otp = otpService.generateOtp(email);
        otpDeliveryService.sendOtp(email, otp);
        return ResponseEntity.ok("OTP sent to your email.");
    }

    @PostMapping("/user/reset-password")
    public ResponseEntity<?> resetPasswordViaOtp(
            @RequestParam String email,
            @RequestParam String otp,
            @RequestParam String newPassword) {
        if (!otpService.validateOtp(email, otp)) {
            return ResponseEntity.badRequest().body("Invalid OTP");
        }

        Optional<User> userOpt = userRepo.findByEmail(email);
        if (userOpt.isEmpty())
            return ResponseEntity.badRequest().body("User not found");

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        otpService.clearOtp(email);

        return ResponseEntity.ok("Password reset successful.");
    }

    @PostMapping("/admin/request-otp")
    public ResponseEntity<?> requestAdminOtp(@RequestParam String email) {
        Optional<Admin> adminOpt = adminRepo.findByEmail(email);
        if (adminOpt.isEmpty())
            return ResponseEntity.badRequest().body("Admin email not found");

        String otp = otpService.generateOtp(email);
        otpDeliveryService.sendOtp(email, otp);
        return ResponseEntity.ok("OTP sent to admin email.");
    }

    @PostMapping("/admin/reset-password")
    public ResponseEntity<?> resetAdminPasswordViaOtp(
            @RequestParam String otp,
            @RequestParam String newPassword) {
        String email = otpService.getEmailByOtp(otp);
        if (email == null) {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }

        Optional<Admin> adminOpt = adminRepo.findByEmail(email);
        if (adminOpt.isEmpty())
            return ResponseEntity.badRequest().body("Admin not found");

        Admin admin = adminOpt.get();
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminRepo.save(admin);
        otpService.clearOtp(email);

        return ResponseEntity.ok("Password reset successful.");
    }

    @GetMapping("/user/google-login-success")
    public ResponseEntity<?> googleLoginSuccess(@RequestParam String email) {
        Optional<User> optionalUser = userRepo.findByEmail(email);

        User user = optionalUser.orElseThrow();

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(), "", Collections.emptyList());

        String token = jwtUtil.generateToken(userDetails);
        // return ResponseEntity.ok(new JwtResponse(token));
        return ResponseEntity.ok(
                new GoogleLoginResponse(
                        token,
                        user.getName(),
                        user.getEmail(),
                        user.getImageUrl()));
    }

}