package com.brewhaven.config;

import com.brewhaven.entity.*;
import com.brewhaven.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Component
public class DataLoader implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final CafeTableRepository cafeTableRepository;
    private final PasswordEncoder passwordEncoder;
    private final javax.sql.DataSource dataSource;
    private final Environment environment;

    @Value("${app.security.seed-demo-users:true}")
    private boolean seedDemoUsers;

    @Value("${app.security.initial-admin.email:admin@brewhaven.com}")
    private String initialAdminEmail;

    @Value("${app.security.initial-admin.password:}")
    private String initialAdminPassword;

    @Value("${app.security.initial-admin.name:Admin Officer}")
    private String initialAdminName;

    @Value("${app.security.initial-admin.phone:9876543210}")
    private String initialAdminPhone;

    public DataLoader(UserRepository userRepository,
                      CategoryRepository categoryRepository,
                      MenuItemRepository menuItemRepository,
                      CafeTableRepository cafeTableRepository,
                      PasswordEncoder passwordEncoder,
                      javax.sql.DataSource dataSource,
                      Environment environment) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.cafeTableRepository = cafeTableRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        migrateSchema();
        seedUsers();
        backfillExistingUsers();
        seedCategoriesAndMenuItems();
        seedTables();
    }

    private void migrateSchema() {
        if (dataSource == null) return;
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            logger.info("Checking and applying schema migrations for users table...");

            // 1. Add username column if not exists
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(255)");
            } catch (Exception e) {
                logger.debug("ALTER TABLE ADD COLUMN username: {}", e.getMessage());
            }

            // 2. Allow NULL for email column
            try {
                stmt.execute("ALTER TABLE users ALTER COLUMN email DROP NOT NULL");
            } catch (Exception e1) {
                try {
                    stmt.execute("ALTER TABLE users ALTER COLUMN email SET NULL");
                } catch (Exception e2) {
                    try {
                        stmt.execute("ALTER TABLE users ALTER COLUMN email VARCHAR(255) NULL");
                    } catch (Exception e3) {
                        logger.debug("ALTER TABLE email NULL: {}", e3.getMessage());
                    }
                }
            }

            // 3. Create unique index on phone if not exists
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone ON users(phone)");
            } catch (Exception e) {
                logger.debug("CREATE UNIQUE INDEX on phone: {}", e.getMessage());
            }

            // 4. Create unique index on username if not exists
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username)");
            } catch (Exception e) {
                logger.debug("CREATE UNIQUE INDEX on username: {}", e.getMessage());
            }

            // 5. Add columns for admin-assisted password reset if not exist
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN DEFAULT FALSE");
            } catch (Exception e) {
                logger.debug("ALTER TABLE ADD must_change_password: {}", e.getMessage());
            }
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_by VARCHAR(255)");
            } catch (Exception e) {
                logger.debug("ALTER TABLE ADD password_reset_by: {}", e.getMessage());
            }
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_reset_at TIMESTAMP");
            } catch (Exception e) {
                logger.debug("ALTER TABLE ADD password_reset_at: {}", e.getMessage());
            }
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP");
            } catch (Exception e) {
                logger.debug("ALTER TABLE ADD password_changed_at: {}", e.getMessage());
            }

            // 6. Backfill username for existing accounts where username is null
            try {
                stmt.execute("UPDATE users SET username = TRIM(name) WHERE username IS NULL AND name IS NOT NULL AND TRIM(name) <> ''");
                stmt.execute("UPDATE users SET username = TRIM(email) WHERE username IS NULL AND email IS NOT NULL");
            } catch (Exception e) {
                logger.debug("Backfill username: {}", e.getMessage());
            }

            logger.info("Schema migrations checked and applied successfully.");
        } catch (Exception e) {
            logger.error("Error migrating database schema: {}", e.getMessage());
        }
    }

    public boolean isProductionEnvironment() {
        if (environment == null) return false;
        return environment.matchesProfiles("prod", "production") ||
               ("mysql".equalsIgnoreCase(environment.getProperty("spring.profiles.active")) && !seedDemoUsers);
    }

    public boolean shouldSeedDemoUsers() {
        if (isProductionEnvironment()) {
            return false;
        }
        return seedDemoUsers;
    }

    private void backfillExistingUsers() {
        try {
            List<User> allUsers = userRepository.findAll();
            for (User u : allUsers) {
                boolean modified = false;
                if (u.getUsername() == null || u.getUsername().trim().isEmpty()) {
                    u.setUsername(u.getName() != null && !u.getName().trim().isEmpty() ? u.getName().trim() : u.getEmail());
                    modified = true;
                }
                // Only sync demo accounts in development/test mode if demo accounts are explicitly enabled
                if (shouldSeedDemoUsers()) {
                    if ("staff@brewhaven.com".equalsIgnoreCase(u.getEmail())) {
                        if (Boolean.TRUE.equals(u.getMustChangePassword()) || !passwordEncoder.matches("staff123", u.getPassword())) {
                            u.setPassword(passwordEncoder.encode("staff123"));
                            u.setMustChangePassword(false);
                            modified = true;
                        }
                    }
                    if ("admin@brewhaven.com".equalsIgnoreCase(u.getEmail())) {
                        if (Boolean.TRUE.equals(u.getMustChangePassword()) || !passwordEncoder.matches("admin123", u.getPassword())) {
                            u.setPassword(passwordEncoder.encode("admin123"));
                            u.setMustChangePassword(false);
                            modified = true;
                        }
                    }
                }
                if (modified) {
                    userRepository.save(u);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not backfill existing users: {}", e.getMessage());
        }
    }

    private void seedUsers() {
        seedOrProvisionAdmin();
        if (shouldSeedDemoUsers()) {
            seedDemoStaffAndCustomer();
        } else {
            logger.info("Production mode active: Seed demo customer and staff accounts will NOT be created.");
        }
    }

    private void seedOrProvisionAdmin() {
        boolean adminExists = userRepository.findAll().stream()
                .anyMatch(u -> u.getRole() == Role.ROLE_ADMIN);

        if (adminExists) {
            logger.info("Admin account already exists in database. Skipping initial admin provisioning.");
            return;
        }

        String email = (initialAdminEmail != null && !initialAdminEmail.trim().isEmpty())
                ? initialAdminEmail.trim().toLowerCase()
                : "admin@brewhaven.com";
        String name = (initialAdminName != null && !initialAdminName.trim().isEmpty())
                ? initialAdminName.trim()
                : "Admin Officer";
        String phone = (initialAdminPhone != null && !initialAdminPhone.trim().isEmpty())
                ? initialAdminPhone.trim()
                : "9876543210";

        // Case 1: Secure environment-based password provided
        if (initialAdminPassword != null && !initialAdminPassword.trim().isEmpty()) {
            User admin = new User(
                    name,
                    email,
                    passwordEncoder.encode(initialAdminPassword.trim()),
                    phone,
                    Role.ROLE_ADMIN
            );
            admin.setMustChangePassword(false);
            userRepository.save(admin);
            logger.info("Secure initial administrator account provisioned from environment credentials for email: {}", email);
            return;
        }

        // Case 2: Development / Test mode (demo seeding enabled)
        if (shouldSeedDemoUsers()) {
            User admin = new User(
                    name,
                    email,
                    passwordEncoder.encode("admin123"),
                    phone,
                    Role.ROLE_ADMIN
            );
            admin.setMustChangePassword(false);
            userRepository.save(admin);
            logger.info("Development demo administrator created for email: {} (Default demo credentials active).", email);
            return;
        }

        // Case 3: Production startup without ADMIN_PASSWORD -> Generate secure one-time setup temporary password
        String generatedPassword = generateSecureOneTimePassword();
        User admin = new User(
                name,
                email,
                passwordEncoder.encode(generatedPassword),
                phone,
                Role.ROLE_ADMIN
        );
        admin.setMustChangePassword(true);
        admin.setPasswordResetBy("SYSTEM_INITIAL_PROVISIONING");
        admin.setPasswordResetAt(java.time.LocalDateTime.now());
        userRepository.save(admin);

        System.out.println("\n" +
                "================================================================================\n" +
                "  [BREW HAVEN CAFE] PRODUCTION INITIAL ADMIN PROVISIONING NOTICE\n" +
                "  No administrator existed and ADMIN_PASSWORD environment variable was not set.\n" +
                "  A secure one-time temporary password has been generated for initial login:\n\n" +
                "      Admin Email:    " + email + "\n" +
                "      Temporary Pass: " + generatedPassword + "\n\n" +
                "  NOTE: You MUST change this temporary password immediately upon first login.\n" +
                "================================================================================\n");

        logger.warn("Initial administrator account created with a secure one-time temporary password for email: {}. Immediate password change is enforced.", email);
    }

    private void seedDemoStaffAndCustomer() {
        if (!userRepository.existsByEmail("staff@brewhaven.com")) {
            User staff = new User(
                    "Barista Staff",
                    "staff@brewhaven.com",
                    passwordEncoder.encode("staff123"),
                    "9876543211",
                    Role.ROLE_STAFF
            );
            userRepository.save(staff);
            logger.info("Development demo staff user seeded: staff@brewhaven.com");
        }

        if (!userRepository.existsByEmail("customer@brewhaven.com")) {
            User customer = new User(
                    "Aarav Sharma",
                    "customer@brewhaven.com",
                    passwordEncoder.encode("customer123"),
                    "9876543212",
                    Role.ROLE_CUSTOMER
            );
            userRepository.save(customer);
            logger.info("Development demo customer user seeded: customer@brewhaven.com");
        }
    }

    private String generateSecureOneTimePassword() {
        final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String lower = "abcdefghijklmnopqrstuvwxyz";
        final String digits = "0123456789";
        final String special = "!@#$%^&*()-_=+";
        final String all = upper + lower + digits + special;

        SecureRandom random = new SecureRandom();
        StringBuilder pwd = new StringBuilder();

        pwd.append(upper.charAt(random.nextInt(upper.length())));
        pwd.append(lower.charAt(random.nextInt(lower.length())));
        pwd.append(digits.charAt(random.nextInt(digits.length())));
        pwd.append(special.charAt(random.nextInt(special.length())));

        for (int i = 4; i < 16; i++) {
            pwd.append(all.charAt(random.nextInt(all.length())));
        }

        char[] chars = pwd.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    private void seedCategoriesAndMenuItems() {
        logger.info("Synchronizing multi-cuisine cafe categories and starter menu...");

        // 1. Pizza
        Category pizza = getOrCreateCategory(
                "Pizza", null,
                "Stone-baked hand-stretched artisan pizzas topped with fresh mozzarella and herbs",
                "https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=600&q=80",
                1
        );

        // 2. Pasta
        Category pasta = getOrCreateCategory(
                "Pasta", null,
                "Italian al dente penne and fusilli cooked in creamy, tangy, and pink sauces",
                "https://images.unsplash.com/photo-1621996346565-e3d5d6281728?auto=format&fit=crop&w=600&q=80",
                2
        );

        // 3. Burgers
        Category burgers = getOrCreateCategory(
                "Burgers", null,
                "Gourmet grilled patties layered in toasted brioche with melted cheese and crisp veggies",
                "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=600&q=80",
                3
        );

        // 4. Sandwiches
        Category sandwiches = getOrCreateCategory(
                "Sandwiches", "Gourmet Sandwiches & Savories",
                "Artisan paninis, Bombay club sandwiches, and sourdough melts pressed golden",
                "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?auto=format&fit=crop&w=600&q=80",
                4
        );

        // 5. Snacks & Starters
        Category snacks = getOrCreateCategory(
                "Snacks & Starters", "Fresh Bakery & Pastries",
                "Crispy fries, loaded nachos, cheesy garlic breads, and quick savory bites",
                "https://images.unsplash.com/photo-1576107232684-1279f3908594?auto=format&fit=crop&w=600&q=80",
                5
        );

        // 6. Coffee
        Category coffee = getOrCreateCategory(
                "Coffee", "Coffee Specials",
                "Single-origin espresso roasts, iced frappes, flat whites, and cold brews",
                "https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&w=600&q=80",
                6
        );

        // 7. Tea
        Category tea = getOrCreateCategory(
                "Tea", "Artisanal Teas",
                "Aromatic masala chais, whole-leaf organic green teas, and chilled botanical infusions",
                "https://images.unsplash.com/photo-1544787219-7f47ccb76574?auto=format&fit=crop&w=600&q=80",
                7
        );

        // 8. Cold Beverages
        Category coldBeverages = getOrCreateCategory(
                "Cold Beverages", null,
                "Sparkling virgin mojitos, chilled lemonades, iced fruit coolers, and refreshers",
                "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80",
                8
        );

        // 9. Milkshakes
        Category milkshakes = getOrCreateCategory(
                "Milkshakes", null,
                "Decadent thick shakes spun with Belgian chocolate, Oreos, mangoes, and dairy ice cream",
                "https://images.unsplash.com/photo-1572490122747-3968b75cc699?auto=format&fit=crop&w=600&q=80",
                9
        );

        // 10. Desserts
        Category desserts = getOrCreateCategory(
                "Desserts", "Sweet Treats & Desserts",
                "Warm fudgy brownies, classic New York cheesecakes, and sweet culinary indulgences",
                "https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80",
                10
        );

        // --- 1. PIZZA ITEMS ---
        seedItem("Margherita Pizza",
                "Crisp thin crust layered with slow-cooked San Marzano tomato sauce, fresh mozzarella cheese, and fragrant basil",
                new BigDecimal("279.00"), pizza, true, true,
                "https://images.unsplash.com/photo-1604382355076-af4b0eb60143?auto=format&fit=crop&w=600&q=80",
                15, "Mild");

        seedItem("Farmhouse Pizza",
                "Loaded with crunchy green bell peppers, golden sweet corn, fresh mushrooms, red onions, and melted mozzarella",
                new BigDecimal("339.00"), pizza, true, true,
                "https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=600&q=80",
                15, "Mild");

        seedItem("Paneer Tikka Pizza",
                "Tandoori-spiced cottage cheese cubes, charred capsicum, pickled red onions, and mozzarella on a herbed tomato base",
                new BigDecimal("359.00"), pizza, true, true,
                "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=600&q=80",
                15, "Medium");

        seedItem("BBQ Veggie Delight Pizza",
                "Smoky barbecue sauce base, roasted button mushrooms, caramelized onions, jalapeños, and smoked cheddar blend",
                new BigDecimal("349.00"), pizza, true, true,
                "https://images.unsplash.com/photo-1534308983496-4fabb1a015ee?auto=format&fit=crop&w=600&q=80",
                15, "Medium");

        // --- 2. PASTA ITEMS ---
        seedItem("White Sauce Pasta",
                "Italian penne cooked al dente in a velvety garlic Parmesan cream sauce with tender broccoli and sautéed mushrooms",
                new BigDecimal("269.00"), pasta, true, true,
                "https://images.unsplash.com/photo-1645112411341-6c4fd023714a?auto=format&fit=crop&w=600&q=80",
                12, "Mild");

        seedItem("Red Sauce Pasta",
                "Classic penne tossed in fiery, tangy tomato Arrabbiata sauce with black olives, sweet peppers, and fresh parsley",
                new BigDecimal("249.00"), pasta, true, true,
                "https://images.unsplash.com/photo-1621996346565-e3d5d6281728?auto=format&fit=crop&w=600&q=80",
                12, "Medium");

        seedItem("Pink Sauce Pasta",
                "Fusilli combining the zest of rich tomato concasse with silky Alfredo cream sauce and melted mozzarella",
                new BigDecimal("279.00"), pasta, true, true,
                "https://images.unsplash.com/photo-1551183053-bf91a1d81141?auto=format&fit=crop&w=600&q=80",
                12, "Mild");

        // --- 3. BURGERS ---
        seedItem("Veg Burger",
                "Crispy spiced herb potato patty, fresh romaine lettuce, juicy tomato slices, gherkins, and thousand island spread in a toasted bun",
                new BigDecimal("159.00"), burgers, true, true,
                "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=600&q=80",
                10, "Mild");

        seedItem("Cheese Burger",
                "Grilled vegetable supreme patty layered with double English cheddar cheese, caramelized onions, and smoky chipotle aioli",
                new BigDecimal("199.00"), burgers, true, true,
                "https://images.unsplash.com/photo-1586190848861-99aa4a171e90?auto=format&fit=crop&w=600&q=80",
                10, "Mild");

        seedItem("Paneer Crunch Burger",
                "Panko-crusted crispy paneer steak, tangy coleslaw, pickled jalapeños, and house burger relish in a butter-glazed brioche",
                new BigDecimal("229.00"), burgers, true, true,
                "https://images.unsplash.com/photo-1550547660-d9450f859349?auto=format&fit=crop&w=600&q=80",
                12, "Medium");

        // --- 4. SANDWICHES ---
        seedItem("Grilled Sandwich",
                "Tri-layer Bombay style toasted sandwich filled with sliced potatoes, cucumber, tomatoes, mint chutney, and molten cheese",
                new BigDecimal("169.00"), sandwiches, true, true,
                "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?auto=format&fit=crop&w=600&q=80",
                8, "Medium");

        seedItem("Pesto Paneer Sourdough Panini",
                "Marinated cottage cheese, basil walnut pesto, sundried tomatoes, and fresh mozzarella pressed crisp on artisanal sourdough",
                new BigDecimal("259.00"), sandwiches, true, true,
                "https://images.unsplash.com/photo-1509722747041-616f39b57569?auto=format&fit=crop&w=600&q=80",
                10, "Medium");

        seedItem("Spinach Corn & Mozzarella Toastie",
                "Golden grilled bread stuffed with creamy sauteed spinach, sweet American corn kernels, and stretchy mozzarella",
                new BigDecimal("189.00"), sandwiches, true, true,
                "https://images.unsplash.com/photo-1553909489-cd47e0907980?auto=format&fit=crop&w=600&q=80",
                8, "Mild");

        // --- 5. SNACKS & STARTERS ---
        seedItem("Garlic Bread",
                "Toasted French baguette slices drenched in roasted garlic herb butter and crowned with bubbling mozzarella cheese",
                new BigDecimal("169.00"), snacks, true, true,
                "https://images.unsplash.com/photo-1619535860434-ba1d8fa12536?auto=format&fit=crop&w=600&q=80",
                8, "Mild");

        seedItem("French Fries",
                "Crispy golden skin-on potato fries dusted with aromatic peri-peri seasoning, served with creamy garlic mayo dip",
                new BigDecimal("139.00"), snacks, true, true,
                "https://images.unsplash.com/photo-1576107232684-1279f3908594?auto=format&fit=crop&w=600&q=80",
                6, "Medium");

        seedItem("Loaded Mexican Nachos",
                "Crisp stone-ground corn tortilla chips drizzled with warm queso cheese, fresh salsa fresca, jalapeños, and sour cream",
                new BigDecimal("219.00"), snacks, true, true,
                "https://images.unsplash.com/photo-1513456852971-30c0b8199d4d?auto=format&fit=crop&w=600&q=80",
                8, "Medium");

        // --- 6. COFFEE ---
        seedItem("Artisanal Cappuccino",
                "Double shot of single-origin espresso crowned with thick velvety microfoam and rich Dutch cocoa dusting",
                new BigDecimal("179.00"), coffee, true, true,
                "https://images.unsplash.com/photo-1534778101976-62847782c213?auto=format&fit=crop&w=600&q=80",
                5, "Mild");

        seedItem("Cold Coffee",
                "Classic blended espresso with chilled whole milk, rich vanilla syrup, and chocolate drizzle, served thick and frothy",
                new BigDecimal("169.00"), coffee, true, true,
                "https://images.unsplash.com/photo-1517701550927-30cf4ba1dba5?auto=format&fit=crop&w=600&q=80",
                4, "Mild");

        seedItem("Caramel Macchiato",
                "Freshly steamed milk marked with bold espresso shots, infused with vanilla, and finished with salted caramel drizzle",
                new BigDecimal("219.00"), coffee, true, true,
                "https://images.unsplash.com/photo-1485808191679-5f86510681a2?auto=format&fit=crop&w=600&q=80",
                5, "Mild");

        seedItem("Classic Double Espresso",
                "Intense, concentrated shot extracted from our signature medium-dark roasted Arabica beans",
                new BigDecimal("139.00"), coffee, true, true,
                "https://images.unsplash.com/photo-1510591509098-f4fdc6d0ff04?auto=format&fit=crop&w=600&q=80",
                3, "Mild");

        // --- 7. TEA ---
        seedItem("Masala Chai Supreme",
                "Strong Assam CTC simmered with fragrant green cardamom, cloves, cinnamon, fresh crushed ginger, and creamy milk",
                new BigDecimal("109.00"), tea, true, true,
                "https://images.unsplash.com/photo-1561336313-0bd5e0b27ec8?auto=format&fit=crop&w=600&q=80",
                5, "Mild");

        seedItem("Royal Earl Grey",
                "Pure black tea perfumed with natural cold-pressed Italian bergamot oil and blue cornflower petals",
                new BigDecimal("149.00"), tea, true, true,
                "https://images.unsplash.com/photo-1597481499750-3e6b22637e12?auto=format&fit=crop&w=600&q=80",
                4, "Mild");

        seedItem("Iced Hibiscus Green Tea",
                "Whole leaf green tea cold-brewed with ruby tart hibiscus petals, mint sprigs, and lemon over ice",
                new BigDecimal("159.00"), tea, true, true,
                "https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=600&q=80",
                4, "Mild");

        // --- 8. COLD BEVERAGES ---
        seedItem("Classic Virgin Mojito",
                "Muddled fresh garden mint leaves, zesty Persian lime wedges, pure cane sugar syrup, and crisp sparkling soda",
                new BigDecimal("149.00"), coldBeverages, true, true,
                "https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=600&q=80",
                4, "Mild");

        seedItem("Blue Curacao Lagoon",
                "Vibrant citrus mocktail with blue curacao, freshly squeezed lemon juice, mint, and fizzy lemon spritz",
                new BigDecimal("169.00"), coldBeverages, true, true,
                "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=600&q=80",
                4, "Mild");

        seedItem("Iced Lemon Peach Cooler",
                "Sun-ripened peach nectar shaken with chilled iced tea, lemon slices, and crushed mint over crystal ice",
                new BigDecimal("169.00"), coldBeverages, true, true,
                "https://images.unsplash.com/photo-1556679343-c7306c1976bc?auto=format&fit=crop&w=600&q=80",
                4, "Mild");

        // --- 9. MILKSHAKES ---
        seedItem("Belgian Dark Chocolate Shake",
                "Ultra-thick milkshake blended with gourmet 70% dark Belgian ganache, cocoa, and rich chocolate ice cream",
                new BigDecimal("209.00"), milkshakes, true, true,
                "https://images.unsplash.com/photo-1572490122747-3968b75cc699?auto=format&fit=crop&w=600&q=80",
                5, "Mild");

        seedItem("Oreo Hazelnut Crunch Shake",
                "Crushed chocolate Oreo cookies spun with roasted hazelnut cream, vanilla gelato, and chocolate drizzle",
                new BigDecimal("229.00"), milkshakes, true, true,
                "https://images.unsplash.com/photo-1579954115545-a95591f28bfc?auto=format&fit=crop&w=600&q=80",
                5, "Mild");

        seedItem("Alphonso Mango Shake",
                "Rich luscious Alphonso mango pulp blended with whole milk and vanilla bean ice cream, topped with mango chunks",
                new BigDecimal("199.00"), milkshakes, true, true,
                "https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=80",
                5, "Mild");

        // --- 10. DESSERTS ---
        seedItem("Warm Belgian Chocolate Brownie",
                "Dense, fudgy dark chocolate walnut brownie served warm with chocolate fudge drizzle and vanilla bean gelato",
                new BigDecimal("199.00"), desserts, true, true,
                "https://images.unsplash.com/photo-1606313564200-e75d5e30476c?auto=format&fit=crop&w=600&q=80",
                6, "Mild");

        seedItem("New York Baked Cheesecake",
                "Traditional velvety cream cheese cake on a spiced buttery graham cracker crust with sweet raspberry coulis",
                new BigDecimal("249.00"), desserts, true, true,
                "https://images.unsplash.com/photo-1533134242443-d4fd215305ad?auto=format&fit=crop&w=600&q=80",
                4, "Mild");

        logger.info("Multi-cuisine categories and menu synchronized successfully.");
    }

    private Category getOrCreateCategory(String name, String oldLegacyName, String description, String imageUrl, int displayOrder) {
        Category category = null;
        Optional<Category> opt = categoryRepository.findByName(name);
        if (opt.isPresent()) {
            category = opt.get();
        } else if (oldLegacyName != null) {
            Optional<Category> legacyOpt = categoryRepository.findByName(oldLegacyName);
            if (legacyOpt.isPresent()) {
                category = legacyOpt.get();
                category.setName(name);
                logger.info("Migrated legacy category '{}' to '{}'", oldLegacyName, name);
            }
        }

        if (category == null) {
            category = new Category();
            category.setName(name);
        }

        category.setDescription(description);
        category.setImageUrl(imageUrl);
        category.setDisplayOrder(displayOrder);
        category = categoryRepository.saveAndFlush(category);

        // If duplicate legacy category exists separately from target, reassign its items
        if (oldLegacyName != null) {
            Optional<Category> duplicateLegacy = categoryRepository.findByName(oldLegacyName);
            if (duplicateLegacy.isPresent() && !duplicateLegacy.get().getId().equals(category.getId())) {
                Category legacyCat = duplicateLegacy.get();
                List<MenuItem> items = menuItemRepository.findByCategoryId(legacyCat.getId());
                for (MenuItem item : items) {
                    item.setCategory(category);
                }
                menuItemRepository.saveAllAndFlush(items);
                try {
                    categoryRepository.delete(legacyCat);
                    categoryRepository.flush();
                } catch (Exception e) {
                    logger.warn("Could not delete legacy category: {}", oldLegacyName);
                }
            }
        }

        return category;
    }

    private void seedItem(String name, String description, BigDecimal price, Category category,
                           boolean isVegetarian, boolean isAvailable, String imageUrl,
                           int prepMinutes, String spicyLevel) {
        Optional<MenuItem> existing = menuItemRepository.findByName(name);
        if (existing.isPresent()) {
            MenuItem item = existing.get();
            item.setDescription(description);
            item.setPrice(price);
            item.setCategory(category);
            item.setIsVegetarian(isVegetarian);
            item.setIsAvailable(isAvailable);
            item.setImageUrl(imageUrl);
            item.setPreparationTimeMinutes(prepMinutes);
            item.setSpicyLevel(spicyLevel);
            menuItemRepository.save(item);
        } else {
            MenuItem item = new MenuItem(name, description, price, category, isVegetarian, isAvailable, imageUrl, prepMinutes, spicyLevel);
            menuItemRepository.save(item);
        }
    }

    private void seedTables() {
        if (cafeTableRepository.count() == 0) {
            logger.info("Seeding cafe tables 1 to 10...");
            for (int i = 1; i <= 10; i++) {
                int capacity = (i <= 4) ? 2 : ((i <= 8) ? 4 : 6);
                cafeTableRepository.save(new CafeTable(i, capacity));
            }
            logger.info("Tables 1 to 10 seeded.");
        }
    }
}
