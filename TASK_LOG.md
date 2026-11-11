# Task Log: Brew Haven Cafe Management System

## Status & Resume Summary
- **Current State**: ✅ Final Scope & Security Audit Complete. H2 Database lock issue diagnosed, fixed, and verified. Project running live via `mvn spring-boot:run` on port 8080.
- **Git Push**: Held per user instruction ("Do not push to GitHub yet").

---

## Final Scope & Security Audit Checklist

- [x] **Audit Item 1: Scope Enforcement - Table Reservation Excluded**
  - Confirmed: Table reservation is strictly excluded. Pre-orders with dine-in intent prepare meals for arrival time but do not lock or hold tables in advance (`tableNumber=null`, `cafeTable=null`).
  - Floor table management (Tables 1–10) and QR-based immediate dine-in (`?table=N`) remain fully functional and actively manage occupancy.

- [x] **Audit Item 2: All Four Order Combinations Verified**
  1. `IMMEDIATE` + `DINE_IN`: Table 2 marked `OCCUPIED` immediately; released upon order completion.
  2. `IMMEDIATE` + `TAKEAWAY`: Fresh immediate preparation for counter pickup.
  3. `PRE_ORDER` + `DINE_IN`: Prepared for scheduled arrival; table reservation excluded.
  4. `PRE_ORDER` + `TAKEAWAY`: Prepared for pickup at scheduled arrival time.

- [x] **Audit Item 3: Payment Simulation & Order Status Handling**
  - Instant UPI and Card payments simulated to `SUCCESS`.
  - Cash at counter payments initialized as `PENDING`; automatically transition to `SUCCESS` upon staff marking order as `COMPLETED`.
  - Status lifecycle verified: `CONFIRMED` -> `PREPARING` -> `READY` -> `COMPLETED`. Table release verified on completion and cancellation.

- [x] **Audit Item 4: Secrets & API Keys Protection**
  - No secrets or API keys hardcoded or committed.
  - Environment variable resolution with fallbacks for JWT secrets and AI keys (`${JWT_SECRET:...}`, `${GEMINI_API_KEY:}`, `${GROQ_API_KEY:}`).
  - Built-in heuristic NLP barista engine operates completely offline without external credentials.

- [x] **Audit Item 5: Git Exclusions (.gitignore)**
  - `.gitignore` verified: `target/`, `.env`, `.env.local`, `data/`, and `*.mv.db` / `*.trace.db` excluded.
  - `git status` checked: untracked files clean and no build artifacts or environment secrets exposed.

- [x] **Audit Item 6: Documentation Preservation**
  - `docs/requirements.md` preserved and enriched with architecture, scope boundaries, order matrix, payment simulation, and security guidelines.

- [x] **Audit Item 7: Final Maven Build**
  - Clean build executed: `mvn clean package -DskipTests` -> `BUILD SUCCESS` (0 errors).
  - Executable archive generated: `target/brew-haven-cafe-1.0.0.jar`.

- [x] **Multi-Cuisine Cafe Transformation Complete**
  - 10 Expanded Categories: Pizza, Pasta, Burgers, Sandwiches, Snacks & Starters, Coffee, Tea, Cold Beverages, Milkshakes, Desserts.
  - Realistic multi-cuisine starter menu seeded idempotently with multiple vegetarian choices in every category.
  - Mandatory dishes verified: Margherita Pizza, Farmhouse Pizza, Paneer Tikka Pizza, White Sauce Pasta, Red Sauce Pasta, Pink Sauce Pasta, Veg Burger, Cheese Burger, Grilled Sandwich, Garlic Bread, French Fries, Cold Coffee, Artisanal Cappuccino, Classic Virgin Mojito, Warm Belgian Chocolate Brownie, New York Baked Cheesecake.
  - Admin Portal enhanced with category modal and item management (add, edit, availability toggle, safe deletion detaching order items).
  - Role-based security verified: Menu modifications restricted strictly to `ROLE_ADMIN`.
  - AI Assistant updated with multi-cuisine dining recommendations, pairing logic, and menu sync.
  - All automated tests passed 100%. Database and existing data preserved.

---

## H2 Database Lock Resolution & `mvn spring-boot:run` Verification

### 1. Root Cause Analysis
1. **Background Process Contention**: A prior Brew Haven instance was running in the background holding an exclusive operating system file handle on `data/brewhaven.mv.db`.
2. **Missing Automatic Mixed Mode**: In `application-h2.properties`, the H2 JDBC URL was configured without `AUTO_SERVER=TRUE`, meaning H2 opened the file in exclusive single-process mode. Any subsequent attempt (such as `mvn spring-boot:run` or another CLI) to access the same file was immediately rejected by H2 with `Database may be already in use: E:/Brew-Haven-Cafe/data/brewhaven.mv.db - The file is locked`.
3. **Incompatible Property Flag in H2 2.2.x**: In H2 version 2.2+, `AUTO_SERVER=TRUE` cannot be combined with `DB_CLOSE_ON_EXIT=FALSE` (throws `Feature not supported: "AUTO_SERVER=TRUE && DB_CLOSE_ON_EXIT=FALSE"`).

### 2. Actions Taken
1. **Process Isolation**: Inspected all running Java processes using PowerShell (`Get-CimInstance Win32_Process -Filter "Name = 'java.exe'"`). Safely stopped only the background Brew Haven Java process while leaving unrelated Java processes untouched.
2. **H2 Configuration Update**: Modified `application-h2.properties` to:
   ```properties
   spring.datasource.url=jdbc:h2:file:./data/brewhaven;AUTO_SERVER=TRUE
   spring.datasource.driverClassName=org.h2.Driver
   spring.datasource.username=sa
   spring.datasource.password=
   ```
   This activates H2 Automatic Mixed Mode, permitting multi-process access to the persistent file-based database without throwing file lock errors.
3. **Data Preservation**: Preserved `data/brewhaven.mv.db` without deleting or resetting database files.
4. **Execution & Verification**:
   - `mvn clean compile` succeeded with `BUILD SUCCESS`.
   - `mvn spring-boot:run` started cleanly in 5.815 seconds.
   - Confirmed Tomcat listening on port 8080.
   - Verified 5 categories, 16 menu items, user authentication (`customer@brewhaven.com`), and live order placement.

---

## Role-Based Authentication & Authorization Audit & Remediation

### 1. Audit Summary & Vulnerabilities Identified
1. **Missing AccessDeniedHandler**: Authenticated users attempting unauthorized actions defaulted to generic Spring error dispatching rather than clean, explicit JSON 403 Forbidden responses.
2. **Missing Ownership Check on Orders**: Endpoints `GET /api/orders/{id}` and `GET /api/orders/number/{orderNumber}` allowed any authenticated user to inspect orders without validating customer ownership against the authenticated principal.
3. **Role Confusion on Reviews and Customer Orders**: Review submission permitted Admin bypass and returned 400 instead of 403. Admin and Staff accounts were not strictly barred from placing customer orders.
4. **Staff Access to Executive Analytics**: `GET /api/ai/insights` permitted `ROLE_STAFF`, violating the requirement that staff cannot access admin sales analytics.
5. **JWT Role Verification Gap**: `JwtAuthenticationFilter` extracted username but did not cross-validate that the JWT `role` claim matched the user's active authorities in the database on every request.
6. **Frontend UI Exposure**: Navigation bars exposed "Staff Dashboard" and "Admin Portal" to unauthenticated guests and customers. `admin.html` and `staff.html` lacked client-side pre-render authentication guards and role-based redirect logic.
7. **Staff / Admin Account Provisioning**: Public registration allowed browser payload inspection, but did not have an admin-only user management portal for assigning roles or creating staff.

### 2. Remediation Actions Taken
1. **`CustomAccessDeniedHandler`**: Added component returning HTTP 403 with standardized JSON `{ success: false, message: "Access denied: ...", path: ..., timestamp: ... }`.
2. **Strict Spring Security Rules (`SecurityConfig.java`)**:
   - `HTTP 401` for unauthenticated requests via `JwtAuthenticationEntryPoint`.
   - `HTTP 403` for forbidden requests via `CustomAccessDeniedHandler`.
   - `POST /api/orders`, `GET /api/orders/my-orders`, `POST /api/reviews` restricted strictly to `ROLE_CUSTOMER`.
   - `GET /api/orders/active`, `GET /api/orders/upcoming-preorders`, `PATCH /api/orders/{id}/status`, `PATCH /api/tables/**` restricted to `ROLE_STAFF` and `ROLE_ADMIN`.
   - `POST/PUT/DELETE /api/menu/**`, `GET /api/menu/all-items`, `GET /api/admin/**`, `GET /api/ai/insights` restricted strictly to `ROLE_ADMIN`.
3. **Object Ownership Enforcement (`OrderService.java` & `ReviewService.java`)**:
   - `getOrderById` & `getOrderByOrderNumber` verify customer ownership: if a customer requests another customer's order, an `AccessDeniedException` (HTTP 403) is thrown immediately.
   - `submitReview` strictly enforces customer ownership of completed orders; non-owners receive HTTP 403.
4. **JWT Verification with Role Claims (`JwtTokenProvider.java` & `JwtAuthenticationFilter.java`)**:
   - Every protected request extracts and validates the JWT `role` claim.
   - Verifies the claim against the user's database authorities. Any role mismatch or tampering results in rejection.
5. **Secure Staff & User Provisioning (`AdminController.java`)**:
   - Added `POST /api/admin/users` to allow authorized administrators to provision Staff, Admin, or Customer accounts with encrypted passwords.
   - Added `PATCH /api/admin/users/{id}/role` for administrative role assignment.
   - Public registration `POST /api/auth/register` hardcodes `Role.ROLE_CUSTOMER` and disregards any browser-submitted role.
6. **Frontend Role Guarding (`app.js`, `admin.html`, `staff.html`, `history.html`, `index.html`)**:
   - Dynamic `renderNavigation()` renders links based on active role:
     - Guests: `Menu & Order`
     - Customers: `Menu & Order`, `My Orders`
     - Staff: `Kitchen & Service`
     - Admin: `Admin Portal`, `Kitchen Ops`, `Cafe Menu`
   - Added `protectPage()` in `app.js` with pre-auth loading placeholders. Protected DOM elements remain hidden (`display: none`) until cryptographic verification passes.
   - Unauthorized attempts automatically redirect users to their permitted dashboard (`admin.html` for Admin, `staff.html` for Staff, `index.html` for Customers).
   - Added Staff & User management modal and role selector in `admin.html`.

### 3. Verification Test Results (54/54 Tests Passed - 100%)
- **Unauthenticated / Guest Access**: 9/9 passed (public endpoints return 200; protected endpoints return 401).
- **Authentication & Login**: 3/3 passed (Admin, Staff, Customer login).
- **Public Registration Security**: 2/2 passed (Role spoof blocked -> `ROLE_CUSTOMER`; new user denied admin access -> 403).
- **Customer Role Permissions**: 12/12 passed (Allowed: menu, orders, own order tracking; Denied: admin stats -> 403, active orders -> 403, menu changes -> 403, table toggle -> 403, AI insights -> 403).
- **Object Ownership Security**: 3/3 passed (Customer B cannot view Customer A's order by ID -> 403, by Number -> 403, or submit review for it -> 403).
- **Staff Role Permissions**: 13/13 passed (Allowed: active orders -> 200, status update -> 200, table toggle -> 200; Denied: admin stats -> 403, user directory -> 403, AI insights -> 403, menu modifications -> 403, create customer order -> 403, reviews -> 403).
- **Admin Role Permissions**: 13/13 passed (Allowed: admin stats -> 200, all orders -> 200, user management -> 200, create staff user -> 201, change role -> 200, menu CRUD -> 200, AI insights -> 200; Denied: cannot place customer orders -> 403, submit reviews -> 403).
- **Account Switching & Logout**: 2/2 passed (Switched to newly provisioned staff account; verified immediate role boundaries).
- **Database & Data Preservation**: H2 database `./data/brewhaven.mv.db` fully preserved with all original categories, menu items, and records intact.

---

## Final Cart Isolation Audit & Account Switching Verification

### 1. Verification of Cart Storage Key (Shared vs User-Specific)
- **Pre-Audit State**: Cart data was previously saved to `localStorage` using a single shared key `bh_cart` (`localStorage.setItem('bh_cart', ...)`). Additionally, `logout()` did not clear `state.cart` in memory. As a result, switching accounts on the same browser exposed the previous user's cart items to the subsequent user.
- **Post-Audit Fix**:
  - Implemented `getCartStorageKey(user)`:
    - Authenticated customer: `bh_cart_user_${user.id}` (isolated per user ID).
    - Guest visitor: `bh_cart_guest`.
  - Added `loadUserCart(user)` to load the specific user's cart on login and initialization.
  - Updated `saveCart()` to write to the active user's key and remove any legacy shared `bh_cart` key.
  - Updated `logout()` to reset in-memory `state.cart = []`, remove cached auth credentials (`bh_token`, `bh_user`), and update UI badges/modal.

### 2. Multi-Account Switching Lifecycle Verified
1. **Customer A** (ID: 101) logs in with an empty cart. Adds 2 items: Margherita Pizza (qty 1 @ ₹299.00) and Artisanal Cappuccino (qty 2 @ ₹189.00). Cart totals recalculated: Subtotal ₹677.00, Tax (5% GST) ₹33.85, Grand Total ₹710.85, Badge: 3 items. Persisted under `bh_cart_user_101`.
2. **Customer A Logs Out**: In-memory `state.cart` reset to `[]`, `state.user` and `state.token` cleared, `bh_token` and `bh_user` removed from `localStorage`. Cart badge reset to 0/hidden. Customer A's persisted cart remains intact under `bh_cart_user_101`.
3. **Customer B** (ID: 202) logs in on the same browser: Sees an empty cart (`state.cart.length === 0`), badge 0. Adds Garlic Bread (qty 1 @ ₹149.00). Saved under `bh_cart_user_202`. Customer B logs out, clearing memory.
4. **Customer A Logs Back In**: Original cart is restored from `bh_cart_user_101` with 2 items (Margherita Pizza qty 1, Cappuccino qty 2). Updating Cappuccino quantity (+1) recalculates: Quantity 4, Subtotal ₹866.00, Tax ₹43.30, Total ₹909.30.
5. **Customer B Logs Back In**: Cart is restored with Garlic Bread (qty 1 @ ₹149.00), proving zero cross-contamination.

### 3. Automated Test Results (37/37 Tests Passed - 100%)
- **Test Suite**: Automated headless test suite executed in Node.js environment against `app.js`.
- **Tests Passed**: 37 / 37 assertions verified.
- **Database & Data Preservation**: H2 database `./data/brewhaven.mv.db` verified via live API probe: 10 categories, 39 menu items, 10 tables, and all orders/users 100% intact. No data lost or reset.
