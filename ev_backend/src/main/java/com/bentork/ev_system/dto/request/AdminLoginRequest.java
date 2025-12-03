package com.bentork.ev_system.dto.request;

public class AdminLoginRequest {
    private String emailOrMobile;
    private String password;

    // Getters
    public String getEmailOrMobile() {
        return emailOrMobile;
    }

    public String getPassword() {
        return password;
    }

    // Setters
    public void setEmailOrMobile(String emailOrMobile) {
        this.emailOrMobile = emailOrMobile;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

