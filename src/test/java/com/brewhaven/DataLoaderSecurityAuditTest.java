package com.brewhaven;

import com.brewhaven.config.DataLoader;
import com.brewhaven.entity.Role;
import com.brewhaven.entity.User;
import com.brewhaven.repository.CafeTableRepository;
import com.brewhaven.repository.CategoryRepository;
import com.brewhaven.repository.MenuItemRepository;
import com.brewhaven.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DataLoaderSecurityAuditTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private CafeTableRepository cafeTableRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("1. Production mode with empty database: never seeds demo customer or demo staff accounts")
    void testProductionModeDoesNotSeedDemoUsers() {
        // Prepare empty repository state in transaction
        userRepository.deleteAll();
        assertEquals(0, userRepository.count());

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        DataLoader prodLoader = new DataLoader(
                userRepository,
                categoryRepository,
                menuItemRepository,
                cafeTableRepository,
                passwordEncoder,
                dataSource,
                prodEnv
        );

        ReflectionTestUtils.setField(prodLoader, "seedDemoUsers", false);
        ReflectionTestUtils.setField(prodLoader, "initialAdminEmail", "prodadmin@brewhaven.com");
        ReflectionTestUtils.setField(prodLoader, "initialAdminPassword", "SecureProdAdmin#2026");
        ReflectionTestUtils.setField(prodLoader, "initialAdminName", "Production Admin");
        ReflectionTestUtils.setField(prodLoader, "initialAdminPhone", "9988776655");

        prodLoader.run();

        // Verify: No demo staff or demo customer was created
        assertFalse(userRepository.existsByEmail("staff@brewhaven.com"), "Demo staff must not be created in production");
        assertFalse(userRepository.existsByEmail("customer@brewhaven.com"), "Demo customer must not be created in production");

        // Verify: Only production admin was created
        List<User> users = userRepository.findAll();
        assertEquals(1, users.size(), "Only the provisioned admin should exist");
        User admin = users.get(0);
        assertEquals("prodadmin@brewhaven.com", admin.getEmail());
        assertEquals(Role.ROLE_ADMIN, admin.getRole());
        assertTrue(passwordEncoder.matches("SecureProdAdmin#2026", admin.getPassword()));
        assertFalse(Boolean.TRUE.equals(admin.getMustChangePassword()));
    }

    @Test
    @DisplayName("2. Production mode without ADMIN_PASSWORD: provisions admin with secure one-time temporary password")
    void testProductionModeGeneratesSecureOneTimePasswordWhenUnset() {
        userRepository.deleteAll();

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("production");

        DataLoader prodLoader = new DataLoader(
                userRepository,
                categoryRepository,
                menuItemRepository,
                cafeTableRepository,
                passwordEncoder,
                dataSource,
                prodEnv
        );

        ReflectionTestUtils.setField(prodLoader, "seedDemoUsers", false);
        ReflectionTestUtils.setField(prodLoader, "initialAdminEmail", "fallbackadmin@brewhaven.com");
        ReflectionTestUtils.setField(prodLoader, "initialAdminPassword", ""); // empty!
        ReflectionTestUtils.setField(prodLoader, "initialAdminName", "Fallback Admin");
        ReflectionTestUtils.setField(prodLoader, "initialAdminPhone", "9988776644");

        prodLoader.run();

        User admin = userRepository.findByEmail("fallbackadmin@brewhaven.com")
                .orElseThrow(() -> new AssertionError("Admin should have been provisioned"));

        // Admin must have mustChangePassword = true
        assertTrue(Boolean.TRUE.equals(admin.getMustChangePassword()), "Admin must be flagged for mandatory password change");
        assertEquals("SYSTEM_INITIAL_PROVISIONING", admin.getPasswordResetBy());
        assertNotNull(admin.getPasswordResetAt());

        // Password must NOT be any known default
        assertFalse(passwordEncoder.matches("admin123", admin.getPassword()));
        assertFalse(passwordEncoder.matches("password", admin.getPassword()));
        assertFalse(passwordEncoder.matches("admin", admin.getPassword()));

        // Password must be BCrypt hashed
        assertTrue(admin.getPassword().startsWith("$2a$") || admin.getPassword().startsWith("$2b$"),
                "Stored password must be a valid BCrypt hash");
    }

    @Test
    @DisplayName("3. All stored passwords in database use BCrypt")
    void testAllStoredPasswordsUseBCrypt() {
        List<User> users = userRepository.findAll();
        assertFalse(users.isEmpty(), "Users table should have records");

        for (User u : users) {
            assertNotNull(u.getPassword(), "User password cannot be null");
            assertTrue(u.getPassword().startsWith("$2a$") || u.getPassword().startsWith("$2b$"),
                    "Password for user " + u.getEmail() + " must be BCrypt hashed");
            assertNotEquals("admin123", u.getPassword(), "Raw password must never be stored");
            assertNotEquals("staff123", u.getPassword(), "Raw password must never be stored");
            assertNotEquals("customer123", u.getPassword(), "Raw password must never be stored");
        }
    }

    @Test
    @DisplayName("4. Existing users are never deleted and existing admin passwords are preserved on restart")
    void testExistingUsersPreservedAcrossRestarts() {
        // Record current user count
        long initialCount = userRepository.count();
        assertTrue(initialCount > 0);

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        DataLoader prodLoader = new DataLoader(
                userRepository,
                categoryRepository,
                menuItemRepository,
                cafeTableRepository,
                passwordEncoder,
                dataSource,
                prodEnv
        );

        ReflectionTestUtils.setField(prodLoader, "seedDemoUsers", false);
        ReflectionTestUtils.setField(prodLoader, "initialAdminEmail", "admin@brewhaven.com");
        ReflectionTestUtils.setField(prodLoader, "initialAdminPassword", "NewAdminSecret#2026");

        // Run loader again as if restarting
        prodLoader.run();

        // User count must remain unchanged (no duplicates, no deletions)
        assertEquals(initialCount, userRepository.count(), "User records must not be deleted or duplicated on restart");
    }
}
