# Brew Haven Cafe Management System - Comprehensive Specifications & Audit Report

## 1. Project Overview & Scope
Brew Haven Cafe is an AI-enhanced cafe management and dining system built on Spring Boot 3.3.5 and Java 23. It provides a seamless dining experience for customers, an operational kitchen/barista workflow for staff, and real-time revenue analytics for cafe administrators.

### Scope Boundaries & Clarifications
- **Included**:
  - Full Cafe Menu catalog with categories, vegetarian flags, spice indicators, and live pricing.
  - Interactive Customer Ordering with cart tray and 5% GST tax calculation.
  - **All 4 Order Combinations**:
    1. `IMMEDIATE` + `DINE_IN` (immediate seated dining with QR code table detection / selection)
    2. `IMMEDIATE` + `TAKEAWAY` (immediate counter pickup)
    3. `PRE_ORDER` + `DINE_IN` (scheduled ahead of arrival; food prepared for expected arrival time)
    4. `PRE_ORDER` + `TAKEAWAY` (scheduled ahead for pickup at expected arrival time)
  - **Table Management**: Real-time cafe floor plan (Tables 1–10), capacity tracking, occupancy auto-update, and staff manual toggle.
  - **Payment Simulation**: Instant UPI and Card payment simulation, and Counter Cash collection.
  - **AI Barista Assistant**: Beverage and pastry recommendations, conversational pairing advice, and AI executive sales insights.
  - **Customer Verified Feedback**: Post-dining rating and review submission.
- **Explicitly Excluded**:
  - **Table Reservation**: Advance table booking/reservation is strictly excluded from this system. Tables cannot be reserved or blocked for future hours. For pre-orders with dine-in intent, the food is prepared for the arrival time, and seating is taken on a first-come, first-served basis upon physical arrival.

---

## 2. System Architecture & Technologies
- **Backend Framework**: Spring Boot 3.3.5
- **Language**: Java 23 (with Java 17 bytecode compatibility)
- **Persistence**: Spring Data JPA / Hibernate with H2 Database (`./data/brewhaven`) and MySQL configuration profile.
- **Security**: Spring Security 6 with stateless HMAC-SHA256 JWT tokens (JJWT 0.12.5).
- **Frontend**: Responsive Single-Page Architecture served via Spring Boot static assets (`index.html`, `staff.html`, `admin.html`, `history.html`, `css/styles.css`, `js/app.js`).

---

## 3. Order Combinations & Status Lifecycle Matrix

| Combination | Timing | Mode | Table Assigned | Advance Table Hold? | Notes |
|-------------|--------|------|----------------|----------------------|-------|
| **1** | `IMMEDIATE` | `DINE_IN` | Yes (e.g. Table 2) | No (Seated Now) | Table marked `OCCUPIED` immediately; released on `COMPLETED` / `CANCELLED`. Supports QR code auto-selection (`?table=N`). |
| **2** | `IMMEDIATE` | `TAKEAWAY` | No | No | Fresh preparation for immediate counter takeaway. |
| **3** | `PRE_ORDER` | `DINE_IN` | No (Upon Arrival) | No | Arrival time required (>= 5 min in future). Food timed for arrival. Table reservation excluded. |
| **4** | `PRE_ORDER` | `TAKEAWAY` | No | No | Arrival time required. Food packed and ready for pickup at arrival timestamp. |

### Order Status Lifecycle
```
[Placed] -> CONFIRMED -> PREPARING -> READY -> COMPLETED (Releases Table / Finalizes Cash)
                     \-> CANCELLED (Releases Table)
```

---

## 4. Payment Simulation
- **UPI**: Instant simulated payment confirmation (`PaymentStatus.SUCCESS`), generates `TXN-...` reference.
- **CARD**: Instant simulated payment confirmation (`PaymentStatus.SUCCESS`).
- **CASH**: Marked as `PaymentStatus.PENDING` at order placement; automatically transitions to `PaymentStatus.SUCCESS` when staff marks the order as `COMPLETED` at the counter.

---

## 5. Security & Secret Protection
- **No Hardcoded API Keys or Secrets**:
  - Secrets and keys are read strictly through environment variables with fallbacks (`${JWT_SECRET:...}`, `${GEMINI_API_KEY:}`, `${GROQ_API_KEY:}`).
  - Built-in heuristic NLP engine ensures full functionality without requiring any external cloud API keys.
- **Git Exclusions (`.gitignore`)**:
  - `target/` directory excluded.
  - `.env` and `.env.local` excluded.
  - `data/` and local H2 database files (`*.mv.db`, `*.trace.db`) excluded.

---

## 6. Default Pre-Seeded Accounts
| Role | Email | Password | Access Rights |
|------|-------|----------|---------------|
| **Administrator** | `admin@brewhaven.com` | `admin123` | Executive analytics, menu catalog CRUD, order history, user directory |
| **Staff / Barista** | `staff@brewhaven.com` | `staff123` | Kitchen active order queue, order status transitions, floor tables tracker |
| **Customer** | `customer@brewhaven.com` | `customer123` | Menu ordering, cart tray, order tracking, review feedback, AI chat |

---

## 7. Web Endpoints & UI Portals
- **Customer Portal**: `http://localhost:8080/index.html` (supports QR scan e.g. `http://localhost:8080/?table=4`)
- **Kitchen & Staff Operations**: `http://localhost:8080/staff.html`
- **Admin Executive Portal**: `http://localhost:8080/admin.html`
- **Customer Order Tracking**: `http://localhost:8080/history.html`
- **H2 Console**: `http://localhost:8080/h2-console`

---

## 8. Multi-Cuisine Cafe Expansion
- **10 Core Categories**: Pizza, Pasta, Burgers, Sandwiches, Snacks & Starters, Coffee, Tea, Cold Beverages, Milkshakes, Desserts.
- **Starter Menu**: 39 items with multiple vegetarian choices in each category (Margherita Pizza, Farmhouse Pizza, Paneer Tikka Pizza, White Sauce Pasta, Red Sauce Pasta, Pink Sauce Pasta, Veg Burger, Cheese Burger, Grilled Sandwich, Garlic Bread, French Fries, Cold Coffee, Artisanal Cappuccino, Classic Virgin Mojito, Warm Belgian Chocolate Brownie, New York Baked Cheesecake, etc.).
- **Live Admin Menu Management**: Add, edit, toggle availability, and delete menu items with dynamic instant UI updates without restarting.
- **AI Dining Advisor**: Context-aware meal suggestions and beverage pairings across multi-cuisine dishes.
