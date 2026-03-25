package com.bentork.ev_system.dto.request;

public class GoogleLoginResponse {

    private String token;
    private String name;
    private String email;
    private String imageUrl;
    private String mobile;

    public GoogleLoginResponse(String token, String name, String email, String imageUrl, String mobile) {
        this.token = token;
        this.name = name;
        this.email = email;
        this.imageUrl = imageUrl;
        this.mobile = mobile;
    }

    public String getToken() {
        return token;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getMobile() {
        return mobile;
    }
}