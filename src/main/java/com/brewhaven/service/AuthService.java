package com.brewhaven.service;

import com.brewhaven.dto.AuthRequest;
import com.brewhaven.dto.AuthResponse;
import com.brewhaven.dto.ChangePasswordRequest;
import com.brewhaven.dto.RegisterRequest;
import com.brewhaven.entity.Role;
import com.brewhaven.entity.User;
import com.brewhaven.exception.BadRequestException;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.UserRepository;
import com.brewhaven.security.JwtTokenProvider;
import com.brewhaven.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    public AuthResponse login(AuthRequest request) {
        String identifier = request.getUsername();
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BadRequestException("Username, mobile number, or email is required");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new BadRequestException("Password is required");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier.trim(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = tokenProvider.generateToken(authentication);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        return new AuthResponse(token, user.getId(), user.getName(), user.getEmail(), user.getRole().name(), mustChange);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Mobile number validation & uniqueness
        String phone = request.getPhone() != null ? request.getPhone().trim() : "";
        if (!phone.matches("^[0-9]{10}$")) {
            throw new BadRequestException("Mobile number must be a valid 10-digit number");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Mobile number is already registered: " + phone);
        }

        // 2. Name validation & uniqueness
        String name = request.getName() != null ? request.getName().trim() : "";
        if (name.length() < 2 || name.length() > 50) {
            throw new BadRequestException("Full name must be between 2 and 50 characters");
        }

        String username = request.getUsername() != null && !request.getUsername().trim().isEmpty()
                ? request.getUsername().trim()
                : name;

        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByNameIgnoreCase(username)) {
            throw new BadRequestException("Name/Username is already taken: " + username + ". Please choose a unique name.");
        }

        // 3. Password validation
        String password = request.getPassword();
        if (password == null || password.length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters");
        }

        // 4. Optional email validation & uniqueness
        String email = null;
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            email = request.getEmail().trim().toLowerCase();
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new BadRequestException("Email is already registered: " + email);
            }
        }

        // 5. Create customer account - automatically assign ROLE_CUSTOMER
        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.ROLE_CUSTOMER);

        User savedUser = userRepository.save(user);

        // 6. Generate JWT and return AuthResponse
        String jwtSubject = savedUser.getUsername() != null ? savedUser.getUsername() : savedUser.getPhone();
        String token = tokenProvider.generateTokenForUser(
                jwtSubject,
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getRole().name()
        );

        return new AuthResponse(token, savedUser.getId(), savedUser.getName(), savedUser.getEmail(), savedUser.getRole().name());
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(UserPrincipal principal) {
        if (principal == null) {
            throw new BadRequestException("Not authenticated");
        }
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));
    }

    @Transactional
    public AuthResponse changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        if (principal == null) {
            throw new BadRequestException("Not authenticated");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId()));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current/temporary password is incorrect");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as the current/temporary password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        String jwtSubject = savedUser.getUsername() != null ? savedUser.getUsername() : savedUser.getPhone();
        String token = tokenProvider.generateTokenForUser(
                jwtSubject,
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getRole().name(),
                false
        );

        return new AuthResponse(token, savedUser.getId(), savedUser.getName(), savedUser.getEmail(), savedUser.getRole().name(), false);
    }
}
