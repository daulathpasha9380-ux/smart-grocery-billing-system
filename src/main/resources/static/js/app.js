/*
  GROCERY BILLING SYSTEM - APPLICATION JS SYSTEM (PHASE 0.5 DATA ENGINE)
  Complete Default Dataset, Reactive POS Engine, LocalStorage Persistence & Interactive Views
*/

// Expanded Rich Default Products Dataset (20+ Items)
const DEFAULT_PRODUCTS = [
  { id: 1, name: 'Amul Taaza Milk 1L', category: 'Dairy', brand: 'Amul', barcode: '8901262010012', purchasePrice: 28.00, price: 32.00, stock: 18, minStock: 10, expiry: '2026-09-09', status: 'In Stock' },
  { id: 2, name: 'Basmati Rice 5kg', category: 'Grocery', brand: 'Fortune', barcode: '8906010501123', purchasePrice: 380.00, price: 450.00, stock: 24, minStock: 5, expiry: '2027-06-30', status: 'In Stock' },
  { id: 3, name: 'Harvest Gold Wheat Bread', category: 'Bakery', brand: 'Harvest Gold', barcode: '8904022001045', purchasePrice: 36.00, price: 45.00, stock: 5, minStock: 8, expiry: '2026-09-08', status: 'Low Stock' },
  { id: 4, name: 'Shimla Fresh Apples 1kg', category: 'Produce', brand: 'Fresh Farm', barcode: '8900010002011', purchasePrice: 140.00, price: 180.00, stock: 12, minStock: 5, expiry: '2026-09-12', status: 'In Stock' },
  { id: 5, name: 'Tata Salt 1kg', category: 'Grocery', brand: 'Tata', barcode: '8901058000105', purchasePrice: 22.00, price: 28.00, stock: 45, minStock: 10, expiry: '2028-01-01', status: 'In Stock' },
  { id: 6, name: 'Coca Cola 750ml', category: 'Beverages', brand: 'Coca Cola', barcode: '8901764012214', purchasePrice: 31.00, price: 40.00, stock: 18, minStock: 6, expiry: '2027-03-15', status: 'In Stock' },
  { id: 7, name: 'Aashirvaad Whole Wheat Atta 10kg', category: 'Grocery', brand: 'ITC', barcode: '8901058852315', purchasePrice: 380.00, price: 440.00, stock: 15, minStock: 5, expiry: '2027-01-10', status: 'In Stock' },
  { id: 8, name: 'Amul Butter 100g', category: 'Dairy', brand: 'Amul', barcode: '8901262020059', purchasePrice: 48.00, price: 56.00, stock: 3, minStock: 8, expiry: '2026-09-14', status: 'Low Stock' },
  { id: 9, name: 'Mother Dairy Curd 400g', category: 'Dairy', brand: 'Mother Dairy', barcode: '8901262030010', purchasePrice: 28.00, price: 35.00, stock: 2, minStock: 6, expiry: '2026-09-08', status: 'Expiring Soon' },
  { id: 10, name: 'Fortune Sunlite Sunflower Oil 1L', category: 'Grocery', brand: 'Fortune', barcode: '8906010502211', purchasePrice: 125.00, price: 145.00, stock: 0, minStock: 10, expiry: '2027-08-01', status: 'Out of Stock' },
  { id: 11, name: 'Red Label Tea 500g', category: 'Beverages', brand: 'Brooke Bond', barcode: '8901030012541', purchasePrice: 240.00, price: 285.00, stock: 16, minStock: 4, expiry: '2027-11-20', status: 'In Stock' },
  { id: 12, name: 'Maggi 2-Min Noodles 4-Pack', category: 'Grocery', brand: 'Nestle', barcode: '8901058863214', purchasePrice: 48.00, price: 58.00, stock: 30, minStock: 10, expiry: '2027-04-10', status: 'In Stock' },
  { id: 13, name: 'Fresh Farm Tomatoes 1kg', category: 'Produce', brand: 'Local Farm', barcode: '8900010003321', purchasePrice: 25.00, price: 38.00, stock: 22, minStock: 8, expiry: '2026-09-10', status: 'In Stock' },
  { id: 14, name: 'Surf Excel Easy Wash 1kg', category: 'Household', brand: 'Unilever', barcode: '8901030745210', purchasePrice: 118.00, price: 140.00, stock: 14, minStock: 5, expiry: '2028-05-01', status: 'In Stock' },
  { id: 15, name: 'Dettol Original Soap 125g (3+1)', category: 'Personal Care', brand: 'Reckitt', barcode: '8901396001245', purchasePrice: 132.00, price: 160.00, stock: 11, minStock: 4, expiry: '2028-02-15', status: 'In Stock' },
  { id: 16, name: 'Parle-G Gold Biscuits 250g', category: 'Bakery', brand: 'Parle', barcode: '8901030800012', purchasePrice: 22.00, price: 30.00, stock: 40, minStock: 10, expiry: '2027-05-15', status: 'In Stock' },
  { id: 17, name: 'Everest Garam Masala 100g', category: 'Grocery', brand: 'Everest', barcode: '8901234500987', purchasePrice: 65.00, price: 78.00, stock: 25, minStock: 5, expiry: '2027-10-10', status: 'In Stock' },
  { id: 18, name: 'Kissan Tomato Ketchup 950g', category: 'Grocery', brand: 'Kissan', barcode: '8901030345671', purchasePrice: 110.00, price: 135.00, stock: 14, minStock: 4, expiry: '2027-07-20', status: 'In Stock' },
  { id: 19, name: 'Haldiram Bhujia Sev 400g', category: 'Bakery', brand: 'Haldiram', barcode: '8904012398712', purchasePrice: 95.00, price: 115.00, stock: 20, minStock: 6, expiry: '2027-03-30', status: 'In Stock' },
  { id: 20, name: 'Tropicana 100% Orange Juice 1L', category: 'Beverages', brand: 'Tropicana', barcode: '8901456987123', purchasePrice: 105.00, price: 130.00, stock: 8, minStock: 4, expiry: '2026-11-15', status: 'In Stock' },
  { id: 21, name: 'Cadbury Dairy Milk Silk 150g', category: 'Bakery', brand: 'Cadbury', barcode: '8901234999888', purchasePrice: 140.00, price: 175.00, stock: 16, minStock: 5, expiry: '2027-02-14', status: 'In Stock' }
];

// Expanded Default Customers Dataset
const DEFAULT_CUSTOMERS = [
  { id: 1, name: 'Rahul Sharma', phone: '+91 98765 43210', email: 'rahul.s@gmail.com', points: 340, credit: 0.00, lastVisit: 'Today, 10:42 AM' },
  { id: 2, name: 'Priya Patel', phone: '+91 98123 45678', email: 'priya.p@yahoo.com', points: 180, credit: 250.00, lastVisit: 'Yesterday, 04:15 PM' },
  { id: 3, name: 'Amit Verma', phone: '+91 97111 22334', email: 'amit.verma@outlook.com', points: 520, credit: 0.00, lastVisit: '05 Sep 2026' },
  { id: 4, name: 'Sunita Devi', phone: '+91 96543 21098', email: 'sunita.d@gmail.com', points: 95, credit: 120.00, lastVisit: '04 Sep 2026' },
  { id: 5, name: 'Vikram Singh', phone: '+91 99887 76655', email: 'vikram.singh@corp.com', points: 810, credit: 0.00, lastVisit: 'Today, 09:55 AM' },
  { id: 6, name: 'Anish Kapadia', phone: '+91 98222 33445', email: 'anish.k@gmail.com', points: 210, credit: 0.00, lastVisit: '03 Sep 2026' },
  { id: 7, name: 'Kavita Reddy', phone: '+91 97333 44556', email: 'kavita.reddy@yahoo.com', points: 430, credit: 50.00, lastVisit: '02 Sep 2026' },
  { id: 8, name: 'Deepak Joshi', phone: '+91 98444 55667', email: 'd.joshi@gmail.com', points: 150, credit: 0.00, lastVisit: '31 Aug 2026' }
];

const DEFAULT_SUPPLIERS = [
  { id: 1, company: 'Amul Dairy Distributors Ltd', contactPerson: 'Suresh Kumar', phone: '+91 98200 11223', email: 'orders@amuldist.com', category: 'Dairy & Refrigerated', pendingOrders: 2 },
  { id: 2, company: 'Fortune FMCG Wholesale', contactPerson: 'Ramesh Shah', phone: '+91 98333 44556', email: 'sales@fortunefmcg.in', category: 'Staples & Oils', pendingOrders: 1 },
  { id: 3, company: 'Fresh Produce Farmers Co-op', contactPerson: 'Harpreet Singh', phone: '+91 98111 88990', email: 'farmfresh@coop.org', category: 'Fruits & Vegetables', pendingOrders: 0 },
  { id: 4, company: 'Nestle & Britannia Supply Corp', contactPerson: 'Anish Kapadia', phone: '+91 97444 55667', email: 'supply@nestle-dist.com', category: 'Packaged Foods & Bakery', pendingOrders: 1 },
  { id: 5, company: 'Hindustan Unilever Agencies', contactPerson: 'Meena Saxena', phone: '+91 98999 00112', email: 'hul.wholesale@agencies.com', category: 'Household & Care', pendingOrders: 0 }
];

const DEFAULT_PURCHASES = [
  { id: 'PO-2026-089', supplier: 'Amul Dairy Distributors Ltd', date: '06 Sep 2026', itemsCount: 4, totalAmount: 4850.00, status: 'Received' },
  { id: 'PO-2026-088', supplier: 'Fortune FMCG Wholesale', date: '04 Sep 2026', itemsCount: 8, totalAmount: 14200.00, status: 'In Transit' },
  { id: 'PO-2026-087', supplier: 'Nestle & Britannia Supply Corp', date: '01 Sep 2026', itemsCount: 6, totalAmount: 6400.00, status: 'Received' },
  { id: 'PO-2026-086', supplier: 'Fresh Produce Farmers Co-op', date: '30 Aug 2026', itemsCount: 3, totalAmount: 3100.00, status: 'Received' }
];

const DEFAULT_SALES = [
  { invoice: 'INV-1048', date: 'Today, 10:42 AM', customer: 'Rahul Sharma', paymentMode: 'Cash', cashier: 'Cashier', items: 3, amount: 1240.00, status: 'Completed', phone: '+91 98765 43210' },
  { invoice: 'INV-1047', date: 'Today, 10:18 AM', customer: 'Walk-in Retail', paymentMode: 'UPI / PhonePe', cashier: 'Cashier', items: 2, amount: 860.00, status: 'Completed', phone: '+91 98123 45678' },
  { invoice: 'INV-1046', date: 'Today, 09:55 AM', customer: 'Vikram Singh', paymentMode: 'Card', cashier: 'Cashier', items: 5, amount: 2410.00, status: 'Completed', phone: '+91 99887 76655' },
  { invoice: 'INV-1045', date: 'Yesterday, 06:20 PM', customer: 'Priya Patel', paymentMode: 'Customer Credit', cashier: 'Owner', items: 4, amount: 950.00, status: 'Completed', phone: '+91 98123 45678' },
  { invoice: 'INV-1044', date: 'Yesterday, 04:15 PM', customer: 'Walk-in Retail', paymentMode: 'Cash', cashier: 'Cashier', items: 1, amount: 180.00, status: 'Completed', phone: '' },
  { invoice: 'INV-1043', date: 'Yesterday, 02:30 PM', customer: 'Amit Verma', paymentMode: 'UPI / PhonePe', cashier: 'Cashier', items: 6, amount: 3120.00, status: 'Completed', phone: '+91 97111 22334' }
];

const DEFAULT_EXPENSES = [
  { id: 1, date: 'Today', category: 'Store Utilities', description: 'Monthly Electricity Bill Payment', amount: 3850.00, method: 'Net Banking' },
  { id: 2, date: '05 Sep 2026', category: 'Packaging & Bags', description: '500 Nos Biodegradable Carrying Bags', amount: 920.00, method: 'Cash' },
  { id: 3, date: '01 Sep 2026', category: 'Store Rent', description: 'Store Premises Shop #4 Monthly Rent', amount: 22000.00, method: 'Bank Transfer' },
  { id: 4, date: '28 Aug 2026', category: 'Staff Advance', description: 'Cashier Staff Festival Advance', amount: 4000.00, method: 'Cash' },
  { id: 5, date: '25 Aug 2026', category: 'Maintenance', description: 'Refrigerated Display Counter Servicing', amount: 1500.00, method: 'UPI' }
];

// App State
let state = {
  products: JSON.parse(localStorage.getItem('freshledger_products') || 'null') || DEFAULT_PRODUCTS,
  customers: JSON.parse(localStorage.getItem('freshledger_customers') || 'null') || DEFAULT_CUSTOMERS,
  suppliers: JSON.parse(localStorage.getItem('freshledger_suppliers') || 'null') || DEFAULT_SUPPLIERS,
  purchases: JSON.parse(localStorage.getItem('freshledger_purchases') || 'null') || DEFAULT_PURCHASES,
  sales: JSON.parse(localStorage.getItem('freshledger_sales') || 'null') || DEFAULT_SALES,
  expenses: JSON.parse(localStorage.getItem('freshledger_expenses') || 'null') || DEFAULT_EXPENSES,
  heldBills: JSON.parse(localStorage.getItem('freshledger_held_bills') || '[]'),
  cart: []
};
let lastCompletedSale = null;
let checkoutInProgress = false;
let loyaltyValuePerPoint = 1;
let pendingUpiResolution = null;
let pendingResumeBill = null;
let checkoutButtonMarkup = null;

// Save State Helper
function saveState() {
  localStorage.setItem('freshledger_products', JSON.stringify(state.products));
  localStorage.setItem('freshledger_customers', JSON.stringify(state.customers));
  localStorage.setItem('freshledger_suppliers', JSON.stringify(state.suppliers));
  localStorage.setItem('freshledger_purchases', JSON.stringify(state.purchases));
  localStorage.setItem('freshledger_sales', JSON.stringify(state.sales));
  localStorage.setItem('freshledger_expenses', JSON.stringify(state.expenses));
  localStorage.setItem('freshledger_held_bills', JSON.stringify(state.heldBills));
}

// Lifecycle Initialization
document.addEventListener('DOMContentLoaded', () => {
  initMobileDrawer();
  initNavigation();
  renderAllViews();
  initProductForm();
  initCustomerForm();
  initSupplierForm();
  initPOS();
  if (document.getElementById('posProductGrid') || document.getElementById('productsTableBody')) {
    loadBackendData().then(async () => {
      const invoice = sessionStorage.getItem('freshledger_last_completed_invoice');
      if (!invoice || !document.getElementById('btnPrintInvoice')) return;
      try {
        lastCompletedSale = mapTransactionFromApi(
          await apiRequest(`/api/sales/${encodeURIComponent(invoice)}`));
      } catch (error) {
        sessionStorage.removeItem('freshledger_last_completed_invoice');
        showToast('Previous Invoice Unavailable', error.message, 'warning');
      }
    });
  }
});

async function apiRequest(url, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body !== undefined && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
  if (csrfToken && csrfHeader && options.method && options.method !== 'GET') {
    headers.set(csrfHeader, csrfToken);
  }

  const response = await fetch(url, { ...options, headers });
  if (!response.ok) {
    let message = `Request failed (${response.status}).`;
    try {
      const error = await response.json();
      message = error.detail || error.message || message;
    } catch (error) {
      if (!(error instanceof SyntaxError)) throw error;
    }
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.json();
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

function suggestedProductImage(product) {
  const name = String(product?.name || '').normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase();
  const category = String(product?.category || '').toLowerCase();
  const matches = [
    [['rice'], 'rice'], [['chocolate', 'cocoa', 'cadbury'], 'chocolate'],
    [['milk', 'curd', 'yogurt', 'butter', 'cheese', 'paneer'], 'dairy'],
    [['sugar'], 'sugar'], [['salt'], 'salt'], [['flour', 'atta'], 'flour'], [['oil', 'ghee'], 'oil'],
    [['egg', 'eggs'], 'eggs'], [['bread', 'loaf'], 'bread'],
    [['noodle', 'noodles', 'pasta'], 'noodles'], [['tea'], 'tea'],
    [['masala', 'spice', 'spices'], 'spices'], [['ketchup', 'sauce'], 'condiments'],
    [['bhujia', 'sev', 'snack', 'snacks', 'chips'], 'snacks'], [['juice', 'tropicana'], 'juice'],
    [['cola', 'soda'], 'soft-drink'], [['apple', 'apples'], 'apples'],
    [['banana', 'bananas'], 'bananas'], [['tomato', 'tomatoes'], 'tomatoes'], [['potato', 'potatoes'], 'potatoes'],
    [['biscuit', 'biscuits', 'cookie', 'cookies'], 'biscuits'], [['soap'], 'soap']
  ];
  const match = matches.find(([terms]) => terms.some(term => new RegExp(`\\b${term}\\b`).test(name)));
  if (match) return `/images/products/${match[1]}.svg`;
  if (category.includes('dairy')) return '/images/products/dairy.svg';
  if (category.includes('produce') || category.includes('fruit') || category.includes('vegetable')) return '/images/products/produce.svg';
  if (category.includes('bakery')) return '/images/products/bakery.svg';
  if (category.includes('beverage') || category.includes('drink')) return '/images/products/beverages.svg';
  if (category.includes('personal care')) return '/images/products/personal-care.svg';
  if (category.includes('household')) return '/images/products/household.svg';
  if (category.includes('grocery')) return '/images/products/grocery.svg';
  return '/images/products/placeholder.svg';
}

function productImageMarkup(product, className = 'product-thumb') {
  const path = product?.imagePath;
  const safePath = typeof path === 'string'
    && (path.startsWith('/images/products/') || path.startsWith('/product-images/'))
    ? path : suggestedProductImage(product);
  return `<img class="${escapeHtml(className)}" src="${escapeHtml(safePath)}" alt="${escapeHtml(product?.name || 'Product image')}" loading="lazy" onerror="this.onerror=null;this.src='/images/products/placeholder.svg'">`;
}

function mapTransactionFromApi(transaction) {
  const customer = state.customers.find(item => item.username === transaction.customerUsername);
  const items = transaction.items.map(item => ({
    id: item.productId,
    name: item.name,
    price: Number(item.price),
    qty: item.quantity
  }));
  return {
    invoice: transaction.invoice,
    date: transaction.date,
    customer: transaction.customer,
    customerUsername: transaction.customerUsername,
    paymentMode: transaction.paymentMode,
    cashier: transaction.cashier,
    items: items.length,
    cartItems: items,
    amount: Number(transaction.amount),
    subtotal: Number(transaction.subtotal),
    discount: Number(transaction.discount),
    tax: Number(transaction.tax),
    status: transaction.status,
    phone: customer?.phone || ''
  };
}

async function loadBackendData() {
  try {
    const [products, customers, loyaltyRules] = await Promise.all([
      apiRequest('/api/products'),
      apiRequest('/api/customers'),
      apiRequest('/api/loyalty/rules')
    ]);
    state.products = products.map(product => ({
      ...product,
      purchasePrice: Number(product.purchasePrice),
      price: Number(product.price)
    }));
    state.customers = customers;
    loyaltyValuePerPoint = Number(loyaltyRules.valuePerPoint);

    if (document.getElementById('salesTableBody')) {
      const transactions = await apiRequest('/api/sales');
      state.sales = transactions.map(mapTransactionFromApi);
    }
    renderAllViews();
  } catch (error) {
    console.error('Could not load billing data from the server.', error);
    showToast('Data Load Failed', error.message, 'danger');
  }
}

// Modal Global Triggers
function openProductModal() {
  const m = document.getElementById('productModal');
  const form = document.getElementById('productForm');
  if (!m || !form) return;
  clearProductImagePreviewUrl();
  editingProductId = null;
  productImageRemovalPending = false;
  form.reset();
  document.getElementById('productModalTitle').textContent = '📦 Add Product';
  document.getElementById('productFormSubmit').textContent = 'Add Product';
  document.getElementById('productImagePreview').src = '/images/products/placeholder.svg';
  document.getElementById('removeProductImageBtn').hidden = true;
  m.classList.add('active');
}
let editingProductId = null;
let productImageRemovalPending = false;
let selectedProductImagePreviewUrl = null;

function clearProductImagePreviewUrl() {
  if (selectedProductImagePreviewUrl) {
    URL.revokeObjectURL(selectedProductImagePreviewUrl);
    selectedProductImagePreviewUrl = null;
  }
}

function openEditProductModal(id) {
  const product = state.products.find(item => item.id === id);
  const form = document.getElementById('productForm');
  const modal = document.getElementById('productModal');
  if (!product || !form || !modal) return;
  editingProductId = id;
  productImageRemovalPending = false;
  clearProductImagePreviewUrl();
  form.elements.name.value = product.name;
  form.elements.category.value = product.category;
  form.elements.brand.value = product.brand;
  form.elements.barcode.value = product.barcode;
  form.elements.purchasePrice.value = product.purchasePrice;
  form.elements.sellingPrice.value = product.price;
  form.elements.openingStock.value = product.stock;
  form.elements.minStock.value = product.minStock;
  form.elements.productImage.value = '';
  document.getElementById('productImagePreview').src = productImageMarkupUrl(product);
  document.getElementById('productModalTitle').textContent = '📦 Edit Product';
  document.getElementById('productFormSubmit').textContent = 'Save Changes';
  document.getElementById('removeProductImageBtn').hidden = false;
  modal.classList.add('active');
}

function productImageMarkupUrl(product) {
  const path = product?.imagePath;
  return typeof path === 'string'
    && (path.startsWith('/images/products/') || path.startsWith('/product-images/'))
    ? path : suggestedProductImage(product);
}

function removeProductImageFromForm() {
  const fileInput = document.getElementById('productImageInput');
  const preview = document.getElementById('productImagePreview');
  if (fileInput) fileInput.value = '';
  clearProductImagePreviewUrl();
  if (preview) preview.src = '/images/products/placeholder.svg';
  productImageRemovalPending = editingProductId !== null;
}
function closeProductModal() {
  const m = document.getElementById('productModal');
  clearProductImagePreviewUrl();
  if (m) m.classList.remove('active');
}

function openCustomerModal() {
  const m = document.getElementById('customerModal');
  if (m) m.classList.add('active');
}
function closeCustomerModal() {
  const m = document.getElementById('customerModal');
  if (m) m.classList.remove('active');
}

function openSupplierModal() {
  const m = document.getElementById('supplierModal');
  if (m) m.classList.add('active');
}
function closeSupplierModal() {
  const m = document.getElementById('supplierModal');
  if (m) m.classList.remove('active');
}

function closeUpiModal() {
  const m = document.getElementById('upiModal');
  if (m) m.classList.remove('active');
  if (pendingUpiResolution) {
    const resolvePayment = pendingUpiResolution;
    pendingUpiResolution = null;
    resolvePayment(false);
  }
}

function closeWhatsAppModal() {
  const m = document.getElementById('whatsAppModal');
  if (m) m.classList.remove('active');
}

/* 1. Mobile Sidebar Navigation Drawer */
function initMobileDrawer() {
  const toggleBtn = document.getElementById('mobileNavToggle');
  const sidebar = document.getElementById('sidebar');
  let backdrop = document.getElementById('sidebarBackdrop');

  if (!toggleBtn || !sidebar) return;

  if (!backdrop) {
    backdrop = document.createElement('div');
    backdrop.id = 'sidebarBackdrop';
    backdrop.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.4);z-index:35;display:none;';
    document.body.appendChild(backdrop);
  }

  toggleBtn.addEventListener('click', () => {
    sidebar.classList.add('mobile-open');
    backdrop.style.display = 'block';
  });

  backdrop.addEventListener('click', () => {
    sidebar.classList.remove('mobile-open');
    backdrop.style.display = 'none';
  });
}

/* 2. Navigation Controller */
function initNavigation() {
  const navItems = document.querySelectorAll('.nav-item[data-view]');
  const views = document.querySelectorAll('.page-view');

  navItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const targetView = item.getAttribute('data-view');

      navItems.forEach(n => n.classList.remove('active'));
      item.classList.add('active');

      views.forEach(v => {
        v.style.display = (v.id === targetView + '-view') ? 'block' : 'none';
      });

      const pageTitle = document.getElementById('currentPageTitle');
      if (pageTitle) {
        pageTitle.textContent = item.getAttribute('data-label') || 'Dashboard';
      }

      const sidebar = document.getElementById('sidebar');
      const backdrop = document.getElementById('sidebarBackdrop');
      if (sidebar && sidebar.classList.contains('mobile-open')) {
        sidebar.classList.remove('mobile-open');
        if (backdrop) backdrop.style.display = 'none';
      }
    });
  });
}

/* 3. Global Views Renderer */
function renderAllViews() {
  renderPOSCatalog();
  renderPOSCustomerDropdown();
  renderHeldBillsDashboard();
  renderProductsTable();
  renderInventoryTable();
  renderCustomersTable();
  renderSuppliersTable();
  renderPurchasesTable();
  renderSalesTable();
  renderExpensesTable();
  renderAlerts();
}

function heldBillTotal(items) {
  return calculateCartTotals(items).grandTotal;
}

function renderHeldBillsDashboard() {
  const count = document.getElementById('heldBillsCount');
  if (count) count.textContent = String(state.heldBills.length);
  const container = document.getElementById('heldBillsList');
  if (!container) return;
  container.replaceChildren();
  if (state.heldBills.length === 0) {
    container.textContent = 'No held bills.';
    return;
  }
  state.heldBills.forEach(bill => {
    const row = document.createElement('div');
    row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:8px;border:1px solid #ebf0ed;padding:10px 12px;border-radius:12px;';
    const details = document.createElement('span');
    details.textContent = `${bill.label} · ${bill.items.length} item type(s) · ₹${heldBillTotal(bill.items).toFixed(2)}`;
    const resume = document.createElement('button');
    resume.className = 'btn btn-secondary';
    resume.type = 'button';
    resume.textContent = 'Resume';
    resume.addEventListener('click', () => resumeHeldBill(bill.id));
    row.append(details, resume);
    container.append(row);
  });
}

function showHeldBills() {
  const existing = document.getElementById('heldBillsModal');
  if (existing) existing.remove();
  const modal = document.createElement('div');
  modal.id = 'heldBillsModal';
  modal.className = 'modal-backdrop active';
  modal.setAttribute('role', 'dialog');
  modal.setAttribute('aria-modal', 'true');
  const card = document.createElement('div');
  card.className = 'modal-card';
  const heading = document.createElement('div');
  heading.className = 'modal-header';
  const title = document.createElement('h3');
  title.className = 'modal-title';
  title.textContent = 'Held Bills';
  const close = document.createElement('button');
  close.className = 'modal-close-btn';
  close.type = 'button';
  close.textContent = '×';
  close.setAttribute('aria-label', 'Close held bills');
  close.addEventListener('click', () => modal.remove());
  heading.append(title, close);
  const body = document.createElement('div');
  body.className = 'modal-body';
  if (state.heldBills.length === 0) {
    body.textContent = 'No held bills. Use “Hold Bill” to save the current cart for later.';
  } else {
    state.heldBills.forEach(bill => {
      const row = document.createElement('div');
      row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px 0;border-bottom:1px solid var(--border-subtle);';
      const description = document.createElement('span');
      description.textContent = `${bill.label} · ${bill.items.length} item type(s) · ₹${heldBillTotal(bill.items).toFixed(2)}`;
      const resume = document.createElement('button');
      resume.className = 'btn btn-primary';
      resume.type = 'button';
      resume.textContent = 'Resume';
      resume.addEventListener('click', () => resumeHeldBill(bill.id));
      row.append(description, resume);
      body.append(row);
    });
  }
  card.append(heading, body);
  modal.append(card);
  modal.addEventListener('click', event => {
    if (event.target === modal) modal.remove();
  });
  document.body.append(modal);
  close.focus();
}

function resumeHeldBill(id) {
  const index = state.heldBills.findIndex(bill => bill.id === id);
  if (index < 0) return;
  const bill = state.heldBills[index];
  if (state.cart.length > 0) {
    showToast('Current Bill Not Empty', 'Hold or clear the current bill before resuming another one.', 'warning');
    return;
  }
  state.heldBills.splice(index, 1);
  saveState();
  if (!document.getElementById('posCartItems')) {
    sessionStorage.setItem('freshledger_resume_bill', JSON.stringify(bill));
    window.location.href = '/cashier/pos';
    return;
  }
  state.cart = bill.items.map(item => ({ ...item }));
  const customerSelect = document.getElementById('posCustomerSelect');
  if (customerSelect) customerSelect.value = bill.customerUsername || 'Walk-in Retail';
  const payment = Array.from(document.querySelectorAll('input[name="payType"]'))
    .find(radio => radio.value === (bill.paymentMode || 'Cash'));
  if (payment) payment.checked = true;
  renderCart();
  renderHeldBillsDashboard();
  document.getElementById('heldBillsModal')?.remove();
  showToast('Bill Resumed', `${bill.label} is back in the active cart.`, 'success');
}

function renderPOSCustomerDropdown() {
  const select = document.getElementById('posCustomerSelect');
  if (!select) return;

  let html = `<option value="Walk-in Retail">Walk-in Retail Customer</option>`;
  state.customers.forEach(c => {
    const value = c.username || c.name;
    html += `<option value="${value}">${c.name} (${c.phone || 'no phone'})</option>`;
  });
  select.innerHTML = html;
  if (pendingResumeBill) {
    select.value = pendingResumeBill.customerUsername || 'Walk-in Retail';
    const payRadio = Array.from(document.querySelectorAll('input[name="payType"]'))
      .find(radio => radio.value === (pendingResumeBill.paymentMode || 'Cash'));
    if (payRadio) payRadio.checked = true;
    pendingResumeBill = null;
    sessionStorage.removeItem('freshledger_resume_bill');
  }
  updatePOSLoyaltyControls();
}

function updatePOSLoyaltyControls() {
  const select = document.getElementById('posCustomerSelect');
  const input = document.getElementById('posPointsToRedeem');
  if (!select || !input) return;
  const customer = state.customers.find(item => item.username === select.value);
  const available = customer ? Number(customer.points || 0) : 0;
  input.disabled = available <= 0;
  input.max = String(available);
  const requested = Number.parseInt(input.value || '0', 10) || 0;
  if (requested > available) input.value = String(available);
  const applied = Math.min(available, Number.parseInt(input.value || '0', 10) || 0);
  const availableDisplay = document.getElementById('posAvailablePoints');
  const valueDisplay = document.getElementById('posRedeemValue');
  if (availableDisplay) availableDisplay.textContent = available.toLocaleString();
  if (valueDisplay) valueDisplay.textContent = applied
    ? `Value: ₹${(applied * loyaltyValuePerPoint).toFixed(2)}`
    : 'No points applied';
  if (document.getElementById('posCartItems')) renderCart();
}

/* 4. POS Catalog & Checkout Engine */
let activePOSCategory = 'All';

function renderPOSCatalog(categoryFilter = activePOSCategory) {
  const grid = document.getElementById('posProductGrid');
  if (!grid) return;

  activePOSCategory = categoryFilter;
  const searchTerm = document.getElementById('posProductSearch')?.value.trim().toLowerCase() || '';
  let filtered = state.products;
  if (categoryFilter !== 'All') {
    filtered = state.products.filter(p => p.category.toLowerCase().includes(categoryFilter.toLowerCase()));
  }
  if (searchTerm) {
    filtered = filtered.filter(product =>
      `${product.name} ${product.barcode} ${product.category} ${product.brand}`.toLowerCase().includes(searchTerm));
  }

  if (filtered.length === 0) {
    grid.innerHTML = `
      <div class="empty-state" style="grid-column:1/-1;padding:var(--space-xl);">
        <div class="empty-state-icon">🔍</div>
        <div class="empty-state-title">No products found</div>
        <div class="empty-state-desc">Try clearing your search or category filter.</div>
      </div>
    `;
    return;
  }

  grid.innerHTML = filtered.map(p => `
    <div class="pos-product-card" role="button" tabindex="0" aria-label="Add ${escapeHtml(p.name)} to bill"
         onclick="addToCart(${p.id})"
         onkeydown="if(event.key==='Enter'||event.key===' '){event.preventDefault();addToCart(${p.id})}">
      <div class="pos-product-heading">
        ${productImageMarkup(p, 'pos-product-image')}
        <div>
          <div class="pos-product-name">${escapeHtml(p.name)}</div>
          <div class="pos-product-meta">${escapeHtml(p.category)} • Stock: <strong>${p.stock}</strong></div>
        </div>
      </div>
      <div style="display:flex;align-items:center;justify-content:space-between;margin-top:var(--space-xs);">
        <div class="pos-product-price">₹${p.price.toFixed(2)}</div>
        <button class="btn btn-primary" style="padding:2px 8px;font-size:0.78rem;" type="button"
                onclick="event.stopPropagation();addToCart(${p.id})">+ Add</button>
      </div>
    </div>
  `).join('');
}

function calculateCartTotals(items) {
  const subtotalCents = items.reduce((sum, item) => {
    return sum + Math.round(item.price * 100) * item.qty;
  }, 0);
  const discountCents = Math.round(subtotalCents * 0.08);
  const taxCents = Math.round(subtotalCents * 0.05);

  return {
    subtotal: subtotalCents / 100,
    discount: discountCents / 100,
    tax: taxCents / 100,
    grandTotal: (subtotalCents - discountCents + taxCents) / 100
  };
}

function getBillTotals(sale, items = []) {
  const calculatedTotals = calculateCartTotals(items);
  const hasItems = items.length > 0;

  return {
    subtotal: sale.subtotal ?? (hasItems ? calculatedTotals.subtotal : sale.amount),
    discount: sale.discount ?? (hasItems ? calculatedTotals.discount : 0),
    tax: sale.tax ?? (hasItems ? calculatedTotals.tax : 0),
    grandTotal: sale.amount ?? calculatedTotals.grandTotal
  };
}

function addToCart(productId) {
  const product = state.products.find(p => p.id === productId);
  if (!product) return;

  if (product.stock <= 0) {
    showToast('Out of Stock', `${product.name} is currently out of stock.`, 'warning');
    return;
  }

  const existing = state.cart.find(item => item.id === productId);
  if (existing) {
    if (existing.qty + 1 > product.stock) {
      showToast('Stock Limit Reached', `Only ${product.stock} units available in inventory.`, 'warning');
      return;
    }
    existing.qty += 1;
  } else {
    state.cart.push({ id: product.id, name: product.name, price: product.price, qty: 1 });
  }

  renderCart();
  showToast('Item Added', `${product.name} added to bill cart.`, 'success');
}

function updateCartQty(index, delta) {
  const item = state.cart[index];
  if (!item) return;

  const product = state.products.find(p => p.id === item.id);
  const newQty = item.qty + delta;

  if (newQty <= 0) {
    state.cart.splice(index, 1);
  } else if (product && newQty > product.stock) {
    showToast('Stock Limit', `Only ${product.stock} units available.`, 'warning');
    return;
  } else {
    item.qty = newQty;
  }

  renderCart();
}

function renderCart() {
  const container = document.getElementById('posCartItems');
  const totalDisplay = document.getElementById('posGrandTotal');
  const subtotalDisplay = document.getElementById('posSubtotal');
  const discountDisplay = document.getElementById('posDiscount');
  const taxDisplay = document.getElementById('posTax');
  const redemptionDisplay = document.getElementById('posLoyaltyRedemption');
  if (!container) return;

  if (state.cart.length === 0) {
    if (subtotalDisplay) subtotalDisplay.textContent = '₹0.00';
    if (discountDisplay) discountDisplay.textContent = '₹0.00';
    if (taxDisplay) taxDisplay.textContent = '₹0.00';
    if (redemptionDisplay) redemptionDisplay.textContent = '₹0.00';
    if (totalDisplay) totalDisplay.textContent = '₹0.00';
  }

  if (state.cart.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="padding: var(--space-xl) 0;">
        <div class="empty-state-icon" style="width:48px;height:48px;font-size:1.2rem;">🛒</div>
        <div class="empty-state-title" style="font-size:0.95rem;">Cart is empty</div>
        <div class="empty-state-desc" style="font-size:0.8rem;">Click products on left to add to bill</div>
      </div>
    `;
    return;
  }

  container.innerHTML = state.cart.map((item, index) => {
    const lineTotal = Math.round(item.price * 100) * item.qty / 100;
    const product = state.products.find(candidate => candidate.id === item.id) || item;
    return `
      <div class="cart-item-row">
        ${productImageMarkup(product, 'cart-product-image')}
        <div class="cart-item-info">
          <div class="cart-item-title">${escapeHtml(item.name)}</div>
          <div class="cart-item-price">₹${item.price.toFixed(2)} each</div>
        </div>
        <div class="qty-control">
          <button class="btn-qty" type="button" onclick="updateCartQty(${index}, -1)">-</button>
          <span class="qty-val">${item.qty}</span>
          <button class="btn-qty" type="button" onclick="updateCartQty(${index}, 1)">+</button>
        </div>
        <div style="font-weight:700;font-size:0.95rem;width:75px;text-align:right;">
          ₹${lineTotal.toFixed(2)}
        </div>
      </div>
    `;
  }).join('');

  const totals = calculateCartTotals(state.cart);
  const requestedPoints = Number.parseInt(document.getElementById('posPointsToRedeem')?.value || '0', 10) || 0;
  const redemptionAmount = Math.min(totals.grandTotal, requestedPoints * loyaltyValuePerPoint);

  if (subtotalDisplay) subtotalDisplay.textContent = `₹${totals.subtotal.toFixed(2)}`;
  if (discountDisplay) discountDisplay.textContent = `₹${totals.discount.toFixed(2)}`;
  if (taxDisplay) taxDisplay.textContent = `₹${totals.tax.toFixed(2)}`;
  if (redemptionDisplay) redemptionDisplay.textContent = `₹${redemptionAmount.toFixed(2)}`;
  if (totalDisplay) totalDisplay.textContent = `₹${(totals.grandTotal - redemptionAmount).toFixed(2)}`;
}

// 5. UPI Payment & PhonePe Deep Linking Handler
function triggerUpiCheckout(total, operationId, customerUsername, cartItemsCopy) {
  const upiModal = document.getElementById('upiModal');
  if (!upiModal) {
    throw new Error('UPI payment is unavailable on this POS page.');
  }

  document.getElementById('upiAmountDisplay').textContent = `₹${total.toFixed(2)}`;
  const invoiceLabel = `FreshLedger_${operationId.slice(0, 8)}`;

  const upiUrl = `upi://pay?pa=freshledger@upi&pn=FreshLedger%20Supermarket&am=${total.toFixed(2)}&cu=INR&tn=${invoiceLabel}`;

  // Set PhonePe & GPay Deep Links
  const phonePeBtn = document.getElementById('btnPhonePeDeepLink');
  if (phonePeBtn) phonePeBtn.href = upiUrl;

  const gPayBtn = document.getElementById('btnGPayDeepLink');
  if (gPayBtn) gPayBtn.href = upiUrl;

  // Generate dynamic QR Code using QR Server API
  const qrImg = document.getElementById('upiQrImage');
  if (qrImg) {
    qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(upiUrl)}`;
  }

  upiModal.classList.add('active');

  const confirmBtn = document.getElementById('btnConfirmUpiSuccess');
  return new Promise(resolve => {
    pendingUpiResolution = resolve;
    confirmBtn.onclick = () => {
      const resolvePayment = pendingUpiResolution;
      pendingUpiResolution = null;
      closeUpiModal();
      if (resolvePayment) resolvePayment(true);
    };
  });
}

// 6. WhatsApp Bill Receipt Handler
/* Normalization helper for WhatsApp Indian/International phone numbers */
function normalizeWhatsAppPhone(rawPhone) {
  if (!rawPhone) return '';
  let digits = rawPhone.replace(/[^0-9]/g, '');
  if (!digits) return '';
  if (digits.length === 10) {
    digits = '91' + digits;
  }
  return digits;
}

/* Dynamic Bill Message Generator for WhatsApp */
function generateWhatsAppBillMessage(sale, cartItems = []) {
  const totals = getBillTotals(sale, cartItems);
  let itemsList = '';
  if (cartItems && cartItems.length > 0) {
    itemsList = cartItems.map((item, idx) =>
      `${idx + 1}. ${item.name} × ${item.qty} = ₹${(item.price * item.qty).toFixed(2)}`
    ).join('\n');
  } else {
    itemsList = `• Bill Items (${sale.items || 1} items) = ₹${(sale.amount || 0).toFixed(2)}`;
  }

  return `Hello ${sale.customer || 'Valued Customer'},\n\n` +
    `Thank you for shopping with FreshLedger Supermarket.\n\n` +
    `🧾 BILL RECEIPT\n` +
    `Invoice No: ${sale.invoice}\n` +
    `Date: ${sale.date || 'Today'}\n\n` +
    `Items:\n` +
    `${itemsList}\n\n` +
    `Subtotal: ₹${totals.subtotal.toFixed(2)}\n` +
    `Discount: ₹${totals.discount.toFixed(2)}\n` +
    `Tax: ₹${totals.tax.toFixed(2)}\n` +
    `*Grand Total: ₹${totals.grandTotal.toFixed(2)}*\n\n` +
    `Payment Method: ${sale.paymentMode || 'Cash'}\n\n` +
    `Thank you for shopping with us!\n\n` +
    `FreshLedger Supermarket`;
}

function createInvoicePdf(sale, cartItems = []) {
  if (!window.jspdf || !window.jspdf.jsPDF) {
    showToast('PDF Unavailable', 'The PDF generator is still loading. Please try again.', 'warning');
    return null;
  }

  const { jsPDF } = window.jspdf;
  const pdf = new jsPDF();
  const totals = getBillTotals(sale, cartItems);
  const pageWidth = pdf.internal.pageSize.getWidth();
  let y = 18;

  pdf.setFontSize(18);
  pdf.setFont(undefined, 'bold');
  pdf.text('FRESHLEDGER SUPERMARKET', pageWidth / 2, y, { align: 'center' });
  y += 8;
  pdf.setFontSize(10);
  pdf.setFont(undefined, 'normal');
  pdf.text('TAX INVOICE / RECEIPT', pageWidth / 2, y, { align: 'center' });
  y += 12;
  pdf.line(14, y, pageWidth - 14, y);
  y += 9;

  pdf.text(`Invoice: ${sale.invoice}`, 14, y);
  pdf.text(`Date: ${sale.date || 'Today'}`, pageWidth - 14, y, { align: 'right' });
  y += 7;
  pdf.text(`Customer: ${sale.customer || 'Walk-in Retail'}`, 14, y);
  pdf.text(`Payment: ${sale.paymentMode || 'Cash'}`, pageWidth - 14, y, { align: 'right' });
  y += 10;

  pdf.setFont(undefined, 'bold');
  pdf.text('Item', 14, y);
  pdf.text('Qty', 125, y, { align: 'right' });
  pdf.text('Amount', pageWidth - 14, y, { align: 'right' });
  y += 3;
  pdf.line(14, y, pageWidth - 14, y);
  y += 8;
  pdf.setFont(undefined, 'normal');

  const items = cartItems.length ? cartItems : [{
    name: `Bill Items (${sale.items || 1} items)`,
    qty: 1,
    price: sale.amount || 0
  }];
  items.forEach(item => {
    const lineTotal = item.price * item.qty;
    const itemName = pdf.splitTextToSize(item.name, 100);
    pdf.text(itemName, 14, y);
    pdf.text(String(item.qty), 125, y, { align: 'right' });
    pdf.text(`Rs. ${lineTotal.toFixed(2)}`, pageWidth - 14, y, { align: 'right' });
    y += Math.max(7, itemName.length * 5);
    if (y > 265) {
      pdf.addPage();
      y = 18;
    }
  });

  y += 4;
  pdf.line(14, y, pageWidth - 14, y);
  y += 9;
  pdf.text(`Subtotal: Rs. ${totals.subtotal.toFixed(2)}`, pageWidth - 14, y, { align: 'right' });
  y += 7;
  pdf.text(`Discount: Rs. ${totals.discount.toFixed(2)}`, pageWidth - 14, y, { align: 'right' });
  y += 7;
  pdf.text(`GST Tax (5%): Rs. ${totals.tax.toFixed(2)}`, pageWidth - 14, y, { align: 'right' });
  y += 9;
  pdf.setFont(undefined, 'bold');
  pdf.setFontSize(13);
  pdf.text(`Grand Total: Rs. ${totals.grandTotal.toFixed(2)}`, pageWidth - 14, y, { align: 'right' });
  pdf.setFont(undefined, 'normal');
  pdf.setFontSize(9);
  pdf.text('Thank you for shopping with FreshLedger!', pageWidth / 2, 285, { align: 'center' });

  return pdf;
}

async function shareInvoicePdf(sale, cartItems = [], targetPhone = null) {
  const pdf = createInvoicePdf(sale, cartItems);
  if (!pdf) return;

  const fileName = `FreshLedger_${sale.invoice}.pdf`;
  const pdfBlob = pdf.output('blob');
  const pdfFile = new File([pdfBlob], fileName, { type: 'application/pdf' });
  const messageText = generateWhatsAppBillMessage(sale, cartItems);

  if (navigator.share && (!navigator.canShare || navigator.canShare({ files: [pdfFile] }))) {
    try {
      await navigator.share({
        title: `Invoice ${sale.invoice}`,
        text: messageText,
        files: [pdfFile]
      });
      showToast('PDF Ready', 'Choose WhatsApp in the share sheet to send the invoice document.', 'success');
      return;
    } catch (error) {
      if (error.name === 'AbortError') return;
    }
  }

  const downloadUrl = URL.createObjectURL(pdfBlob);
  const downloadLink = document.createElement('a');
  downloadLink.href = downloadUrl;
  downloadLink.download = fileName;
  downloadLink.click();
  URL.revokeObjectURL(downloadUrl);

  const phone = normalizeWhatsAppPhone(targetPhone || sale.phone || '');
  const waUrl = phone
    ? `https://wa.me/${phone}?text=${encodeURIComponent(`${messageText}\n\nPDF invoice downloaded: ${fileName}. Please attach it before sending.`)}`
    : 'https://web.whatsapp.com/';
  window.open(waUrl, '_blank');
  showToast('PDF Downloaded', `${fileName} downloaded. Attach it in WhatsApp before sending.`, 'info');
}

/* Direct WhatsApp Launcher - DOES NOT CALL window.print() OR OPEN PRINT DIALOG */
function launchWhatsAppDirectly(sale, cartItems = [], targetPhone = null) {
  let phone = targetPhone || sale.phone || '';
  if (!phone && sale.customer) {
    const custObj = state.customers.find(c => c.name === sale.customer);
    if (custObj) phone = custObj.phone;
  }

  const cleanPhone = normalizeWhatsAppPhone(phone);
  if (!cleanPhone || cleanPhone.length < 10) {
    openWhatsAppModal(sale, cartItems);
    return;
  }
  const message = generateWhatsAppBillMessage(sale, cartItems);
  const whatsappUrl = `https://wa.me/${cleanPhone}?text=${encodeURIComponent(message)}`;
  window.open(whatsappUrl, '_blank');
  showToast('WhatsApp Ready', 'WhatsApp opened with the completed bill details. Review and send it there.', 'info');
}

function openWhatsAppModal(sale, cartItems = []) {
  const modal = document.getElementById('whatsAppModal');
  if (!modal) return;

  let phone = sale.phone || '';
  if (!phone && sale.customer) {
    const custObj = state.customers.find(c => c.name === sale.customer);
    if (custObj) phone = custObj.phone;
  }

  const phoneInput = document.getElementById('whatsappPhoneInput');
  if (phoneInput) phoneInput.value = phone;

  const receiptMsg = generateWhatsAppBillMessage(sale, cartItems);
  const previewText = document.getElementById('whatsappPreviewText');
  if (previewText) previewText.value = receiptMsg;

  modal.classList.add('active');

  const sendBtn = document.getElementById('btnOpenWhatsApp');
  if (sendBtn) {
    sendBtn.onclick = () => {
      const rawPhone = phoneInput ? phoneInput.value : '';
      const cleanPhone = normalizeWhatsAppPhone(rawPhone);

      if (!cleanPhone || cleanPhone.length < 10) {
        showToast('Valid Phone Required', 'Customer phone number is required to send this bill through WhatsApp.', 'warning');
        return;
      }

      closeWhatsAppModal();
      launchWhatsAppDirectly(sale, cartItems, cleanPhone);
    };
  }
}

function closeWhatsAppModal() {
  const modal = document.getElementById('whatsAppModal');
  if (modal) modal.classList.remove('active');
}

/* Independent Print Bill Function - PURE PRINTING ONLY */
function printInvoiceBill(sale, cartItems = []) {
  const totals = getBillTotals(sale, cartItems);
  const itemsHtml = (cartItems && cartItems.length > 0)
    ? cartItems.map((item, idx) => `
        <tr>
          <td style="padding:6px;border-bottom:1px solid #eee;">${idx + 1}</td>
          <td style="padding:6px;border-bottom:1px solid #eee;"><strong>${item.name}</strong></td>
          <td style="padding:6px;border-bottom:1px solid #eee;text-align:center;">${item.qty}</td>
          <td style="padding:6px;border-bottom:1px solid #eee;text-align:right;">₹${item.price.toFixed(2)}</td>
          <td style="padding:6px;border-bottom:1px solid #eee;text-align:right;"><strong>₹${(item.price * item.qty).toFixed(2)}</strong></td>
        </tr>
      `).join('')
    : `<tr><td colspan="5" style="padding:12px;text-align:center;">Bill Items (${sale.items || 1} items) — Total ₹${(sale.amount || 0).toFixed(2)}</td></tr>`;

  const printWindow = window.open('', '_blank', 'width=800,height=900');
  if (printWindow) {
    printWindow.document.write(`
      <!DOCTYPE html>
      <html>
      <head>
        <title>Invoice ${sale.invoice} - FreshLedger Supermarket</title>
        <style>
          body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 20px; color: #1e293b; max-width: 750px; margin: 0 auto; }
          .header { text-align: center; border-bottom: 2px solid #107048; padding-bottom: 15px; margin-bottom: 20px; }
          .brand { font-size: 24px; font-weight: 800; color: #107048; }
          .meta-table { width: 100%; margin-bottom: 20px; font-size: 14px; }
          .items-table { width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 14px; }
          .items-table th { background: #f0fdf4; color: #0b4f33; padding: 8px; text-align: left; border-bottom: 2px solid #cbd5e1; }
          .total-box { background: #f8fafc; padding: 15px; border-radius: 8px; border: 1px solid #e2e8f0; font-size: 16px; margin-bottom: 20px; }
          .footer { text-align: center; font-size: 12px; color: #64748b; margin-top: 30px; border-top: 1px dashed #cbd5e1; padding-top: 10px; }
          @media print { body { padding: 0; } }
        </style>
      </head>
      <body>
        <div class="header">
          <div class="brand">🛒 FRESHLEDGER SUPERMARKET</div>
          <div style="font-size:13px;color:#64748b;margin-top:4px;">123 Retail Plaza, Main Road • GSTIN: 29AAAAA0000A1Z5 • Ph: +91 98765 00000</div>
          <h2 style="margin:10px 0 0 0;color:#0f172a;">TAX INVOICE / RECEIPT</h2>
        </div>

        <table class="meta-table">
          <tr>
            <td><strong>Invoice #:</strong> ${sale.invoice}</td>
            <td style="text-align:right;"><strong>Date:</strong> ${sale.date || 'Today'}</td>
          </tr>
          <tr>
            <td><strong>Customer:</strong> ${sale.customer || 'Walk-in Retail'}</td>
            <td style="text-align:right;"><strong>Payment Mode:</strong> ${sale.paymentMode || 'Cash'}</td>
          </tr>
        </table>

        <table class="items-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Item Description</th>
              <th style="text-align:center;">Qty</th>
              <th style="text-align:right;">Price</th>
              <th style="text-align:right;">Total</th>
            </tr>
          </thead>
          <tbody>
            ${itemsHtml}
          </tbody>
        </table>

        <div class="total-box">
          <div style="display:flex;justify-content:space-between;margin-bottom:6px;"><span>Subtotal:</span><span>₹${totals.subtotal.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;margin-bottom:6px;color:#107048;"><span>Discount Savings:</span><strong>- ₹${totals.discount.toFixed(2)}</strong></div>
          <div style="display:flex;justify-content:space-between;margin-bottom:6px;"><span>GST Tax (5%):</span><span>+ ₹${totals.tax.toFixed(2)}</span></div>
          <div style="display:flex;justify-content:space-between;font-size:20px;font-weight:800;border-top:2px solid #107048;padding-top:8px;color:#0b4f33;">
            <span>Grand Total:</span><span>₹${totals.grandTotal.toFixed(2)}</span>
          </div>
        </div>

        <div class="footer">
          Thank you for shopping at FreshLedger! 🛒<br>
          This is a computer generated tax invoice receipt.
        </div>

        <script>
          window.onload = function() { window.print(); }
        </script>
      </body>
      </html>
    `);
    printWindow.document.close();
  }
}

function sendWhatsAppBillForInvoice(invoiceNum) {
  const sale = state.sales.find(s => s.invoice === invoiceNum);
  if (!sale) return;
  openWhatsAppModal(sale);
}

async function executeSaleCheckout(total, operationId, customerUsername, payMode, cartItemsCopy, pointsToRedeem) {
  try {
    const completed = await apiRequest('/api/sales', {
      method: 'POST',
      body: JSON.stringify({
        operationId,
        customerUsername: customerUsername === 'Walk-in Retail' ? null : customerUsername,
        paymentMode: payMode,
        pointsToRedeem,
        items: cartItemsCopy.map(item => ({ productId: item.id, quantity: item.qty }))
      })
    });
    const newSale = mapTransactionFromApi(completed);
    lastCompletedSale = newSale;
    sessionStorage.setItem('freshledger_last_completed_invoice', newSale.invoice);
    state.sales.unshift(newSale);
    state.cart = [];
    resetPOSCustomerAndPayment();
    renderCart();
    await loadBackendData();
    showToast('Payment Completed!', `Invoice ${newSale.invoice} of ₹${newSale.amount.toFixed(2)} completed via ${payMode}.`, 'success');
    setTimeout(() => openExplainBillModal(newSale, newSale.cartItems), 400);
  } catch (error) {
    console.error('Sale could not be completed.', error);
    showToast('Payment Failed', error.message, 'danger');
  }
}

/* 11. Customer Budget Guardian Logic */
function updateBudgetGuardian() {
  const budgetInput = document.getElementById('custBudgetInput');
  const alertBadge = document.getElementById('budgetAlertBadge');
  const warningText = document.getElementById('budgetWarningText');
  if (!budgetInput || !alertBadge) return;

  const budget = parseFloat(budgetInput.value);
  if (isNaN(budget) || budget <= 0) {
    alertBadge.textContent = 'On Budget';
    alertBadge.className = 'badge badge-success';
    if (warningText) warningText.style.display = 'none';
    return;
  }

  const currentTotal = state.cart.reduce((sum, item) => sum + (item.price * item.qty), 0);
  if (currentTotal > budget) {
    const diff = currentTotal - budget;
    alertBadge.textContent = `₹${diff.toFixed(2)} Over Budget`;
    alertBadge.className = 'badge badge-danger';
    if (warningText) {
      warningText.textContent = `⚠️ Current cart is ₹${diff.toFixed(2)} over set customer budget of ₹${budget.toFixed(2)}.`;
      warningText.style.display = 'block';
    }
  } else {
    alertBadge.textContent = 'Within Budget';
    alertBadge.className = 'badge badge-success';
    if (warningText) warningText.style.display = 'none';
  }
}

function optimizeCartForBudget() {
  const budgetInput = document.getElementById('custBudgetInput');
  const budget = parseFloat(budgetInput?.value);
  const currentTotal = state.cart.reduce((sum, item) => sum + (item.price * item.qty), 0);

  if (!budget || budget <= 0) {
    showToast('Set Budget First', 'Please enter a target customer budget amount.', 'warning');
    return;
  }

  if (currentTotal <= budget) {
    showToast('Within Budget!', `Current bill total (₹${currentTotal.toFixed(2)}) is within budget limit.`, 'success');
    return;
  }

  const diff = currentTotal - budget;
  let suggestionText = `Target Budget: ₹${budget.toFixed(2)}\nCurrent Total: ₹${currentTotal.toFixed(2)}\nExcess: ₹${diff.toFixed(2)}\n\nSuggestions:\n`;

  if (state.cart.length > 0) {
    const topItem = state.cart[0];
    suggestionText += `1. Reduce ${topItem.name} qty by 1 → Save ₹${topItem.price.toFixed(2)}\n`;
  }
  suggestionText += `2. Switch to smaller pack size or house brand alternative.\n`;

  showConfirmModal({
    title: '🛡️ Cart Budget Optimization',
    message: suggestionText,
    confirmText: 'Acknowledge Suggestion',
    confirmClass: 'btn-primary',
    onConfirm: () => {
      showToast('Budget Advice Shown', 'Customer can approve item quantity adjustments.', 'info');
    }
  });
}

function onPosCustomerChange() {
  const select = document.getElementById('posCustomerSelect');
  const banner = document.getElementById('frequentlyBoughtBanner');
  if (!select) return;

  const name = select.value;
  if (banner && name !== 'Walk-in Retail') {
    banner.style.display = 'block';
  } else if (banner) {
    banner.style.display = 'none';
  }
  updatePOSLoyaltyControls();
}

/* 12. Explain My Bill Summary Modal Generator */
function openExplainBillModal(sale, cartItems = []) {
  const modal = document.getElementById('explainBillModal');
  const container = document.getElementById('explainBillContent');
  if (!modal || !container) return;

  let phone = sale.phone || '';
  if (!phone && sale.customer) {
    const custObj = state.customers.find(c => c.name === sale.customer);
    if (custObj) phone = custObj.phone;
  }

  const totals = getBillTotals(sale, cartItems);
  const estimatedSavings = totals.discount;
  const itemsCount = cartItems.length || sale.items;

  container.innerHTML = `
    <div style="text-align:center;padding-bottom:var(--space-md);border-bottom:1px dashed var(--border-subtle);margin-bottom:var(--space-md);">
      <div style="font-size:1.8rem;font-weight:800;color:var(--primary);">₹${sale.amount.toFixed(2)}</div>
      <div style="font-size:0.84rem;color:var(--text-secondary);">Paid via <strong>${sale.paymentMode}</strong> • Invoice ${sale.invoice}</div>
      <div class="badge badge-success" style="margin-top:6px;font-size:0.84rem;">🎉 Customer saved ₹${estimatedSavings.toFixed(2)} on this transaction today!</div>
    </div>

    <div style="display:flex;flex-direction:column;gap:8px;font-size:0.88rem;margin-bottom:var(--space-md);">
      <div style="display:flex;justify-content:space-between;"><span>Customer:</span><strong>${sale.customer}</strong></div>
      <div style="display:flex;justify-content:space-between;"><span>Total Item Types:</span><strong>${itemsCount} products</strong></div>
      <div style="display:flex;justify-content:space-between;"><span>Subtotal:</span><span>₹${totals.subtotal.toFixed(2)}</span></div>
      <div style="display:flex;justify-content:space-between;color:var(--primary);"><span>Store Savings / Discounts:</span><strong>- ₹${estimatedSavings.toFixed(2)}</strong></div>
      <div style="display:flex;justify-content:space-between;"><span>GST Tax (5%):</span><span>+ ₹${totals.tax.toFixed(2)}</span></div>
      <div style="display:flex;justify-content:space-between;font-weight:800;font-size:1rem;padding-top:6px;border-top:1px solid var(--border-subtle);"><span>Final Bill Total:</span><span style="color:var(--primary);">₹${sale.amount.toFixed(2)}</span></div>
    </div>

    <div class="form-group" style="margin-bottom:var(--space-md);padding:10px;background:var(--bg-app);border-radius:var(--radius-md);">
      <label class="form-label" style="font-size:0.82rem;font-weight:700;">📱 Ordering Customer's WhatsApp Phone Number</label>
      <input id="explainBillPhoneInput" class="form-control" value="${phone}" placeholder="e.g. +91 98765 43210" style="font-size:0.84rem;" />
    </div>

    <div style="padding:10px;background:var(--bg-app);border-radius:var(--radius-md);font-size:0.8rem;color:var(--text-secondary);">
      💡 <strong>Explainable Breakdown:</strong> Customer received loyalty points (+${Math.floor(sale.amount / 10)} pts) and volume discount savings.
    </div>
  `;

  modal.classList.add('active');

  const printBtn = document.getElementById('btnExplainBillPrintPdf');
  if (printBtn) {
    printBtn.onclick = () => {
      printInvoiceBill(sale, cartItems);
    };
  }

  const directWhatsAppBtn = document.getElementById('btnExplainBillDirectWhatsApp');
  if (directWhatsAppBtn) {
    directWhatsAppBtn.onclick = () => {
      const phoneInput = document.getElementById('explainBillPhoneInput');
      const targetPhone = phoneInput ? phoneInput.value : phone;
      closeExplainBillModal();
      launchWhatsAppDirectly(sale, cartItems, targetPhone);
    };
  }
}

function closeExplainBillModal() {
  const modal = document.getElementById('explainBillModal');
  if (modal) modal.classList.remove('active');
}

/* 13. Cashier Shift Management */
function openShiftModal() {
  const modal = document.getElementById('shiftModal');
  if (!modal) return;

  const cashSales = state.sales.filter(s => s.paymentMode.includes('Cash')).reduce((sum, s) => sum + s.amount, 0);
  const digitalSales = state.sales.filter(s => !s.paymentMode.includes('Cash')).reduce((sum, s) => sum + s.amount, 0);
  const openingCash = 5000;
  const expectedCash = openingCash + cashSales;

  document.getElementById('shiftCashSales').textContent = `₹${cashSales.toFixed(2)}`;
  document.getElementById('shiftDigitalSales').textContent = `₹${digitalSales.toFixed(2)}`;
  document.getElementById('shiftExpectedCash').textContent = `₹${expectedCash.toFixed(2)}`;

  modal.classList.add('active');
}

function closeShiftModal() {
  const modal = document.getElementById('shiftModal');
  if (modal) modal.classList.remove('active');
}

function calculateShiftDifference() {
  const actualInput = document.getElementById('actualCashInput');
  const display = document.getElementById('shiftDifferenceDisplay');
  if (!actualInput || !display) return;

  const actual = parseFloat(actualInput.value) || 0;
  const cashSales = state.sales.filter(s => s.paymentMode.includes('Cash')).reduce((sum, s) => sum + s.amount, 0);
  const expected = 5000 + cashSales;
  const diff = actual - expected;

  display.style.display = 'block';
  if (Math.abs(diff) < 0.01) {
    display.style.background = 'var(--success-bg)';
    display.style.color = 'var(--success-dark)';
    display.textContent = '✔ Drawer Cash Matches Expected Total Exactly (₹0.00 difference)';
  } else if (diff < 0) {
    display.style.background = 'var(--danger-bg)';
    display.style.color = 'var(--danger-dark)';
    display.textContent = `⚠️ Shortage Discrepancy: - ₹${Math.abs(diff).toFixed(2)}`;
  } else {
    display.style.background = 'var(--warning-bg)';
    display.style.color = 'var(--warning-dark)';
    display.textContent = `ℹ️ Excess Cash Discrepancy: + ₹${diff.toFixed(2)}`;
  }
}

/* 14. Voice Smart Cart */
function openVoiceBillingModal() {
  const modal = document.getElementById('voiceBillingModal');
  if (!modal) return;
  modal.classList.add('active');
  const recognitionAvailable = Boolean(window.SpeechRecognition || window.webkitSpeechRecognition);
  setVoiceStatus(recognitionAvailable
    ? 'Speak a product or use the typed command field.'
    : 'Speech recognition is unavailable here. Use the typed command field.');
  document.getElementById('voiceTranscriptInput')?.focus();
}
function closeVoiceBillingModal() {
  if (voiceRecognition) voiceRecognition.stop();
  const modal = document.getElementById('voiceBillingModal');
  if (modal) modal.classList.remove('active');
}

let voiceRecognition = null;
let pendingVoiceAction = null;

function setVoiceStatus(message) {
  const status = document.getElementById('voiceStatusText');
  if (status) status.textContent = message;
}

function toggleVoiceRecording() {
  const input = document.getElementById('voiceTranscriptInput');
  const btn = document.getElementById('btnMicTrigger');
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) {
    setVoiceStatus('Speech recognition is not supported by this browser. Type a command instead.');
    return;
  }

  try {
    voiceRecognition?.stop();
    voiceRecognition = new Recognition();
    voiceRecognition.lang = 'en-IN';
    voiceRecognition.continuous = false;
    voiceRecognition.interimResults = false;
    voiceRecognition.onstart = () => {
      if (btn) btn.setAttribute('aria-pressed', 'true');
      setVoiceStatus('Listening. Speak one command in English.');
    };
    voiceRecognition.onresult = event => {
      const transcript = event.results?.[0]?.[0]?.transcript?.trim() || '';
      if (input) input.value = transcript;
      setVoiceStatus(transcript ? `Recognized: ${transcript}` : 'No speech was recognized. Try again or type a command.');
    };
    voiceRecognition.onerror = event => {
      const message = event.error === 'not-allowed' || event.error === 'service-not-allowed'
        ? 'Microphone permission was denied. Allow access or type a command.'
        : `Speech recognition failed (${event.error}). You can type a command instead.`;
      setVoiceStatus(message);
    };
    voiceRecognition.onend = () => {
      if (btn) btn.setAttribute('aria-pressed', 'false');
    };
    voiceRecognition.start();
  } catch (error) {
    if (btn) btn.setAttribute('aria-pressed', 'false');
    setVoiceStatus('Could not start speech recognition. Type a command instead.');
  }
}

function parseVoiceNumber(value) {
  const normalized = value.toLowerCase();
  if (/^\d+$/.test(normalized)) return Number(normalized);
  return ({ one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10 })[normalized] || null;
}

function parseVoiceQuantityAndProduct(value) {
  const match = value.match(/^(\d+|one|two|three|four|five|six|seven|eight|nine|ten)(?:\s+(?:packets?|packs?|pieces?|items?|kilograms?|kilos?|kgs?|grams?|g|litres?|liters?|l))?(?:\s+of)?\s+(.+)$/i);
  if (!match) return { quantity: 1, product: value.trim() };
  return { quantity: parseVoiceNumber(match[1]), product: match[2].trim() };
}

function parseVoiceCommand(value) {
  const command = value.trim().toLowerCase().replace(/[.!?]+$/g, '').replace(/\s+/g, ' ');
  if (!command) return null;
  if (/^(show|view|open)( my)? cart$/.test(command)) return { intent: 'VIEW_CART' };
  if (/^(what is )?(my )?(cart )?(total|total bill)( please)?$/.test(command)
      || /^(what is|tell me) (my )?(total|total bill)( please)?$/.test(command)) return { intent: 'GET_TOTAL' };
  if (/^(clear|empty)( my)? cart$/.test(command)) return { intent: 'CLEAR_CART' };

  const search = command.match(/^(?:search(?: for)?|find)\s+(.+)$/);
  if (search) return { intent: 'SEARCH_PRODUCT', query: search[1].trim() };

  const remove = command.match(/^(?:remove|delete)\s+(.+?)(?:\s+from (?:my )?cart)?$/);
  if (remove) return { intent: 'REMOVE_PRODUCT', query: remove[1].trim() };

  const update = command.match(/^(?:set|change|update|increase|decrease)\s+(?:the\s+)?(?:quantity\s+of\s+)?(.+?)\s+to\s+(\d+|one|two|three|four|five|six|seven|eight|nine|ten)$/);
  if (update) return { intent: 'UPDATE_QUANTITY', query: update[1].trim(), quantity: parseVoiceNumber(update[2]) };

  const add = command.match(/^(?:add|put|include)\s+(.+)$/);
  if (add) {
    const parsed = parseVoiceQuantityAndProduct(add[1]);
    return { intent: 'ADD_PRODUCT', query: parsed.product, quantity: parsed.quantity };
  }

  return null;
}

function voiceCartProducts() {
  if (typeof cartMap !== 'undefined' && typeof customerProducts !== 'undefined') return customerProducts;
  return Array.isArray(state.products) ? state.products : [];
}

function findVoiceProductMatches(query) {
  const normalizedQuery = query.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  const words = normalizedQuery.split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  return voiceCartProducts().filter(product => {
    const searchable = `${product.name} ${product.brand || ''}`.toLowerCase().replace(/[^a-z0-9]+/g, ' ');
    return searchable.includes(normalizedQuery) || words.every(word => searchable.split(/\s+/).includes(word));
  });
}

function currentVoiceCart() {
  if (typeof cartMap !== 'undefined' && typeof customerProducts !== 'undefined') {
    return Array.from(cartMap.values());
  }
  return state.cart;
}

function setVoiceProductQuantity(product, quantity) {
  const customerCart = typeof cartMap !== 'undefined' && typeof customerProducts !== 'undefined';
  if (customerCart) {
    const current = cartMap.get(product.id);
    if (quantity > product.stock) throw new Error(`Only ${product.stock} units of ${product.name} are in stock.`);
    if (quantity <= 0) cartMap.delete(product.id);
    else cartMap.set(product.id, {
      id: product.id, name: product.name, price: Number(product.price), stock: product.stock,
      imagePath: product.imagePath, qty: quantity
    });
    updateCustomerCartSummary();
    return current;
  }

  const current = state.cart.find(item => item.id === product.id);
  if (quantity > product.stock) throw new Error(`Only ${product.stock} units of ${product.name} are in stock.`);
  if (quantity <= 0) state.cart = state.cart.filter(item => item.id !== product.id);
  else if (current) current.qty = quantity;
  else state.cart.push({ id: product.id, name: product.name, price: product.price, qty: quantity });
  renderCart();
  return current;
}

function renderVoiceProductChoices(products, action) {
  const container = document.getElementById('voiceCommandResults');
  if (!container) return;
  container.replaceChildren();
  const heading = document.createElement('p');
  heading.textContent = 'Choose the exact catalog item:';
  container.append(heading);
  products.forEach(product => {
    const button = document.createElement('button');
    button.className = 'btn btn-secondary';
    button.type = 'button';
    button.textContent = `${product.name} (${product.stock} in stock)`;
    button.addEventListener('click', () => {
      container.replaceChildren();
      applyVoiceProductAction(product, action);
    });
    container.append(button);
  });
}

function applyVoiceProductAction(product, action) {
  try {
    if (action.intent === 'REMOVE_PRODUCT') {
      if (!currentVoiceCart().some(item => item.id === product.id)) {
        setVoiceStatus(`${product.name} is not in the cart.`);
        return;
      }
      if (!window.confirm(`Remove ${product.name} from the cart?`)) return;
      setVoiceProductQuantity(product, 0);
      setVoiceStatus(`${product.name} was removed from the cart.`);
      return;
    }
    if (action.intent === 'UPDATE_QUANTITY') {
      if (!currentVoiceCart().some(item => item.id === product.id)) {
        setVoiceStatus(`${product.name} is not in the cart yet. Add it first.`);
        return;
      }
      setVoiceProductQuantity(product, action.quantity);
      setVoiceStatus(`${product.name} quantity set to ${action.quantity}.`);
      return;
    }
    const current = currentVoiceCart().find(item => item.id === product.id);
    const quantity = (current?.qty || 0) + action.quantity;
    setVoiceProductQuantity(product, quantity);
    setVoiceStatus(`Added ${action.quantity} ${product.name}${action.quantity === 1 ? '' : ' items'} to the cart.`);
  } catch (error) {
    setVoiceStatus(error.message);
  }
}

function processVoiceTranscriptToCart() {
  const input = document.getElementById('voiceTranscriptInput');
  const command = parseVoiceCommand(input?.value || '');
  const results = document.getElementById('voiceCommandResults');
  if (results) results.replaceChildren();
  pendingVoiceAction = null;
  if (!command) {
    setVoiceStatus('Command not recognized. Try “add two packets of milk” or type a supported command.');
    return;
  }

  if (command.intent === 'VIEW_CART' || command.intent === 'GET_TOTAL') {
    const cart = currentVoiceCart();
    if (command.intent === 'VIEW_CART') {
      setVoiceStatus(cart.length ? `Cart: ${cart.map(item => `${item.name} × ${item.qty}`).join(', ')}.` : 'Your cart is empty.');
    } else {
      const total = calculateCartTotals(cart).grandTotal;
      setVoiceStatus(`Current cart total: ₹${total.toFixed(2)}.`);
    }
    return;
  }

  if (command.intent === 'CLEAR_CART') {
    if (!currentVoiceCart().length) {
      setVoiceStatus('Your cart is already empty.');
      return;
    }
    if (!window.confirm('Clear all items from the current cart?')) return;
    if (typeof cartMap !== 'undefined' && typeof customerProducts !== 'undefined') {
      cartMap.clear();
      updateCustomerCartSummary();
    } else {
      state.cart = [];
      renderCart();
    }
    setVoiceStatus('The cart was cleared.');
    return;
  }

  if (command.intent === 'SEARCH_PRODUCT') {
    const searchInput = document.getElementById('posProductSearch') || document.getElementById('customerSearchInput');
    if (searchInput) {
      searchInput.value = command.query;
      searchInput.dispatchEvent(new Event('input', { bubbles: true }));
      setVoiceStatus(`Showing catalog results for “${command.query}”.`);
    } else {
      setVoiceStatus('Product search is unavailable on this page.');
    }
    return;
  }

  if (command.intent === 'UPDATE_QUANTITY' && command.quantity < 1) {
    setVoiceStatus('Choose a quantity of at least one. Use “remove” to remove an item.');
    return;
  }
  const matches = findVoiceProductMatches(command.query);
  if (!matches.length) {
    setVoiceStatus(`No catalog product matched “${command.query}”. Nothing was added.`);
    return;
  }
  pendingVoiceAction = command;
  if (matches.length > 1) {
    setVoiceStatus('Several catalog items match. Select the correct one to continue.');
    renderVoiceProductChoices(matches, command);
    return;
  }
  applyVoiceProductAction(matches[0], command);
}


/* 15. Product Photo Barcode Matching */
function openReceiptScannerModal() {
  const modal = document.getElementById('receiptScannerModal');
  if (modal) modal.classList.add('active');
}
function closeReceiptScannerModal() {
  const modal = document.getElementById('receiptScannerModal');
  if (modal) modal.classList.remove('active');
}

async function simulateReceiptScan(event) {
  const file = event.target.files[0];
  if (!file) return;

  const results = document.getElementById('receiptScanResults');
  if (!results) return;
  results.replaceChildren();
  results.hidden = false;

  const message = text => {
    results.replaceChildren();
    const paragraph = document.createElement('p');
    paragraph.setAttribute('role', 'status');
    paragraph.textContent = text;
    results.append(paragraph);
  };

  if (!file.type.startsWith('image/') || file.size > 5 * 1024 * 1024) {
    message('Choose an image file no larger than 5 MB.');
    event.target.value = '';
    return;
  }
  if (!window.BarcodeDetector || typeof createImageBitmap !== 'function') {
    message('This browser does not support photo barcode detection. Search for the product manually; nothing was added.');
    return;
  }

  message('Checking the photo for a product barcode…');
  let bitmap;
  try {
    const availableFormats = await window.BarcodeDetector.getSupportedFormats();
    const formats = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'code_39']
      .filter(format => availableFormats.includes(format));
    if (!formats.length) {
      message('This browser cannot decode supported product barcode formats. Search manually; nothing was added.');
      return;
    }
    bitmap = await createImageBitmap(file);
    const detector = new window.BarcodeDetector({ formats });
    const detections = await detector.detect(bitmap);
    const productIds = new Set();
    const catalog = voiceCartProducts();
    for (const detection of detections) {
      const product = catalog.find(item => item.barcode === detection.rawValue);
      if (product) productIds.add(product.id);
    }
    const products = catalog.filter(product => productIds.has(product.id));
    if (!products.length) {
      message(detections.length
        ? 'No detected barcode exactly matched the store catalog. Search manually; nothing was added.'
        : 'No barcode was visible in this photo. Try a clearer barcode photo or search manually. Nothing was added.');
      return;
    }
    results.replaceChildren();
    const heading = document.createElement('p');
    heading.textContent = 'Exact catalog matches. Select the product to add one unit:';
    results.append(heading);
    products.forEach(product => {
      const button = document.createElement('button');
      button.className = 'btn btn-secondary';
      button.type = 'button';
      button.textContent = `${product.name} · ${product.stock > 0 ? `${product.stock} in stock` : 'out of stock'}`;
      button.disabled = product.stock <= 0;
      button.addEventListener('click', () => {
        applyVoiceProductAction(product, { intent: 'ADD_PRODUCT', quantity: 1 });
      });
      results.append(button);
    });
  } catch (error) {
    message('Barcode detection failed. Try another image or use product search; nothing was added.');
  } finally {
    bitmap?.close();
  }
}

/* 16. Waste Entry Handler */
function openWasteModal() {
  const modal = document.getElementById('wasteModal');
  const select = document.getElementById('wasteProductSelect');
  if (!modal) return;

  if (select) {
    select.replaceChildren(...state.products.map(product => {
      const option = document.createElement('option');
      option.value = String(product.id);
      option.textContent = `${product.name} (Stock: ${product.stock})`;
      return option;
    }));
  }
  modal.classList.add('active');
}

function closeWasteModal() {
  const modal = document.getElementById('wasteModal');
  if (modal) modal.classList.remove('active');
}

document.addEventListener('DOMContentLoaded', () => {
  const wasteForm = document.getElementById('wasteForm');
  if (wasteForm) {
    wasteForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const prodId = Number.parseInt(document.getElementById('wasteProductSelect').value, 10);
      const formData = new FormData(wasteForm);
      const submitButton = wasteForm.querySelector('[type="submit"]');
      if (submitButton) submitButton.disabled = true;
      try {
        const waste = await apiRequest(`/api/owner/products/${prodId}/waste`, {
          method: 'POST',
          body: JSON.stringify({
            quantity: Number.parseInt(formData.get('quantity'), 10),
            reason: formData.get('reason'),
            notes: formData.get('notes')
          })
        });
        await loadBackendData();
        closeWasteModal();
        wasteForm.reset();
        showToast('Waste Logged', `Recorded ₹${Number(waste.loss).toFixed(2)} stock loss for ${waste.productName}.`, 'warning');
      } catch (error) {
        showToast('Waste Not Recorded', error.message, 'danger');
      } finally {
        if (submitButton) submitButton.disabled = false;
      }
    });
  }
});

/* 17. Festival & Event Planner */
function renderFestivalPlan() {
  const select = document.getElementById('festivalPlannerSelect');
  const results = document.getElementById('festivalPlanResults');
  if (!select || !results) return;

  const event = select.value;
  if (event === 'Diwali') {
    results.innerHTML = `
      <strong>Suggested Preparation Categories for Diwali:</strong><br>
      • Sweets & Dry Fruits (+40% stock)<br>
      • Cooking Oil & Whole Wheat Flour (+30% stock)<br>
      • Snacks & Beverages (+25% stock)
    `;
  } else if (event === 'Eid') {
    results.innerHTML = `
      <strong>Suggested Preparation Categories for Eid:</strong><br>
      • Basmati Rice & Spices (+45% stock)<br>
      • Milk & Dairy Desserts (+35% stock)<br>
      • Cold Drinks & Juices (+20% stock)
    `;
  } else if (event === 'Holi') {
    results.innerHTML = `
      <strong>Suggested Preparation Categories for Holi:</strong><br>
      • Gujiya Sweets & Maida Flour (+35% stock)<br>
      • Cold Beverages & Thandai (+30% stock)<br>
      • Snack Items (+20% stock)
    `;
  } else {
    results.innerHTML = `
      <strong>Suggested Preparation Categories for Christmas:</strong><br>
      • Bakery Cakes & Chocolates (+40% stock)<br>
      • Fruit Juices & Soft Drinks (+30% stock)<br>
      • Gift Packs (+25% stock)
    `;
  }
}

/* 18. Offline Backup & Restore */
function exportDataBackup() {
  const backupData = JSON.stringify(state, null, 2);
  const blob = new Blob([backupData], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `freshledger_database_backup_${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(url);
  showToast('Database Backup Exported', 'JSON backup file downloaded to your device offline.', 'success');
}

function importDataBackup(event) {
  const file = event.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const parsed = JSON.parse(e.target.result);
      if (parsed.products && parsed.customers) {
        showConfirmModal({
          title: 'Restore Database Backup?',
          message: 'Are you sure you want to replace current local database with this backup file? Existing data will be overwritten.',
          confirmText: 'Restore Backup',
          confirmClass: 'btn-danger',
          onConfirm: () => {
            state = parsed;
            saveState();
            renderAllViews();
            showToast('Backup Restored Successfully', 'Local database restored from JSON file.', 'success');
          }
        });
      } else {
        showToast('Invalid File Format', 'File is missing required database tables.', 'danger');
      }
    } catch (err) {
      showToast('Import Error', 'Failed to parse JSON backup file.', 'danger');
    }
  };
  reader.readAsText(file);
}

function initPOS() {
  const savedResumeBill = sessionStorage.getItem('freshledger_resume_bill');
  if (savedResumeBill) {
    try {
      pendingResumeBill = JSON.parse(savedResumeBill);
      state.cart = pendingResumeBill.items.map(item => ({ ...item }));
    } catch (error) {
      console.error('Held bill could not be restored.', error);
      sessionStorage.removeItem('freshledger_resume_bill');
      showToast('Held Bill Unavailable', 'The saved bill could not be read.', 'danger');
    }
  }
  renderCart();
  if (location.hash === '#posCustomerSelect') {
    document.getElementById('posCustomerSelect')?.focus();
  }

  const searchInput = document.getElementById('posProductSearch');
  if (searchInput) {
    searchInput.addEventListener('input', () => {
      renderPOSCatalog(activePOSCategory);
    });
  }

  const pointsInput = document.getElementById('posPointsToRedeem');
  if (pointsInput) pointsInput.addEventListener('input', updatePOSLoyaltyControls);
  updatePOSLoyaltyControls();

  document.querySelectorAll('.category-filter-pills .pill-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.category-filter-pills .pill-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      const cat = btn.textContent.trim();
      renderPOSCatalog(cat === 'All Items' ? 'All' : cat);
    });
  });

  const heldBillsBtn = document.getElementById('btnHeldBillsList');
  if (heldBillsBtn) {
    heldBillsBtn.addEventListener('click', showHeldBills);
  }

  const payBtn = document.getElementById('btnCompletePayment');
  if (payBtn) {
    payBtn.addEventListener('click', async () => {
      if (checkoutInProgress) return;
      if (state.cart.length === 0) {
        showToast('Cart Empty', 'Please add products before completing payment.', 'warning');
        return;
      }

      const baseTotal = calculateCartTotals(state.cart).grandTotal;
      const operationId = crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
      const customerUsername = document.getElementById('posCustomerSelect')?.value || 'Walk-in Retail';
      const pointsToRedeem = Number.parseInt(document.getElementById('posPointsToRedeem')?.value || '0', 10) || 0;
      const total = Math.max(0, baseTotal - pointsToRedeem * loyaltyValuePerPoint);
      const payRadio = document.querySelector('input[name="payType"]:checked');
      const payModeVal = payRadio ? payRadio.value : 'Cash';
      const cartItemsCopy = state.cart.map(item => ({ ...item }));

      checkoutInProgress = true;
      setPOSCheckoutBusy(true);
      try {
        if (payModeVal.includes('UPI') || payModeVal.includes('PhonePe')) {
          const confirmed = await triggerUpiCheckout(total, operationId, customerUsername, cartItemsCopy);
          if (!confirmed) return;
        }
        await executeSaleCheckout(total, operationId, customerUsername, payModeVal, cartItemsCopy, pointsToRedeem);
      } catch (error) {
        console.error('Sale checkout could not be started.', error);
        showToast('Payment Failed', error.message, 'danger');
      } finally {
        checkoutInProgress = false;
        setPOSCheckoutBusy(false);
      }
    });
  }

  const clearBtn = document.getElementById('btnClearBill');
  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      if (state.cart.length === 0) return;
      showConfirmModal({
        title: 'Clear Active Bill?',
        message: 'Are you sure you want to clear all items from the active bill? Current items will be removed.',
        confirmText: 'Clear Bill',
        confirmClass: 'btn-danger',
        onConfirm: () => {
          state.cart = [];
          renderCart();
          showToast('Bill Cleared', 'Active cart items removed.', 'warning');
        }
      });
    });
  }

  const holdBtn = document.getElementById('btnHoldBill');
  if (holdBtn) {
    holdBtn.addEventListener('click', () => {
      if (state.cart.length === 0) {
        showToast('Cart Empty', 'Add products before holding a bill.', 'warning');
        return;
      }
      const payMode = document.querySelector('input[name="payType"]:checked')?.value || 'Cash';
      const label = `Bill ${new Date().toLocaleString()}`;
      state.heldBills.unshift({
        id: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        label,
        items: state.cart.map(item => ({ ...item })),
        customerUsername: document.getElementById('posCustomerSelect')?.value || 'Walk-in Retail',
        paymentMode: payMode,
        heldAt: new Date().toISOString()
      });
      saveState();
      state.cart = [];
      resetPOSCustomerAndPayment();
      renderCart();
      renderHeldBillsDashboard();
      showToast('Bill Held', `${label} saved. You can resume it from Held Bills.`, 'success');
    });
  }

  const printBtn = document.getElementById('btnPrintInvoice');
  if (printBtn) {
    printBtn.addEventListener('click', () => {
      if (!lastCompletedSale) {
        showToast('No Completed Invoice', 'Complete a sale before printing its invoice.', 'warning');
        return;
      }
      printInvoiceBill(lastCompletedSale, lastCompletedSale.cartItems);
    });
  }

  const posWhatsAppBtn = document.getElementById('btnSendPosWhatsApp');
  if (posWhatsAppBtn) {
    posWhatsAppBtn.addEventListener('click', () => {
      if (!lastCompletedSale) {
        showToast('No Completed Invoice', 'Complete a sale before preparing a WhatsApp bill.', 'warning');
        return;
      }
      launchWhatsAppDirectly(lastCompletedSale, lastCompletedSale.cartItems);
    });
  }
}

function setPOSCheckoutBusy(busy) {
  const payButton = document.getElementById('btnCompletePayment');
  if (payButton) {
    if (checkoutButtonMarkup === null) checkoutButtonMarkup = payButton.innerHTML;
    payButton.disabled = busy;
    if (busy) payButton.textContent = 'Processing…';
    else payButton.innerHTML = checkoutButtonMarkup;
  }
  [
    '#posProductGrid',
    '#posCartItems',
    '#posProductSearch',
    '.category-filter-pills',
    '#posCustomerSelect',
    '#btnHoldBill',
    '#btnClearBill'
  ].forEach(selector => {
    const element = document.querySelector(selector);
    if (element) element.inert = busy;
  });
}

function resetPOSCustomerAndPayment() {
  const customerSelect = document.getElementById('posCustomerSelect');
  if (customerSelect) customerSelect.value = 'Walk-in Retail';
  const cashPayment = document.querySelector('input[name="payType"][value="Cash"]');
  if (cashPayment) cashPayment.checked = true;
  const pointsInput = document.getElementById('posPointsToRedeem');
  if (pointsInput) pointsInput.value = '0';
  updatePOSLoyaltyControls();
}

/* 7. Product Form Handler */
function initProductForm() {
  const form = document.getElementById('productForm');
  if (!form) return;

  const imageInput = form.elements.productImage;
  const preview = document.getElementById('productImagePreview');
  const refreshSuggestedPreview = () => {
    if (imageInput.files.length || productImageRemovalPending || !preview) return;
    preview.src = suggestedProductImage({
      name: form.elements.name.value,
      category: form.elements.category.value
    });
  };
  imageInput.addEventListener('change', () => {
    const file = imageInput.files[0];
    clearProductImagePreviewUrl();
    if (!file) {
      refreshSuggestedPreview();
      return;
    }
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      imageInput.value = '';
      showToast('Image Not Accepted', 'Choose a PNG or JPEG image no larger than 5 MB.', 'warning');
      refreshSuggestedPreview();
      return;
    }
    productImageRemovalPending = false;
    selectedProductImagePreviewUrl = URL.createObjectURL(file);
    preview.src = selectedProductImagePreviewUrl;
  });
  form.elements.name.addEventListener('input', refreshSuggestedPreview);
  form.elements.category.addEventListener('change', refreshSuggestedPreview);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const formData = new FormData(form);

    const name = formData.get('name');
    const category = formData.get('category') || 'Grocery';
    const brand = formData.get('brand') || 'Local';
    const barcode = formData.get('barcode') || ('890' + Math.floor(1000000000 + Math.random() * 9000000000));
    const purchasePrice = parseFloat(formData.get('purchasePrice')) || 0;
    const price = parseFloat(formData.get('sellingPrice')) || 0;
    const stock = parseInt(formData.get('openingStock')) || 0;
    const minStock = parseInt(formData.get('minStock')) || 5;
    const imageFile = formData.get('productImage');
    const submitButton = document.getElementById('productFormSubmit');
    submitButton.disabled = true;

    try {
      const product = await apiRequest(
        editingProductId === null ? '/api/owner/products' : `/api/owner/products/${editingProductId}`,
        {
          method: editingProductId === null ? 'POST' : 'PUT',
          body: JSON.stringify({ name, category, brand, barcode, purchasePrice, price, stock, minStock })
        }
      );
      if (imageFile instanceof File && imageFile.size > 0) {
        const imageData = new FormData();
        imageData.append('image', imageFile);
        await apiRequest(`/api/owner/products/${product.id}/image`, { method: 'POST', body: imageData });
      } else if (productImageRemovalPending && editingProductId !== null) {
        await apiRequest(`/api/owner/products/${product.id}/image`, { method: 'DELETE' });
      }
      await loadBackendData();
      form.reset();
      clearProductImagePreviewUrl();
      editingProductId = null;
      productImageRemovalPending = false;
      closeProductModal();
      showToast('Product Saved', `"${name}" saved to the shared product catalog.`, 'success');
    } catch (error) {
      console.error('Product could not be saved.', error);
      showToast('Product Save Failed', error.message, 'danger');
    } finally {
      submitButton.disabled = false;
    }
  });
}

/* 8. Customer Form Handler */
function initCustomerForm() {
  const form = document.getElementById('customerForm');
  if (!form) return;

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const formData = new FormData(form);

    const name = formData.get('name');
    const phone = formData.get('phone');
    const email = formData.get('email') || `${name.toLowerCase().replace(/\s+/g, '.')}@gmail.com`;
    const points = parseInt(formData.get('points')) || 50;
    const credit = parseFloat(formData.get('credit')) || 0.00;

    const newCustomer = {
      id: state.customers.length ? Math.max(...state.customers.map(c => c.id)) + 1 : 1,
      name,
      phone,
      email,
      points,
      credit,
      lastVisit: 'Just Now'
    };

    state.customers.unshift(newCustomer);
    saveState();
    renderAllViews();
    form.reset();

    closeCustomerModal();
    showToast('Customer Account Saved', `Customer "${name}" registered successfully.`, 'success');
  });
}

/* 9. Supplier Form Handler */
function initSupplierForm() {
  const form = document.getElementById('supplierForm');
  if (!form) return;

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const formData = new FormData(form);

    const company = formData.get('company');
    const contactPerson = formData.get('contactPerson');
    const phone = formData.get('phone');
    const category = formData.get('category') || 'Grocery Staples';

    const newSupplier = {
      id: state.suppliers.length ? Math.max(...state.suppliers.map(s => s.id)) + 1 : 1,
      company,
      contactPerson,
      phone,
      email: `orders@${company.toLowerCase().replace(/[^a-z0-9]/g, '')}.com`,
      category,
      pendingOrders: 0
    };

    state.suppliers.unshift(newSupplier);
    saveState();
    renderAllViews();
    form.reset();

    closeSupplierModal();
    showToast('Supplier Saved', `Supplier "${company}" added to vendor list.`, 'success');
  });
}

/* 10. Data Table Renderers */
function renderProductsTable() {
  const tbody = document.getElementById('productsTableBody');
  if (!tbody) return;

  tbody.innerHTML = state.products.map(p => {
    const badgeClass = p.status === 'In Stock' ? 'badge-success' : (p.status === 'Out of Stock' ? 'badge-danger' : 'badge-warning');
    return `
      <tr>
        <td><div class="product-table-name">${productImageMarkup(p)}<div><strong>${escapeHtml(p.name)}</strong><br><small style="color:var(--text-muted);">${escapeHtml(p.barcode)}</small></div></div></td>
        <td>${escapeHtml(p.category)}</td>
        <td>${escapeHtml(p.brand)}</td>
        <td>₹${p.purchasePrice.toFixed(2)}</td>
        <td><strong>₹${p.price.toFixed(2)}</strong></td>
        <td><strong>${p.stock} units</strong></td>
        <td><span class="badge ${badgeClass}">${p.status}</span></td>
        <td>
          <button class="btn btn-ghost" style="padding:4px 8px;font-size:0.8rem;" type="button" onclick="openEditProductModal(${p.id})">Edit</button>
          <button class="btn btn-ghost" style="padding:4px 8px;font-size:0.8rem;" type="button" onclick="deleteProduct(${p.id})">🗑 Delete</button>
        </td>
      </tr>
    `;
  }).join('');
}

function deleteProduct(id) {
  const prod = state.products.find(p => p.id === id);
  if (!prod) return;

  showConfirmModal({
    title: `Delete Product?`,
    message: `Are you sure you want to delete "${prod.name}"? This action cannot be undone.`,
    confirmText: 'Delete Product',
    confirmClass: 'btn-danger',
    onConfirm: async () => {
      try {
        await apiRequest(`/api/owner/products/${id}`, { method: 'DELETE' });
        await loadBackendData();
        showToast('Product Deleted', `${prod.name} removed from the shared catalog.`, 'warning');
      } catch (error) {
        console.error('Product could not be deleted.', error);
        showToast('Delete Failed', error.message, 'danger');
      }
    }
  });
}

function renderInventoryTable() {
  const tbody = document.getElementById('inventoryTableBody');
  if (!tbody) return;

  tbody.innerHTML = state.products.map((p, idx) => {
    const shelf = `Shelf ${String.fromCharCode(65 + (idx % 4))}${(idx % 5) + 1}`;
    const badgeClass = p.stock <= p.minStock ? 'badge-warning' : 'badge-success';
    return `
      <tr>
        <td><div class="product-table-name">${productImageMarkup(p)}<div><strong>${escapeHtml(p.name)}</strong><br><small style="color:var(--text-muted);">${escapeHtml(p.category)}</small></div></div></td>
        <td><span class="badge badge-info">${shelf}</span></td>
        <td><strong>${p.stock} units</strong></td>
        <td>${p.minStock} units</td>
        <td><span class="badge ${badgeClass}">${p.stock <= p.minStock ? 'Reorder Needed' : 'Sufficient'}</span></td>
        <td>
          <button class="btn btn-secondary" style="padding:4px 8px;font-size:0.78rem;" type="button" onclick="restockItem(${p.id})">+ Restock (+10)</button>
        </td>
      </tr>
    `;
  }).join('');
}

function restockItem(id) {
  const prod = state.products.find(p => p.id === id);
  if (!prod) return;
  apiRequest(`/api/owner/products/${id}/restock`, {
    method: 'POST',
    body: JSON.stringify({ quantity: 10 })
  }).then(async () => {
    await loadBackendData();
    showToast('Stock Replenished', `Added +10 units to ${prod.name}.`, 'success');
  }).catch(error => {
    console.error('Stock could not be replenished.', error);
    showToast('Restock Failed', error.message, 'danger');
  });
}

function renderCustomersTable() {
  const tbody = document.getElementById('customersTableBody');
  if (!tbody) return;

  tbody.replaceChildren();
  state.customers.forEach(customer => {
    const row = document.createElement('tr');
    const name = document.createElement('td');
    const strong = document.createElement('strong');
    strong.textContent = customer.name;
    name.append(strong);
    const phone = document.createElement('td');
    phone.textContent = customer.phone || '';
    const email = document.createElement('td');
    email.textContent = customer.email || '';
    const points = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = 'badge badge-info';
    badge.textContent = `${customer.points} pts`;
    points.append(badge);
    row.append(name, phone, email, points);
    tbody.append(row);
  });
}

function renderSuppliersTable() {
  const tbody = document.getElementById('suppliersTableBody');
  if (!tbody) return;

  tbody.innerHTML = state.suppliers.map(s => `
    <tr>
      <td><strong>${s.company}</strong></td>
      <td>${s.contactPerson}</td>
      <td>${s.phone}<br><small style="color:var(--text-muted);">${s.email}</small></td>
      <td><span class="badge badge-info">${s.category}</span></td>
      <td><strong>${s.pendingOrders} orders</strong></td>
    </tr>
  `).join('');
}

function renderPurchasesTable() {
  const tbody = document.getElementById('purchasesTableBody');
  if (!tbody) return;

  tbody.innerHTML = state.purchases.map(p => {
    const badge = p.status === 'Received' ? 'badge-success' : 'badge-warning';
    return `
      <tr>
        <td><strong>${p.id}</strong></td>
        <td>${p.supplier}</td>
        <td>${p.date}</td>
        <td>${p.itemsCount} items</td>
        <td><strong>₹${p.totalAmount.toFixed(2)}</strong></td>
        <td><span class="badge ${badge}">${p.status}</span></td>
      </tr>
    `;
  }).join('');
}

function renderSalesTable() {
  const tbody = document.getElementById('salesTableBody');
  if (!tbody) return;

  tbody.innerHTML = state.sales.map(s => `
    <tr>
      <td><strong>${s.invoice}</strong></td>
      <td>${s.date}</td>
      <td>${s.customer}</td>
      <td><span class="badge badge-info">${s.paymentMode}</span></td>
      <td>${s.cashier}</td>
      <td><strong>₹${s.amount.toFixed(2)}</strong></td>
      <td><span class="badge badge-success">${s.status}</span></td>
      <td>
        <button class="btn btn-secondary" style="padding:3px 8px;font-size:0.78rem;background:#128c7e;color:white;border-color:#128c7e;" type="button" onclick="sendWhatsAppBillForInvoice('${s.invoice}')">💬 WhatsApp</button>
      </td>
    </tr>
  `).join('');
}

function renderExpensesTable() {
  const tbody = document.getElementById('expensesTableBody');
  if (!tbody) return;

  tbody.innerHTML = state.expenses.map(e => `
    <tr>
      <td><strong>${e.date}</strong></td>
      <td><span class="badge badge-warning">${e.category}</span></td>
      <td>${e.description}</td>
      <td>${e.method}</td>
      <td><strong style="color:var(--danger);">₹${e.amount.toFixed(2)}</strong></td>
    </tr>
  `).join('');
}

function renderAlerts() {
  const container = document.getElementById('alertsContainer');
  if (!container) return;

  const lowStock = state.products.filter(p => p.stock <= p.minStock);

  container.replaceChildren();
  const stockCard = document.createElement('section');
  stockCard.className = 'card';
  const stockHeading = document.createElement('h3');
  stockHeading.className = 'card-title';
  stockHeading.textContent = `Low Stock Alerts (${lowStock.length})`;
  stockCard.append(stockHeading);
  lowStock.forEach(product => {
    const row = document.createElement('div');
    row.style.cssText = 'display:flex;align-items:center;justify-content:space-between;padding:10px;margin-top:8px;background:var(--warning-bg);border:1px solid var(--warning-border);border-radius:var(--radius-md);';
    const description = document.createElement('div');
    description.style.cssText = 'display:flex;align-items:center;gap:10px;min-width:0;';
    const image = document.createElement('img');
    image.src = productImageMarkupUrl(product);
    image.alt = '';
    image.loading = 'lazy';
    image.className = 'product-thumb';
    image.onerror = () => { image.onerror = null; image.src = '/images/products/placeholder.svg'; };
    const label = document.createElement('span');
    label.textContent = `${product.name} (${product.category}) - Only ${product.stock} left (Min: ${product.minStock})`;
    description.append(image, label);
    const restock = document.createElement('button');
    restock.className = 'btn btn-secondary';
    restock.type = 'button';
    restock.textContent = '+ Restock';
    restock.addEventListener('click', () => restockItem(product.id));
    row.append(description, restock);
    stockCard.append(row);
  });

  const expiryCard = document.createElement('section');
  expiryCard.className = 'card';
  const expiryHeading = document.createElement('h3');
  expiryHeading.className = 'card-title';
  expiryHeading.textContent = 'Expiry alerts';
  const expiryMessage = document.createElement('p');
  expiryMessage.textContent = 'Expiry dates are not recorded for products, so expiry alerts are unavailable.';
  expiryCard.append(expiryHeading, expiryMessage);
  container.append(stockCard, expiryCard);
}

/* 11. Shared Toast & Modal System */
function showToast(title, message, type = 'success') {
  let container = document.getElementById('toastContainer');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toastContainer';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  const iconSymbol = type === 'success' ? '✔' : (type === 'warning' ? '⚠️' : (type === 'danger' ? '✕' : 'ℹ️'));

  toast.innerHTML = `
    <div style="font-weight:bold;font-size:1.1rem">${iconSymbol}</div>
    <div style="flex:1">
      <div class="toast-title">${title}</div>
      <div class="toast-message">${message}</div>
    </div>
    <button style="background:none;border:none;cursor:pointer;color:#888;font-size:1.1rem" onclick="this.parentElement.remove()">✕</button>
  `;

  container.appendChild(toast);

  setTimeout(() => {
    if (toast.parentElement) {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }
  }, 4000);
}

function showConfirmModal({ title, message, confirmText = 'Confirm', cancelText = 'Cancel', confirmClass = 'btn-danger', onConfirm }) {
  let modal = document.getElementById('confirmModal');

  if (!modal) {
    modal = document.createElement('div');
    modal.id = 'confirmModal';
    modal.className = 'modal-backdrop';
    modal.setAttribute('role', 'dialog');
    modal.setAttribute('aria-modal', 'true');
    modal.innerHTML = `
      <div class="modal-card" role="document">
        <div class="modal-header">
          <h3 id="confirmModalTitle" class="modal-title">Confirm Action</h3>
          <button id="closeModalBtn" class="modal-close-btn" aria-label="Close" type="button">&times;</button>
        </div>
        <div class="modal-body">
          <p id="confirmModalMessage" style="font-size:0.95rem;color:var(--text-secondary);line-height:1.6;">Are you sure you want to proceed?</p>
        </div>
        <div class="modal-footer">
          <button id="cancelModalBtn" class="btn btn-secondary" type="button">Cancel</button>
          <button id="confirmModalBtn" class="btn btn-danger" type="button">Confirm</button>
        </div>
      </div>
    `;
    document.body.appendChild(modal);
  }

  const titleEl = document.getElementById('confirmModalTitle');
  const messageEl = document.getElementById('confirmModalMessage');
  const confirmBtn = document.getElementById('confirmModalBtn');
  const cancelBtn = document.getElementById('cancelModalBtn');
  const closeBtn = document.getElementById('closeModalBtn');

  if (titleEl) titleEl.textContent = title;
  if (messageEl) messageEl.textContent = message;
  if (confirmBtn) {
    confirmBtn.textContent = confirmText;
    confirmBtn.className = `btn ${confirmClass}`;
  }
  if (cancelBtn) cancelBtn.textContent = cancelText;

  modal.classList.add('active');

  const cleanup = () => {
    modal.classList.remove('active');
    if (confirmBtn) confirmBtn.onclick = null;
    if (cancelBtn) cancelBtn.onclick = null;
    if (closeBtn) closeBtn.onclick = null;
  };

  if (cancelBtn) cancelBtn.onclick = cleanup;
  if (closeBtn) closeBtn.onclick = cleanup;

  if (confirmBtn) {
    confirmBtn.onclick = () => {
      cleanup();
      if (typeof onConfirm === 'function') onConfirm();
    };
  }
}
