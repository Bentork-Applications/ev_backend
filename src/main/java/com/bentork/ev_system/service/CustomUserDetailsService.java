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
        Optional<User> userOpt = userRepo.findByEmailOrMobile(username, username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // User has no role field — assign default ROLE_USER
            return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
        }

        Optional<Admin> adminOpt = adminRepo.findByEmailOrMobile(username, username); 
        if (adminOpt.isPresent()) {
            Admin admin = adminOpt.get();
            String role = admin.getRole(); // should be "ADMIN" in DB
            return new org.springframework.security.core.userdetails.User(
                admin.getEmail(),
                admin.getPassword(),
                List.of(new SimpleGrantedAuthority(role)) // 👈 No "ROLE_" prefix
            );
        }
        throw new UsernameNotFoundException("No user or admin found with email/mobile: " + username);
    }
}

//package com.bentork.ev_system.service;
//
//import com.bentork.ev_system.model.User;
//import com.bentork.ev_system.model.Admin;
//import com.bentork.ev_system.repository.UserRepository;
//import com.bentork.ev_system.repository.AdminRepository;
//
//import java.util.Collections;
//import java.util.List;
//import java.util.Optional;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.stereotype.Service;
//
//@Service
//public class CustomUserDetailsService implements UserDetailsService {
//
//    @Autowired private UserRepository userRepo;
//    @Autowired private AdminRepository adminRepo;
//
//    @Override
//    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        Optional<User> userOpt = userRepo.findByEmailOrMobile(username, username);
//        if (userOpt.isPresent()) {
//            User user = userOpt.get();
//            return new org.springframework.security.core.userdetails.User(
//                user.getEmail(),
//                user.getPassword(),
//                Collections.emptyList()
//            );
//        }
//
//        Optional<Admin> adminOpt = adminRepo.findByEmailOrMobile(username, username);
//        if (adminOpt.isPresent()) {
//            Admin admin = adminOpt.get();
//            return new org.springframework.security.core.userdetails.User(
//                admin.getEmail(),
//                admin.getPassword(),
//                Collections.emptyList()
//            );
//        }
//
//        throw new UsernameNotFoundException("No user or admin found with email/mobile: " + username);
//    }
//}