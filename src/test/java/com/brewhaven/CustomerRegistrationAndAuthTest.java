package com.brewhaven;

import com.brewhaven.dto.AuthRequest;
import com.brewhaven.dto.AuthResponse;
import com.brewhaven.dto.RegisterRequest;
import com.brewhaven.entity.Role;
import com.brewhaven.entity.User;
import com.brewhaven.exception.BadRequestException;
import com.brewhaven.repository.UserRepository;
import com.brewhaven.security.JwtTokenProvider;
import com.brewhaven.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CustomerRegistrationAndAuthTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("1. Customer registration with Name, Mobile, Password (no email) succeeds and assigns ROLE_CUSTOMER")
    void testCustomerRegistrationWithoutEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Priya Sharma");
        req.setPhone("9812345678");
        req.setPassword("priyaPass123");

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertNotNull(resp.getToken());
        assertEquals("Priya Sharma", resp.getName());
        assertNull(resp.getEmail());
        assertEquals("ROLE_CUSTOMER", resp.getRole());

        // Verify entity in repository
        User user = userRepository.findById(resp.getId()).orElse(null);
        assertNotNull(user);
        assertEquals("Priya Sharma", user.getName());
        assertEquals("Priya Sharma", user.getUsername());
        assertEquals("9812345678", user.getPhone());
        assertNull(user.getEmail());
        assertEquals(Role.ROLE_CUSTOMER, user.getRole());

        // Verify password hashing (BCrypt hash starts with $2a$ or $2b$ and matches password)
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$"));
        assertTrue(passwordEncoder.matches("priyaPass123", user.getPassword()));
        assertNotEquals("priyaPass123", user.getPassword());
    }

    @Test
    @DisplayName("2. Customer registration rejects duplicate mobile number")
    void testDuplicateMobileRejection() {
        RegisterRequest req1 = new RegisterRequest();
        req1.setName("Rohan Verma");
        req1.setPhone("9876500001");
        req1.setPassword("pass1234");
        authService.register(req1);

        RegisterRequest req2 = new RegisterRequest();
        req2.setName("Vikram Verma");
        req2.setPhone("9876500001"); // same mobile number
        req2.setPassword("pass5678");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req2));
        assertTrue(ex.getMessage().contains("Mobile number is already registered"));
    }

    @Test
    @DisplayName("3. Customer registration rejects duplicate name/username")
    void testDuplicateNameRejection() {
        RegisterRequest req1 = new RegisterRequest();
        req1.setName("Ananya Iyer");
        req1.setPhone("9876500002");
        req1.setPassword("pass1234");
        authService.register(req1);

        RegisterRequest req2 = new RegisterRequest();
        req2.setName("Ananya Iyer"); // same name
        req2.setPhone("9876500003");
        req2.setPassword("pass5678");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req2));
        assertTrue(ex.getMessage().contains("Name/Username is already taken"));
    }

    @Test
    @DisplayName("4. Customer registration validates 10-digit mobile number")
    void testInvalidMobileNumberFormat() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Dev Kumar");
        req.setPhone("12345"); // invalid length
        req.setPassword("pass1234");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("valid 10-digit number"));
    }

    @Test
    @DisplayName("5. Customer registration with optional email succeeds")
    void testCustomerRegistrationWithOptionalEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Kavita Rao");
        req.setPhone("9876500004");
        req.setEmail("kavita.rao@example.com");
        req.setPassword("pass1234");

        AuthResponse resp = authService.register(req);
        assertNotNull(resp);
        assertEquals("kavita.rao@example.com", resp.getEmail());

        User user = userRepository.findById(resp.getId()).orElse(null);
        assertNotNull(user);
        assertEquals("kavita.rao@example.com", user.getEmail());
    }

    @Test
    @DisplayName("6. Customer login using registered Name / Username succeeds and issues valid JWT")
    void testCustomerLoginWithUsername() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Suresh Menon");
        req.setPhone("9876500005");
        req.setPassword("sureshSecret");
        authService.register(req);

        AuthRequest authReq = new AuthRequest("Suresh Menon", "sureshSecret");
        AuthResponse authResp = authService.login(authReq);

        assertNotNull(authResp);
        assertNotNull(authResp.getToken());
        assertEquals("Suresh Menon", authResp.getName());
        assertEquals("ROLE_CUSTOMER", authResp.getRole());

        // Validate JWT
        assertTrue(jwtTokenProvider.validateToken(authResp.getToken()));
        assertEquals("ROLE_CUSTOMER", jwtTokenProvider.getRoleFromJWT(authResp.getToken()));
    }

    @Test
    @DisplayName("7. Customer login using Mobile Number succeeds and issues valid JWT")
    void testCustomerLoginWithMobileNumber() {
        RegisterRequest req = new RegisterRequest();
        req.setName("Deepak Joshi");
        req.setPhone("9876500006");
        req.setPassword("deepakSecret");
        authService.register(req);

        AuthRequest authReq = new AuthRequest("9876500006", "deepakSecret");
        AuthResponse authResp = authService.login(authReq);

        assertNotNull(authResp);
        assertNotNull(authResp.getToken());
        assertEquals("Deepak Joshi", authResp.getName());
        assertEquals("ROLE_CUSTOMER", authResp.getRole());
        assertTrue(jwtTokenProvider.validateToken(authResp.getToken()));
    }

    @Test
    @DisplayName("8. Staff and Admin login using email and password remains functional")
    void testStaffAndAdminLoginWithEmail() {
        // Staff login
        AuthRequest staffReq = new AuthRequest("staff@brewhaven.com", "staff123");
        AuthResponse staffResp = authService.login(staffReq);
        assertNotNull(staffResp);
        assertEquals("ROLE_STAFF", staffResp.getRole());
        assertTrue(jwtTokenProvider.validateToken(staffResp.getToken()));

        // Admin login
        AuthRequest adminReq = new AuthRequest("admin@brewhaven.com", "admin123");
        AuthResponse adminResp = authService.login(adminReq);
        assertNotNull(adminResp);
        assertEquals("ROLE_ADMIN", adminResp.getRole());
        assertTrue(jwtTokenProvider.validateToken(adminResp.getToken()));
    }

    @Test
    @DisplayName("9. Login with invalid password fails with 401 / BadCredentialsException")
    void testLoginWithInvalidPassword() {
        AuthRequest badReq = new AuthRequest("admin@brewhaven.com", "wrongPassword");
        assertThrows(BadCredentialsException.class, () -> authService.login(badReq));
    }
}
