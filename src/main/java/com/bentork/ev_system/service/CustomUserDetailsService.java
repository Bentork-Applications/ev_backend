package com.bentork.ev_system.service;

import com.bentork.ev_system.model.User;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.repository.UserRepository;
import com.bentork.ev_system.repository.AdminRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private AdminRepository adminRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Try to find User — search by email first (has unique constraint),
        // then fall back to mobile number lookup
        Optional<User> userOpt = userRepo.findByEmail(username);
        if (userOpt.isEmpty()) {
            userOpt = userRepo.findByMobile(username);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Handle Google OAuth users who may have no password set
            String password = user.getPassword();
            if (password == null || password.isEmpty()) {
                password = "";
            }
            return new org.springframework.security.core.userdetails.User(
                    user.getEmail(),
                    password,
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
        }

        // 2. Try to find Admin — same approach: email first, then mobile
        Optional<Admin> adminOpt = adminRepo.findByEmail(username);
        if (adminOpt.isEmpty()) {
            adminOpt = adminRepo.findByMobile(username);
        }

        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            String role = admin.getRole();
            return new org.springframework.security.core.userdetails.User(
                    admin.getEmail(),
                    admin.getPassword(),
                    List.of(new SimpleGrantedAuthority(role)));
        }

        throw new UsernameNotFoundException("No user or admin found with email/mobile: " + username);
    }
}