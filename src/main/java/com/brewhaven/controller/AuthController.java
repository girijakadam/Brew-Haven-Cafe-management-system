package com.brewhaven.controller;

import com.brewhaven.dto.ApiResponse;
import com.brewhaven.dto.AuthRequest;
import com.brewhaven.dto.AuthResponse;
import com.brewhaven.dto.ChangePasswordRequest;
import com.brewhaven.dto.RegisterRequest;
import com.brewhaven.entity.User;
import com.brewhaven.security.UserPrincipal;
import com.brewhaven.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        User user = authService.getCurrentUser(principal);
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("username", user.getUsername() != null ? user.getUsername() : "");
        map.put("email", user.getEmail() != null ? user.getEmail() : "");
        map.put("phone", user.getPhone() != null ? user.getPhone() : "");
        map.put("role", user.getRole().name());
        map.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        return ResponseEntity.ok(map);
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        AuthResponse response = authService.changePassword(principal, request);
        return ResponseEntity.ok(response);
    }
}
