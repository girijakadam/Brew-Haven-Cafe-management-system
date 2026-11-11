package com.brewhaven.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthRequest {

    private String username;
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    public AuthRequest() {
    }

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        if (username != null && !username.trim().isEmpty()) {
            return username.trim();
        }
        return email != null ? email.trim() : null;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        if (email != null && !email.trim().isEmpty()) {
            return email.trim();
        }
        return username != null ? username.trim() : null;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
