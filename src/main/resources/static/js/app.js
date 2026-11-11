// Brew Haven Cafe Management System - Core Client JS

const API_BASE = '/api';

// State
const savedUser = localStorage.getItem('bh_user');
const initialUser = savedUser ? JSON.parse(savedUser) : null;
const initialCartKey = (initialUser && initialUser.id) ? `bh_cart_user_${initialUser.id}` : 'bh_cart_guest';

const state = {
    user: initialUser,
    token: localStorage.getItem('bh_token') || null,
    cart: JSON.parse(localStorage.getItem(initialCartKey) || '[]'),
    categories: [],
    menuItems: [],
    tables: [],
    selectedCategory: null,
    vegOnly: false,
    searchQuery: '',
    orderTiming: 'IMMEDIATE',
    fulfilmentType: 'DINE_IN',
    selectedTable: null,
    paymentMethod: 'UPI'
};

// Cart isolation storage utilities
function getCartStorageKey(user) {
    if (user && user.id) {
        return `bh_cart_user_${user.id}`;
    }
    return 'bh_cart_guest';
}

function loadUserCart(user) {
    const key = getCartStorageKey(user);
    try {
        state.cart = JSON.parse(localStorage.getItem(key) || '[]');
    } catch (e) {
        console.error('Failed to parse cart for key', key, e);
        state.cart = [];
    }
    updateCartBadge();
    renderCartModal();
}

function saveCart() {
    const key = getCartStorageKey(state.user);
    localStorage.setItem(key, JSON.stringify(state.cart));
    localStorage.removeItem('bh_cart'); // Clean up legacy shared key
    updateCartBadge();
}

// Utilities
function showToast(message, type = 'info') {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = 'toast';
    const icon = type === 'success' ? '✅' : (type === 'error' ? '❌' : 'ℹ️');
    toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

function getAuthHeaders() {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) {
        headers['Authorization'] = `Bearer ${state.token}`;
    }
    return headers;
}

// Navigation rendering based on authenticated role
function renderNavigation() {
    const navLists = document.querySelectorAll('.nav-links');
    if (!navLists || navLists.length === 0) return;

    const path = window.location.pathname;
    const isIndex = path.endsWith('/') || path.endsWith('index.html');
    const isHistory = path.endsWith('history.html');
    const isStaff = path.endsWith('staff.html');
    const isAdmin = path.endsWith('admin.html');

    const role = state.user?.role;

    let items = [];
    if (!role) {
        // Guest
        items.push({ href: 'index.html', label: 'Menu & Order', active: isIndex });
    } else if (role === 'ROLE_CUSTOMER') {
        items.push({ href: 'index.html', label: 'Menu & Order', active: isIndex });
        items.push({ href: 'history.html', label: 'My Orders', active: isHistory });
    } else if (role === 'ROLE_STAFF') {
        items.push({ href: 'staff.html', label: 'Kitchen & Service', active: isStaff });
    } else if (role === 'ROLE_ADMIN') {
        items.push({ href: 'admin.html', label: 'Admin Portal', active: isAdmin });
        items.push({ href: 'staff.html', label: 'Kitchen Ops', active: isStaff });
        items.push({ href: 'index.html', label: 'Cafe Menu', active: isIndex });
    }

    const html = items.map(item => `<li><a href="${item.href}" class="${item.active ? 'active' : ''}">${item.label}</a></li>`).join('');
    navLists.forEach(ul => {
        ul.innerHTML = html;
    });
}

function getPermittedDashboard(role) {
    if (role === 'ROLE_ADMIN') return 'admin.html';
    if (role === 'ROLE_STAFF') return 'staff.html';
    return 'index.html';
}

async function protectPage(allowedRoles) {
    const roles = Array.isArray(allowedRoles) ? allowedRoles : [allowedRoles];
    const token = localStorage.getItem('bh_token');

    if (!token) {
        showToast('Authentication required. Redirecting to home...', 'error');
        window.location.replace('index.html');
        return false;
    }

    try {
        const res = await fetch(`${API_BASE}/auth/me`, {
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json'
            }
        });

        if (!res.ok) {
            localStorage.removeItem('bh_token');
            localStorage.removeItem('bh_user');
            state.token = null;
            state.user = null;
            showToast('Session expired. Please log in again.', 'error');
            window.location.replace('index.html');
            return false;
        }

        const user = await res.json();
        state.user = user;
        state.token = token;
        localStorage.setItem('bh_user', JSON.stringify(user));
        updateAuthUI(user);
        renderNavigation();

        if (user.mustChangePassword) {
            window.location.replace('change-password.html');
            return false;
        }

        if (!roles.includes(user.role)) {
            showToast(`Access denied: ${user.role.replace('ROLE_', '')} cannot access this page.`, 'error');
            const target = getPermittedDashboard(user.role);
            window.location.replace(target);
            return false;
        }

        // Reveal protected content
        const loadingEl = document.getElementById('authLoading');
        if (loadingEl) loadingEl.style.display = 'none';

        const protectedEl = document.getElementById('protectedContent');
        if (protectedEl) protectedEl.style.display = 'block';

        return true;
    } catch (e) {
        console.error('Auth verification error:', e);
        window.location.replace('index.html');
        return false;
    }
}

// Auth functions
async function checkAuth() {
    renderNavigation();
    if (!state.token) {
        updateAuthUI(null);
        renderNavigation();
        state.cart = [];
        updateCartBadge();
        return;
    }
    try {
        const res = await fetch(`${API_BASE}/auth/me`, { headers: getAuthHeaders() });
        if (res.ok) {
            state.user = await res.json();
            localStorage.setItem('bh_user', JSON.stringify(state.user));
            if (state.user && state.user.mustChangePassword && !window.location.pathname.includes('change-password.html')) {
                window.location.replace('change-password.html');
                return;
            }
            loadUserCart(state.user);
            updateAuthUI(state.user);
            renderNavigation();
        } else {
            logout();
        }
    } catch (e) {
        console.error('Auth verification failed', e);
    }
}

function updateAuthUI(user) {
    const authContainer = document.getElementById('navAuth');
    if (!authContainer) return;

    if (user) {
        authContainer.innerHTML = `
            <div class="auth-badge">
                <span>👤 <strong>${user.name}</strong> (${user.role.replace('ROLE_', '')})</span>
                <button class="btn-outline" style="padding:0.2rem 0.6rem; font-size:0.8rem;" onclick="logout()">Logout</button>
            </div>
        `;
    } else {
        authContainer.innerHTML = `
            <button class="btn-outline" onclick="openLoginModal()">Login</button>
            <button class="btn-primary" onclick="openRegisterModal()">Register</button>
        `;
    }
}

async function loginUser(identifier, password) {
    try {
        const res = await fetch(`${API_BASE}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: identifier, password: password })
        });
        const data = await res.json();
        clearPasswordFields();
        if (res.ok) {
            state.token = data.token;
            localStorage.setItem('bh_token', data.token);
            state.user = { id: data.id, name: data.name, email: data.email, role: data.role, mustChangePassword: !!data.mustChangePassword };
            localStorage.setItem('bh_user', JSON.stringify(state.user));

            if (data.mustChangePassword) {
                closeModal('authModal');
                window.location.href = 'change-password.html';
                return;
            }

            loadUserCart(state.user);
            updateAuthUI(state.user);
            renderNavigation();
            showToast(`Welcome back, ${data.name}!`, 'success');
            closeModal('authModal');

            // Redirect to appropriate dashboard based on role
            if (data.role === 'ROLE_ADMIN') {
                if (!window.location.pathname.includes('admin.html')) {
                    window.location.href = 'admin.html';
                }
            } else if (data.role === 'ROLE_STAFF') {
                if (!window.location.pathname.includes('staff.html')) {
                    window.location.href = 'staff.html';
                }
            } else {
                if (window.location.pathname.includes('login.html') || window.location.pathname.includes('admin.html') || window.location.pathname.includes('staff.html')) {
                    window.location.href = 'index.html';
                }
            }
        } else {
            clearPasswordFields();
            showToast(data.message || 'Login failed. Please check credentials.', 'error');
        }
    } catch (e) {
        clearPasswordFields();
        showToast('Login error: ' + e.message, 'error');
    }
}

async function registerUser(name, phone, password, email) {
    try {
        const payload = {
            name: name,
            phone: phone,
            password: password
        };
        if (email && email.trim() !== '') {
            payload.email = email.trim();
        }

        const res = await fetch(`${API_BASE}/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        clearPasswordFields();
        if (res.ok) {
            state.token = data.token;
            localStorage.setItem('bh_token', data.token);
            state.user = { id: data.id, name: data.name, email: data.email, role: data.role, mustChangePassword: !!data.mustChangePassword };
            localStorage.setItem('bh_user', JSON.stringify(state.user));

            if (data.mustChangePassword) {
                closeModal('authModal');
                window.location.href = 'change-password.html';
                return;
            }

            loadUserCart(state.user);
            updateAuthUI(state.user);
            renderNavigation();
            showToast('Registration successful! Welcome to Brew Haven.', 'success');
            closeModal('authModal');
            if (window.location.pathname.includes('login.html')) {
                window.location.href = 'index.html';
            }
        } else {
            clearPasswordFields();
            showToast(data.message || 'Registration failed', 'error');
        }
    } catch (e) {
        clearPasswordFields();
        showToast('Registration error: ' + e.message, 'error');
    }
}

function logout() {
    clearPasswordFields();
    state.token = null;
    state.user = null;
    state.cart = [];
    localStorage.removeItem('bh_token');
    localStorage.removeItem('bh_user');
    localStorage.removeItem('bh_cart');
    updateAuthUI(null);
    renderNavigation();
    updateCartBadge();
    renderCartModal();
    clearPasswordFields();
    showToast('Logged out successfully.');
    const p = window.location.pathname;
    if (p.includes('admin.html') || p.includes('staff.html') || p.includes('history.html')) {
        window.location.replace('index.html');
    }
}

// Menu loading & filters
async function loadCategories() {
    try {
        const res = await fetch(`${API_BASE}/menu/categories`);
        if (res.ok) {
            state.categories = await res.json();
            renderCategories();
        }
    } catch (e) {
        console.error('Failed to load categories', e);
    }
}

function renderCategories() {
    const container = document.getElementById('categoryPills');
    if (!container) return;

    let html = `<button class="cat-pill ${state.selectedCategory === null ? 'active' : ''}" onclick="selectCategory(null)">All Delights</button>`;
    state.categories.forEach(cat => {
        html += `<button class="cat-pill ${state.selectedCategory === cat.id ? 'active' : ''}" onclick="selectCategory(${cat.id})">${cat.name}</button>`;
    });
    container.innerHTML = html;
}

function selectCategory(catId) {
    state.selectedCategory = catId;
    renderCategories();
    loadMenuItems();
}

async function loadMenuItems() {
    try {
        let url = `${API_BASE}/menu/items?`;
        const params = [];
        if (state.selectedCategory) params.push(`categoryId=${state.selectedCategory}`);
        if (state.vegOnly) params.push(`isVegetarian=true`);
        if (state.searchQuery) params.push(`query=${encodeURIComponent(state.searchQuery)}`);

        url += params.join('&');
        const res = await fetch(url);
        if (res.ok) {
            state.menuItems = await res.json();
            renderMenuItems();
        }
    } catch (e) {
        console.error('Failed to load menu items', e);
    }
}

function renderMenuItems() {
    const grid = document.getElementById('menuGrid');
    if (!grid) return;

    if (state.menuItems.length === 0) {
        grid.innerHTML = `<div style="grid-column: 1/-1; text-align:center; padding:3rem; color:var(--text-muted);">
            <h3>No delights match your selection</h3>
            <p>Try searching for something else or clearing filters.</p>
        </div>`;
        return;
    }

    grid.innerHTML = state.menuItems.map(item => `
        <div class="menu-card">
            <img class="menu-card-img" src="${item.imageUrl || 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=600&q=80'}" alt="${item.name}" loading="lazy" onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=600&q=80';" />
            <div class="menu-card-body">
                <div class="card-header-line">
                    <span class="item-title">${item.name}</span>
                    <span class="item-price">₹${item.price.toFixed(2)}</span>
                </div>
                <div class="item-badges">
                    <span class="badge ${item.isVegetarian ? 'badge-veg' : 'badge-nonveg'}">
                        ${item.isVegetarian ? '🟢 Veg' : '🔴 Non-Veg'}
                    </span>
                    <span class="badge badge-prep">⏱️ ${item.preparationTimeMinutes || 10} mins</span>
                    ${item.spicyLevel ? `<span class="badge" style="background:#fce4ec; color:#c2185b;">🌶️ ${item.spicyLevel}</span>` : ''}
                </div>
                <p class="item-desc">${item.description || ''}</p>
                <div class="card-actions">
                    <span style="font-size:0.8rem; color:var(--text-muted);">Handcrafted fresh</span>
                    <button class="btn-add-cart" onclick="addToCart(${item.id})">
                        <span>+ Add to Tray</span>
                    </button>
                </div>
            </div>
        </div>
    `).join('');
}

// Cart functions
function addToCart(itemId, qty = 1) {
    if (state.user && state.user.role !== 'ROLE_CUSTOMER') {
        showToast('Only customer accounts can add items to cart.', 'error');
        return;
    }
    const item = state.menuItems.find(i => i.id === itemId);
    if (!item) return;

    const existing = state.cart.find(c => c.itemId === itemId);
    if (existing) {
        existing.quantity += qty;
    } else {
        state.cart.push({
            itemId: item.id,
            name: item.name,
            price: item.price,
            quantity: qty
        });
    }

    saveCart();
    showToast(`Added '${item.name}' to cart!`, 'success');
}

function updateCartQty(itemId, delta) {
    const existing = state.cart.find(c => c.itemId === itemId);
    if (!existing) return;

    existing.quantity += delta;
    if (existing.quantity <= 0) {
        state.cart = state.cart.filter(c => c.itemId !== itemId);
    }
    saveCart();
    renderCartModal();
}

function updateCartBadge() {
    const count = state.cart.reduce((sum, item) => sum + item.quantity, 0);
    const badge = document.getElementById('cartBadge');
    if (badge) {
        badge.innerText = count;
        badge.style.display = count > 0 ? 'inline-block' : 'none';
    }
}

async function loadTables() {
    try {
        const res = await fetch(`${API_BASE}/tables`);
        if (res.ok) {
            state.tables = await res.json();
            renderTableSelect();
        }
    } catch (e) {
        console.error('Failed to load tables', e);
    }
}

function renderTableSelect() {
    const select = document.getElementById('tableSelect');
    if (!select) return;

    select.innerHTML = `<option value="">Select Cafe Table</option>` +
        state.tables.map(t => `
            <option value="${t.tableNumber}" ${t.status !== 'AVAILABLE' ? 'disabled' : ''}>
                Table ${t.tableNumber} (${t.capacity} seats) - [${t.status}]
            </option>
        `).join('');
}

function openCartModal() {
    renderCartModal();
    const modal = document.getElementById('cartModal');
    if (modal) modal.classList.add('active');
}

function renderCartModal() {
    const container = document.getElementById('cartItemsList');
    if (!container) return;

    if (state.cart.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:2rem; color:var(--text-muted);">
            <p>Your tray is currently empty.</p>
            <p>Explore our menu and add your favorite brews!</p>
        </div>`;
        document.getElementById('cartSubtotal').innerText = '₹0.00';
        document.getElementById('cartTax').innerText = '₹0.00';
        document.getElementById('cartTotal').innerText = '₹0.00';
        return;
    }

    container.innerHTML = state.cart.map(item => `
        <div class="cart-item">
            <div>
                <div class="cart-item-name">${item.name}</div>
                <div class="cart-item-price">₹${item.price.toFixed(2)} each</div>
            </div>
            <div class="qty-control">
                <button class="btn-qty" onclick="updateCartQty(${item.itemId}, -1)">-</button>
                <span style="font-weight:700; min-width:20px; text-align:center;">${item.quantity}</span>
                <button class="btn-qty" onclick="updateCartQty(${item.itemId}, 1)">+</button>
                <span style="font-weight:700; margin-left:0.8rem; min-width:65px; text-align:right;">₹${(item.price * item.quantity).toFixed(2)}</span>
            </div>
        </div>
    `).join('');

    const subtotal = state.cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    const tax = subtotal * 0.05;
    const total = subtotal + tax;

    document.getElementById('cartSubtotal').innerText = `₹${subtotal.toFixed(2)}`;
    document.getElementById('cartTax').innerText = `₹${tax.toFixed(2)}`;
    document.getElementById('cartTotal').innerText = `₹${total.toFixed(2)}`;
}

async function placeOrder() {
    if (!state.user) {
        showToast('Please login to place your order.', 'error');
        openLoginModal();
        return;
    }

    if (state.user.role !== 'ROLE_CUSTOMER') {
        showToast('Only customer accounts can place dining orders.', 'error');
        return;
    }

    if (state.cart.length === 0) {
        showToast('Cart is empty!', 'error');
        return;
    }

    const timing = state.orderTiming;
    const fulfilment = state.fulfilmentType;
    let tableNum = null;
    let expectedTime = null;

    if (fulfilment === 'DINE_IN') {
        const select = document.getElementById('tableSelect');
        if (select && select.value) {
            tableNum = parseInt(select.value);
        }
    }

    if (timing === 'PRE_ORDER') {
        const timeInput = document.getElementById('preOrderTime');
        if (!timeInput || !timeInput.value) {
            showToast('Please specify your expected arrival time for pre-order', 'error');
            return;
        }
        expectedTime = new Date(timeInput.value).toISOString().slice(0, 19);
    }

    const payload = {
        items: state.cart.map(c => ({ menuItemId: c.itemId, quantity: c.quantity })),
        orderTiming: timing,
        fulfilmentType: fulfilment,
        tableNumber: tableNum,
        expectedArrivalTime: expectedTime,
        paymentMethod: state.paymentMethod,
        notes: document.getElementById('orderNotes')?.value || ''
    };

    try {
        const res = await fetch(`${API_BASE}/orders`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify(payload)
        });

        const data = await res.json();
        if (res.ok) {
            showToast(`Order #${data.orderNumber} placed successfully!`, 'success');
            state.cart = [];
            saveCart();
            closeModal('cartModal');
            showOrderSuccessModal(data);
        } else {
            showToast(data.message || 'Failed to place order', 'error');
        }
    } catch (e) {
        showToast('Order error: ' + e.message, 'error');
    }
}

function showOrderSuccessModal(order) {
    const modal = document.getElementById('orderReceiptModal');
    if (!modal) return;

    document.getElementById('receiptOrderNo').innerText = order.orderNumber;
    document.getElementById('receiptStatus').innerText = order.status;
    document.getElementById('receiptTotal').innerText = `₹${order.totalAmount.toFixed(2)}`;
    document.getElementById('receiptTiming').innerText = order.orderTiming;
    document.getElementById('receiptType').innerText = order.fulfilmentType + (order.tableNumber ? ` (Table ${order.tableNumber})` : '');
    document.getElementById('receiptPayment').innerText = `${order.paymentMethod} (${order.paymentStatus})`;

    const itemsList = document.getElementById('receiptItems');
    if (itemsList && order.items) {
        itemsList.innerHTML = order.items.map(i => `
            <div style="display:flex; justify-content:space-between; margin-bottom:0.3rem;">
                <span>${i.itemName} x ${i.quantity}</span>
                <span>₹${i.subtotal.toFixed(2)}</span>
            </div>
        `).join('');
    }

    modal.classList.add('active');
}

// AI Assistant
function toggleAIDrawer() {
    const drawer = document.getElementById('aiDrawer');
    if (drawer) {
        drawer.classList.toggle('active');
        if (drawer.classList.contains('active')) {
            document.getElementById('aiChatInput')?.focus();
        }
    }
}

async function sendAIMessage(presetText = null) {
    const input = document.getElementById('aiChatInput');
    const msg = presetText || (input ? input.value.trim() : '');
    if (!msg) return;

    if (input) input.value = '';

    const messagesContainer = document.getElementById('aiMessages');
    if (messagesContainer) {
        messagesContainer.innerHTML += `
            <div class="ai-msg user">${msg}</div>
        `;
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    try {
        const cartItemNames = state.cart.map(c => c.name);
        const res = await fetch(`${API_BASE}/ai/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                message: msg,
                currentCartItems: cartItemNames,
                dietaryPreference: state.vegOnly ? 'veg' : 'all'
            })
        });

        const data = await res.json();
        if (res.ok && messagesContainer) {
            let suggestionsHtml = '';
            if (data.suggestedItems && data.suggestedItems.length > 0) {
                suggestionsHtml = `
                    <div style="margin-top:0.6rem; border-top:1px solid #eedecf; padding-top:0.5rem;">
                        <small style="font-weight:700; color:var(--primary);">Recommended to try:</small>
                        <div style="display:flex; flex-direction:column; gap:0.4rem; margin-top:0.3rem;">
                            ${data.suggestedItems.map(item => `
                                <div style="display:flex; justify-content:space-between; align-items:center; background:#faf5ed; padding:0.3rem 0.5rem; border-radius:4px;">
                                    <span style="font-size:0.82rem;">${item.name} (₹${item.price})</span>
                                    <button class="btn-primary" style="padding:0.15rem 0.5rem; font-size:0.75rem;" onclick="addToCart(${item.id})">+ Add</button>
                                </div>
                            `).join('')}
                        </div>
                    </div>
                `;
            }

            let pairingsHtml = '';
            if (data.pairingSuggestions && data.pairingSuggestions.length > 0) {
                pairingsHtml = `
                    <div style="margin-top:0.4rem; font-size:0.8rem; color:#6a1b9a;">
                        💡 <em>${data.pairingSuggestions[0]}</em>
                    </div>
                `;
            }

            messagesContainer.innerHTML += `
                <div class="ai-msg bot">
                    <div>${data.reply.replace(/\n/g, '<br/>')}</div>
                    ${pairingsHtml}
                    ${suggestionsHtml}
                    <div style="font-size:0.68rem; color:#888; margin-top:0.4rem; text-align:right;">
                        Powered by ${data.modelUsed}
                    </div>
                </div>
            `;
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }
    } catch (e) {
        console.error('AI chat failed', e);
    }
}

// Modal helper
function openModal(id) {
    const m = document.getElementById(id);
    if (m) m.classList.add('active');
}

function closeModal(id) {
    const m = document.getElementById(id);
    if (m) m.classList.remove('active');
}

function clearPasswordFields() {
    const pwdEl = document.getElementById('loginPassword');
    if (pwdEl) pwdEl.value = '';
    const regPwdEl = document.getElementById('regPassword');
    if (regPwdEl) regPwdEl.value = '';
    const newPwdEl = document.getElementById('newUserPassword');
    if (newPwdEl) newPwdEl.value = '';
}

let currentLoginRole = 'CUSTOMER';

function openLoginModal(role = 'CUSTOMER') {
    if (role === true) role = 'STAFF';
    if (role === false || !role) role = 'CUSTOMER';
    currentLoginRole = role;

    const body = document.getElementById('authModalBody');
    if (!body) return;

    let roleTitle = 'Customer Sign In';
    let roleDesc = 'Sign in with your registered Full Name / Username or Mobile Number.';
    let identifierLabel = 'Registered Name / Mobile Number';
    let identifierPlaceholder = 'e.g. Aarav Sharma or 9876543212';
    let identifierType = 'text';
    let demoHtml = `
        <div style="text-align:center; margin-top:0.8rem; font-size:0.85rem; color:var(--text-muted);">
            Demo Customer: <a href="#" onclick="fillLoginIdentifier('Aarav Sharma')">Aarav Sharma</a> | <a href="#" onclick="fillLoginIdentifier('9876543212')">9876543212</a>
        </div>
    `;

    if (role === 'STAFF') {
        roleTitle = 'Staff & Barista Sign In';
        roleDesc = 'Authorized cafe personnel portal. Sign in with your registered email.';
        identifierLabel = 'Staff Email Address';
        identifierPlaceholder = 'e.g. staff@brewhaven.com';
        identifierType = 'email';
        demoHtml = `
            <div style="text-align:center; margin-top:0.8rem; font-size:0.85rem; color:var(--text-muted);">
                Demo Staff: <a href="#" onclick="fillLoginIdentifier('staff@brewhaven.com')">staff@brewhaven.com</a>
            </div>
        `;
    } else if (role === 'ADMIN') {
        roleTitle = 'Admin Portal Sign In';
        roleDesc = 'Administrative management portal. Sign in with your registered email.';
        identifierLabel = 'Admin Email Address';
        identifierPlaceholder = 'e.g. admin@brewhaven.com';
        identifierType = 'email';
        demoHtml = `
            <div style="text-align:center; margin-top:0.8rem; font-size:0.85rem; color:var(--text-muted);">
                Demo Admin: <a href="#" onclick="fillLoginIdentifier('admin@brewhaven.com')">admin@brewhaven.com</a>
            </div>
        `;
    }

    body.innerHTML = `
        <div class="role-login-tabs">
            <button type="button" class="role-login-btn ${role === 'CUSTOMER' ? 'active' : ''}" onclick="switchLoginRole('CUSTOMER')">👤 Customer</button>
            <button type="button" class="role-login-btn ${role === 'STAFF' ? 'active' : ''}" onclick="switchLoginRole('STAFF')">☕ Staff</button>
            <button type="button" class="role-login-btn ${role === 'ADMIN' ? 'active' : ''}" onclick="switchLoginRole('ADMIN')">🛡️ Admin</button>
        </div>

        <h3 style="margin-bottom:0.4rem; color:var(--primary-dark);">${roleTitle}</h3>
        <p style="font-size:0.85rem; color:var(--text-muted); margin-bottom:1rem;">${roleDesc}</p>

        <form onsubmit="event.preventDefault(); handleLoginSubmit();" autocomplete="on">
            <div class="form-group">
                <label for="loginIdentifier">${identifierLabel}</label>
                <input type="${identifierType}" id="loginIdentifier" placeholder="${identifierPlaceholder}" autocomplete="username" />
            </div>
            <div class="form-group">
                <label for="loginPassword">Password</label>
                <input type="password" id="loginPassword" placeholder="Enter your password" autocomplete="current-password" />
            </div>
            <button type="submit" class="btn-primary" style="width:100%; justify-content:center; padding:0.7rem;">Sign In</button>
        </form>

        ${demoHtml}

        ${role === 'CUSTOMER' ? `
            <div style="text-align:center; margin-top:0.8rem; font-size:0.88rem;">
                Don't have an account? <a href="#" onclick="openRegisterModal()" style="color:var(--primary); font-weight:700;">Register here</a>
            </div>
        ` : ''}
    `;

    openModal('authModal');
    clearPasswordFields();
}

function switchLoginRole(newRole) {
    clearPasswordFields();
    openLoginModal(newRole);
    clearPasswordFields();
}

function fillLoginIdentifier(identifier) {
    const idEl = document.getElementById('loginIdentifier');
    if (idEl) idEl.value = identifier;
    clearPasswordFields();
}

function openRegisterModal() {
    const body = document.getElementById('authModalBody');
    if (!body) return;
    body.innerHTML = `
        <h3 style="margin-bottom:0.5rem; color:var(--primary-dark);">Create Your Brew Haven Account</h3>
        <p style="font-size:0.85rem; color:var(--text-muted); margin-bottom:1rem;">
            Enjoy quick table ordering, personalized cart isolation, and dining rewards.
        </p>
        <form onsubmit="event.preventDefault(); handleRegisterSubmit();" autocomplete="on">
            <div class="form-group">
                <label for="regName">Full Name *</label>
                <input type="text" id="regName" placeholder="e.g. Priya Patel" autocomplete="name" />
                <small style="color:var(--text-muted); font-size:0.78rem;">Must be unique; used as your account login name.</small>
            </div>
            <div class="form-group">
                <label for="regPhone">Mobile Number * (10 Digits)</label>
                <input type="tel" id="regPhone" placeholder="e.g. 9876543210" maxlength="10" autocomplete="tel" />
                <small style="color:var(--text-muted); font-size:0.78rem;">Used for order updates and login authentication.</small>
            </div>
            <div class="form-group">
                <label for="regPassword">Password *</label>
                <input type="password" id="regPassword" placeholder="Minimum 6 characters" autocomplete="new-password" />
            </div>
            <div class="form-group">
                <label for="regEmail">Email Address (Optional)</label>
                <input type="email" id="regEmail" placeholder="e.g. priya@example.com (optional)" autocomplete="email" />
            </div>
            <button type="submit" class="btn-primary" style="width:100%; justify-content:center; padding:0.7rem; margin-top:0.5rem;">Create Customer Account</button>
        </form>
        <div style="text-align:center; margin-top:0.8rem; font-size:0.88rem;">
            Already have an account? <a href="#" onclick="openLoginModal('CUSTOMER')" style="color:var(--primary); font-weight:700;">Sign in</a>
        </div>
    `;
    openModal('authModal');
    clearPasswordFields();
}

function handleLoginSubmit() {
    const identifier = document.getElementById('loginIdentifier')?.value.trim();
    const pwd = document.getElementById('loginPassword')?.value;
    if (!identifier || !pwd) {
        clearPasswordFields();
        showToast('Please enter your login identifier and password', 'error');
        return;
    }
    loginUser(identifier, pwd);
}

function handleRegisterSubmit() {
    const name = document.getElementById('regName')?.value.trim();
    const phone = document.getElementById('regPhone')?.value.trim();
    const pwd = document.getElementById('regPassword')?.value;
    const email = document.getElementById('regEmail')?.value.trim();

    if (!name || name.length < 2) {
        showToast('Please enter your full name (minimum 2 characters)', 'error');
        return;
    }
    if (!phone || !/^\d{10}$/.test(phone)) {
        showToast('Please enter a valid 10-digit mobile number', 'error');
        return;
    }
    if (!pwd || pwd.length < 6) {
        showToast('Password must be at least 6 characters', 'error');
        return;
    }
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showToast('Please enter a valid email address format or leave it blank', 'error');
        return;
    }

    registerUser(name, phone, pwd, email);
}

// Order History / My Orders
async function loadMyOrders() {
    const container = document.getElementById('myOrdersList');
    if (!container) return;

    if (!state.token) {
        container.innerHTML = `<div style="text-align:center; padding:2rem;">Please <a href="#" onclick="openLoginModal()">login</a> to view your past orders.</div>`;
        return;
    }

    if (state.user && state.user.role !== 'ROLE_CUSTOMER') {
        container.innerHTML = `<div style="text-align:center; padding:2rem; color:var(--text-muted);">
            Customer orders history is reserved for customer dining accounts.
        </div>`;
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/orders/my-orders`, { headers: getAuthHeaders() });
        if (res.ok) {
            const orders = await res.json();
            if (orders.length === 0) {
                container.innerHTML = `<p style="text-align:center; padding:2rem; color:var(--text-muted);">No orders found yet. Time for some coffee!</p>`;
                return;
            }

            container.innerHTML = orders.map(o => `
                <div style="background:var(--bg-surface); border:1px solid var(--border-color); border-radius:var(--radius-sm); padding:1.2rem; margin-bottom:1rem; box-shadow:var(--shadow-sm);">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.5rem;">
                        <strong>Order #${o.orderNumber}</strong>
                        <span class="status-badge status-${o.status}">${o.status}</span>
                    </div>
                    <div style="font-size:0.88rem; color:var(--text-muted); margin-bottom:0.6rem;">
                        <span>📅 ${new Date(o.createdAt).toLocaleString()}</span> •
                        <span>🏷️ ${o.fulfilmentType} ${o.tableNumber ? `(Table ${o.tableNumber})` : ''}</span> •
                        <span>💳 ${o.paymentMethod} (₹${o.totalAmount.toFixed(2)})</span>
                    </div>
                    <div style="font-size:0.9rem; margin-bottom:0.8rem; background:var(--bg-cream); padding:0.6rem; border-radius:4px;">
                        ${o.items.map(i => `${i.itemName} x ${i.quantity}`).join(', ')}
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="font-size:0.85rem; color:var(--text-muted);">Ref: ${o.transactionReference || 'N/A'}</span>
                        ${o.status === 'COMPLETED' && !o.hasReview ? `
                            <button class="btn-outline" style="font-size:0.82rem; padding:0.3rem 0.7rem;" onclick="openReviewModal(${o.id})">⭐ Review Experience</button>
                        ` : (o.hasReview ? `<span style="font-size:0.82rem; color:var(--success);">⭐ Reviewed</span>` : '')}
                    </div>
                </div>
            `).join('');
        }
    } catch (e) {
        console.error('Error loading my orders', e);
    }
}

function openReviewModal(orderId) {
    document.getElementById('reviewOrderId').value = orderId;
    openModal('reviewModal');
}

async function submitReview() {
    if (!state.user || state.user.role !== 'ROLE_CUSTOMER') {
        showToast('Only customer accounts can submit reviews.', 'error');
        return;
    }

    const orderId = document.getElementById('reviewOrderId').value;
    const rating = document.getElementById('reviewRating').value;
    const comment = document.getElementById('reviewComment').value.trim();

    try {
        const res = await fetch(`${API_BASE}/reviews`, {
            method: 'POST',
            headers: getAuthHeaders(),
            body: JSON.stringify({ orderId: parseInt(orderId), rating: parseInt(rating), comment })
        });
        const data = await res.json();
        if (res.ok) {
            showToast('Thank you for your valuable feedback!', 'success');
            closeModal('reviewModal');
            loadMyOrders();
        } else {
            showToast(data.message || 'Failed to submit review', 'error');
        }
    } catch (e) {
        showToast('Error: ' + e.message, 'error');
    }
}

// Global page initialization
document.addEventListener('DOMContentLoaded', () => {
    renderNavigation();
    checkAuth();
    loadCategories();
    loadMenuItems();
    loadTables();
    updateCartBadge();

    // Event listeners
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            state.searchQuery = e.target.value;
            loadMenuItems();
        });
    }

    const vegCheckbox = document.getElementById('vegFilter');
    if (vegCheckbox) {
        vegCheckbox.addEventListener('change', (e) => {
            state.vegOnly = e.target.checked;
            loadMenuItems();
        });
    }

    const timingBtns = document.querySelectorAll('.order-timing-btn');
    timingBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            timingBtns.forEach(b => b.classList.remove('active'));
            e.target.classList.add('active');
            state.orderTiming = e.target.dataset.timing;
            const preOrderGroup = document.getElementById('preOrderGroup');
            if (preOrderGroup) {
                preOrderGroup.style.display = state.orderTiming === 'PRE_ORDER' ? 'block' : 'none';
            }
            updateTableVisibility();
        });
    });

    const fulfilmentBtns = document.querySelectorAll('.fulfilment-btn');
    fulfilmentBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            fulfilmentBtns.forEach(b => b.classList.remove('active'));
            e.target.classList.add('active');
            state.fulfilmentType = e.target.dataset.type;
            updateTableVisibility();
        });
    });

    function updateTableVisibility() {
        const tableGroup = document.getElementById('tableSelectGroup');
        const preOrderNotice = document.getElementById('preOrderDineInNotice');
        if (state.fulfilmentType === 'DINE_IN') {
            if (state.orderTiming === 'IMMEDIATE') {
                if (tableGroup) tableGroup.style.display = 'block';
                if (preOrderNotice) preOrderNotice.style.display = 'none';
            } else {
                // Table reservation excluded for pre-orders
                if (tableGroup) tableGroup.style.display = 'none';
                if (preOrderNotice) preOrderNotice.style.display = 'block';
            }
        } else {
            if (tableGroup) tableGroup.style.display = 'none';
            if (preOrderNotice) preOrderNotice.style.display = 'none';
        }
    }

    // QR-based immediate dine-in detection: e.g. /index.html?table=3
    const urlParams = new URLSearchParams(window.location.search);
    const qrTableParam = urlParams.get('table');
    if (qrTableParam) {
        const tableNum = parseInt(qrTableParam);
        if (!isNaN(tableNum)) {
            state.selectedTable = tableNum;
            state.fulfilmentType = 'DINE_IN';
            state.orderTiming = 'IMMEDIATE';
            showToast(`📲 QR Code Detected: Seated at Table #${tableNum} for Immediate Dine-In!`, 'success');
            // Auto-select in UI
            setTimeout(() => {
                const select = document.getElementById('tableSelect');
                if (select) select.value = tableNum;
                const qrBadge = document.getElementById('qrTableBadge');
                if (qrBadge) {
                    qrBadge.innerText = `📲 Seated at Table #${tableNum} (QR Detected)`;
                    qrBadge.style.display = 'inline-block';
                }
            }, 600);
        }
    }

    const paymentSelect = document.getElementById('paymentMethodSelect');
    if (paymentSelect) {
        paymentSelect.addEventListener('change', (e) => {
            state.paymentMethod = e.target.value;
        });
    }
});
