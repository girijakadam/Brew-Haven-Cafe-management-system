package com.brewhaven;

import com.brewhaven.controller.AdminController;
import com.brewhaven.controller.AuthController;
import com.brewhaven.dto.*;
import com.brewhaven.entity.*;
import com.brewhaven.exception.BadRequestException;
import com.brewhaven.exception.ResourceNotFoundException;
import com.brewhaven.repository.MenuItemRepository;
import com.brewhaven.repository.UserRepository;
import com.brewhaven.security.JwtTokenProvider;
import com.brewhaven.security.UserPrincipal;
import com.brewhaven.service.AuthService;
import com.brewhaven.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AdminPasswordResetTest {

    @Autowired
    private AdminController adminController;

    @Autowired
    private AuthController authController;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User adminUser;
    private User customerUser;
    private User staffUser;
    private UserPrincipal adminPrincipal;
    private UserPrincipal customerPrincipal;
    private UserPrincipal staffPrincipal;

    @BeforeEach
    void setUp() {
        // Find or create admin
        adminUser = userRepository.findByEmail("admin@brewhaven.com").orElseGet(() -> {
            User a = new User("Admin", "admin@brewhaven.com", passwordEncoder.encode("admin123"), "9999999990", Role.ROLE_ADMIN);
            return userRepository.save(a);
        });
        adminPrincipal = UserPrincipal.create(adminUser);

        // Create test customer
        RegisterRequest reqC = new RegisterRequest();
        reqC.setName("Test Customer " + System.nanoTime());
        reqC.setPhone("98" + (System.currentTimeMillis() % 100000000L));
        reqC.setPassword("origPass123");
        AuthResponse respC = authService.register(reqC);
        customerUser = userRepository.findById(respC.getId()).orElseThrow();
        customerPrincipal = UserPrincipal.create(customerUser);

        // Create test staff
        User s = new User("Test Barista " + System.nanoTime(), "staff" + System.nanoTime() + "@brewhaven.com", passwordEncoder.encode("origStaff123"), "97" + (System.currentTimeMillis() % 100000000L), Role.ROLE_STAFF);
        staffUser = userRepository.save(s);
        staffPrincipal = UserPrincipal.create(staffUser);
    }

    @Test
    @WithMockUser(username = "admin@brewhaven.com", roles = {"ADMIN"})
    @DisplayName("1. Admin can reset customer password to temporary password")
    void testAdminCanResetCustomerPassword() {
        AdminResetPasswordRequest resetReq = new AdminResetPasswordRequest("tempPass123", "tempPass123");
        ResponseEntity<Map<String, Object>> resp = adminController.resetUserPassword(adminPrincipal, customerUser.getId(), resetReq);

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());
        assertTrue((Boolean) resp.getBody().get("mustChangePassword"));

        // Verify updated entity
        User updatedCustomer = userRepository.findById(customerUser.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(updatedCustomer.getMustChangePassword()));
        assertNotNull(updatedCustomer.getPasswordResetAt());
        assertEquals(adminPrincipal.getUsername(), updatedCustomer.getPasswordResetBy());
        assertEquals(Role.ROLE_CUSTOMER, updatedCustomer.getRole()); // Role preserved!

        // Verify password is BCrypt encoded
        assertTrue(passwordEncoder.matches("tempPass123", updatedCustomer.getPassword()));
        assertFalse(passwordEncoder.matches("origPass123", updatedCustomer.getPassword()));
    }

    @Test
    @WithMockUser(username = "admin@brewhaven.com", roles = {"ADMIN"})
    @DisplayName("2. Admin can reset staff password to temporary password")
    void testAdminCanResetStaffPassword() {
        AdminResetPasswordRequest resetReq = new AdminResetPasswordRequest("tempStaff456", "tempStaff456");
        ResponseEntity<Map<String, Object>> resp = adminController.resetUserPassword(adminPrincipal, staffUser.getId(), resetReq);

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode().value());

        User updatedStaff = userRepository.findById(staffUser.getId()).orElseThrow();
        assertTrue(Boolean.TRUE.equals(updatedStaff.getMustChangePassword()));
        assertEquals(Role.ROLE_STAFF, updatedStaff.getRole()); // Role preserved!
        assertTrue(passwordEncoder.matches("tempStaff456", updatedStaff.getPassword()));
    }

    @Test
    @WithMockUser(username = "admin@brewhaven.com", roles = {"ADMIN"})
    @DisplayName("3. Reset password validates confirmation match and minimum length")
    void testResetPasswordValidation() {
        // Mismatch
        AdminResetPasswordRequest mismatchReq = new AdminResetPasswordRequest("tempPass123", "wrongConfirm");
        assertThrows(BadRequestException.class, () ->
                adminController.resetUserPassword(adminPrincipal, customerUser.getId(), mismatchReq)
        );

        // Short password
        AdminResetPasswordRequest shortReq = new AdminResetPasswordRequest("123", "123");
        assertThrows(BadRequestException.class, () ->
                adminController.resetUserPassword(adminPrincipal, customerUser.getId(), shortReq)
        );

        // Non-existent target user
        AdminResetPasswordRequest validReq = new AdminResetPasswordRequest("tempPass123", "tempPass123");
        assertThrows(ResourceNotFoundException.class, () ->
                adminController.resetUserPassword(adminPrincipal, 9999999L, validReq)
        );
    }

    @Test
    @WithMockUser(username = "customer@brewhaven.com", roles = {"CUSTOMER"})
    @DisplayName("3b. Customer cannot use admin reset password endpoint")
    void testCustomerCannotUseAdminResetEndpoint() {
        AdminResetPasswordRequest resetReq = new AdminResetPasswordRequest("tempPass123", "tempPass123");
        assertThrows(AccessDeniedException.class, () ->
                adminController.resetUserPassword(customerPrincipal, customerUser.getId(), resetReq)
        );
    }

    @Test
    @WithMockUser(username = "staff@brewhaven.com", roles = {"STAFF"})
    @DisplayName("3c. Staff cannot use admin reset password endpoint")
    void testStaffCannotUseAdminResetEndpoint() {
        AdminResetPasswordRequest resetReq = new AdminResetPasswordRequest("tempPass123", "tempPass123");
        assertThrows(AccessDeniedException.class, () ->
                adminController.resetUserPassword(staffPrincipal, customerUser.getId(), resetReq)
        );
    }

    @Test
    @WithMockUser(username = "admin@brewhaven.com", roles = {"ADMIN"})
    @DisplayName("4. Temporary password forces mustChangePassword flag on login")
    void testTemporaryPasswordForcesPasswordChange() {
        // Admin resets password
        AdminResetPasswordRequest resetReq = new AdminResetPasswordRequest("tempSecret789", "tempSecret789");
        adminController.resetUserPassword(adminPrincipal, customerUser.getId(), resetReq);

        // Customer logs in with temporary password
        AuthRequest loginReq = new AuthRequest(customerUser.getName(), "tempSecret789");
        AuthResponse loginResp = authService.login(loginReq);

        assertNotNull(loginResp);
        assertNotNull(loginResp.getToken());
        assertTrue(loginResp.isMustChangePassword(), "AuthResponse must flag mustChangePassword as true");

        // Old password no longer works
        AuthRequest oldLoginReq = new AuthRequest(customerUser.getName(), "origPass123");
        assertThrows(BadCredentialsException.class, () -> authService.login(oldLoginReq));
    }

    @Test
    @WithMockUser(username = "admin@brewhaven.com", roles = {"ADMIN"})
    @DisplayName("5. User can change temporary password and gain normal access")
    void testUserCanChangePasswordSuccessfully() {
        // Admin resets password
        adminController.resetUserPassword(adminPrincipal, customerUser.getId(), new AdminResetPasswordRequest("tempPass999", "tempPass999"));

        // Authenticate with temporary password
        AuthResponse loginResp = authService.login(new AuthRequest(customerUser.getName(), "tempPass999"));
        assertTrue(loginResp.isMustChangePassword());

        UserPrincipal tempPrincipal = UserPrincipal.create(userRepository.findById(customerUser.getId()).orElseThrow());

        // Change password
        ChangePasswordRequest changeReq = new ChangePasswordRequest("tempPass999", "brandNewPassword123", "brandNewPassword123");
        AuthResponse changeResp = authService.changePassword(tempPrincipal, changeReq);

        assertNotNull(changeResp);
        assertFalse(changeResp.isMustChangePassword(), "mustChangePassword must be false after change");

        // Verify entity in repository
        User updated = userRepository.findById(customerUser.getId()).orElseThrow();
        assertFalse(Boolean.TRUE.equals(updated.getMustChangePassword()));
        assertTrue(passwordEncoder.matches("brandNewPassword123", updated.getPassword()));

        // Login with new password works normally
        AuthResponse normalLogin = authService.login(new AuthRequest(customerUser.getName(), "brandNewPassword123"));
        assertNotNull(normalLogin);
        assertFalse(normalLogin.isMustChangePassword());

        // Temporary password no longer works
        assertThrows(BadCredentialsException.class, () ->
                authService.login(new AuthRequest(customerUser.getName(), "tempPass999"))
        );
    }

    @Test
    @WithMockUser(username = "admin@brewhaven.com", roles = {"ADMIN"})
    @DisplayName("6. Existing passwords and password hashes are never exposed in API responses")
    void testPasswordsNeverExposedInResponses() {
        // Check Admin getAllUsers
        ResponseEntity<List<Map<String, Object>>> usersResp = adminController.getAllUsers();
        assertNotNull(usersResp.getBody());
        for (Map<String, Object> userMap : usersResp.getBody()) {
            assertFalse(userMap.containsKey("password"), "User list must not contain 'password'");
            assertFalse(userMap.containsKey("passwordHash"), "User list must not contain 'passwordHash'");
        }

        // Check reset response
        ResponseEntity<Map<String, Object>> resetResp = adminController.resetUserPassword(
                adminPrincipal, customerUser.getId(), new AdminResetPasswordRequest("tempPass678", "tempPass678")
        );
        assertFalse(resetResp.getBody().containsKey("password"));
        assertFalse(resetResp.getBody().containsKey("newPassword"));
        assertFalse(resetResp.getBody().containsKey("passwordHash"));

        // Check getCurrentUser /me
        ResponseEntity<Map<String, Object>> meResp = authController.getCurrentUser(customerPrincipal);
        assertFalse(meResp.getBody().containsKey("password"));
        assertFalse(meResp.getBody().containsKey("passwordHash"));
    }
}
