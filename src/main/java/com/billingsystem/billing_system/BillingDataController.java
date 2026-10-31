package com.billingsystem.billing_system;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class BillingDataController {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.08");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.05");

    private final JdbcTemplate jdbc;
    private final ProductImageService productImages;

    public BillingDataController(JdbcTemplate jdbc, ProductImageService productImages) {
        this.jdbc = jdbc;
        this.productImages = productImages;
    }

    @GetMapping("/products")
    public List<ProductView> products() {
        return jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE active = TRUE ORDER BY name
                """, PRODUCT_MAPPER);
    }

    @GetMapping("/customers")
    public List<CustomerView> customers() {
        return jdbc.query("""
                SELECT username, display_name, email, phone, loyalty_points
                FROM app_users WHERE role = 'CUSTOMER' ORDER BY display_name
                """, (rs, row) -> new CustomerView(rs.getString("username"), rs.getString("display_name"),
                rs.getString("phone"), rs.getString("email"), rs.getInt("loyalty_points")));
    }

    @GetMapping("/owner/dashboard")
    @PreAuthorize("hasRole('OWNER')")
    public OwnerDashboardView ownerDashboard() {
        BigDecimal salesToday = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total), 0) FROM transactions
                WHERE transaction_type = 'SALE' AND CAST(created_at AS DATE) = CURRENT_DATE
                """, BigDecimal.class);
        long billsToday = jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions
                WHERE transaction_type = 'SALE' AND CAST(created_at AS DATE) = CURRENT_DATE
                """, Long.class);
        BigDecimal inventoryValue = jdbc.queryForObject("""
                SELECT COALESCE(SUM(purchase_price * stock), 0) FROM products WHERE active = TRUE
                """, BigDecimal.class);
        long activeProducts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE active = TRUE", Long.class);
        long lowStockProducts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM products WHERE active = TRUE AND stock <= min_stock
                """, Long.class);
        return new OwnerDashboardView(salesToday, billsToday, inventoryValue, activeProducts, lowStockProducts);
    }

    @PostMapping("/owner/products")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView createProduct(@RequestBody ProductRequest request) {
        validateProduct(request);
        try {
            jdbc.update("""
                    INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, request.name().trim(), request.category().trim(), defaultText(request.brand(), "Local"),
                    request.barcode().trim(), money(request.purchasePrice()), money(request.price()),
                    request.stock(), request.minStock(), ProductImageLibrary.match(request.name(), request.category()));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with this barcode already exists.");
        }
        return jdbc.queryForObject("SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path FROM products WHERE barcode = ?",
                PRODUCT_MAPPER, request.barcode().trim());
    }

    @PutMapping("/owner/products/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView updateProduct(@PathVariable long id, @RequestBody ProductRequest request) {
        validateProduct(request);
        try {
            int updated = jdbc.update("""
                    UPDATE products SET name = ?, category = ?, brand = ?, barcode = ?, purchase_price = ?,
                        price = ?, stock = ?, min_stock = ?, active = TRUE,
                        image_path = CASE WHEN image_path IS NULL OR image_path NOT LIKE '/product-images/%'
                            THEN ? ELSE image_path END
                    WHERE id = ?
                    """, request.name().trim(), request.category().trim(), defaultText(request.brand(), "Local"),
                    request.barcode().trim(), money(request.purchasePrice()), money(request.price()),
                    request.stock(), request.minStock(), ProductImageLibrary.match(request.name(), request.category()), id);
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with this barcode already exists.");
        }
        return productById(id);
    }

    @PostMapping("/owner/products/{id}/image")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView uploadProductImage(@PathVariable long id, @RequestParam("image") MultipartFile image) {
        String path = productImages.store(image);
        List<String> previousPaths = jdbc.query(
                "SELECT image_path FROM products WHERE id = ? AND active = TRUE",
                (rs, row) -> rs.getString(1), id);
        if (previousPaths.isEmpty()) {
            productImages.deleteManagedImage(path);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        if (jdbc.update("UPDATE products SET image_path = ? WHERE id = ? AND active = TRUE", path, id) == 0) {
            productImages.deleteManagedImage(path);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        productImages.deleteManagedImage(previousPaths.get(0));
        return productById(id);
    }

    @DeleteMapping("/owner/products/{id}/image")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView removeProductImage(@PathVariable long id) {
        List<String> previousPaths = jdbc.query(
                "SELECT image_path FROM products WHERE id = ? AND active = TRUE",
                (rs, row) -> rs.getString(1), id);
        if (previousPaths.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        jdbc.update("UPDATE products SET image_path = ? WHERE id = ? AND active = TRUE",
                ProductImageLibrary.image("placeholder"), id);
        productImages.deleteManagedImage(previousPaths.get(0));
        return productById(id);
    }

    @DeleteMapping("/owner/products/{id}")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable long id) {
        if (jdbc.update("UPDATE products SET active = FALSE WHERE id = ? AND active = TRUE", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
    }

    @PostMapping("/owner/products/{id}/restock")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView restock(@PathVariable long id, @RequestBody RestockRequest request) {
        if (request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restock quantity must be positive.");
        }
        if (jdbc.update("UPDATE products SET stock = stock + ? WHERE id = ? AND active = TRUE",
                request.quantity(), id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        return productById(id);
    }

    @GetMapping("/sales")
    @PreAuthorize("hasRole('OWNER')")
    public List<TransactionView> sales() {
        return listTransactions("SALE", null);
    }

    @GetMapping("/sales/export")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<String> exportSales() {
        StringBuilder csv = new StringBuilder("Invoice,Date,Customer,Payment mode,Total,Status\r\n");
        for (TransactionView sale : listTransactions("SALE", null)) {
            csv.append(csvValue(sale.invoice())).append(',')
                    .append(csvValue(sale.date().toString())).append(',')
                    .append(csvValue(sale.customer())).append(',')
                    .append(csvValue(sale.paymentMode())).append(',')
                    .append(csvValue(sale.amount().toPlainString())).append(',')
                    .append(csvValue(sale.status())).append("\r\n");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales.csv\"")
                .body(csv.toString());
    }

    @GetMapping("/sales/{invoice}")
    @PreAuthorize("hasAnyRole('OWNER', 'CASHIER')")
    public TransactionView sale(@PathVariable String invoice, Authentication authentication) {
        boolean owner = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        String sql = """
                SELECT id, invoice, transaction_type, customer_username, customer_name, cashier_username,
                    payment_mode, subtotal, discount, tax, total, status, created_at
                FROM transactions WHERE invoice = ? AND transaction_type = 'SALE'
                """ + (owner ? "" : " AND cashier_username = ?");
        List<TransactionHeader> matches = owner
                ? jdbc.query(sql, HEADER_MAPPER, invoice)
                : jdbc.query(sql, HEADER_MAPPER, invoice, authentication.getName());
        return matches.stream().findFirst().map(this::withItems)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found."));
    }

    @GetMapping("/owner/orders")
    @PreAuthorize("hasRole('OWNER')")
    public List<TransactionView> ownerOrders() {
        return listTransactions("ORDER", null);
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<TransactionView> customerOrders(Principal principal) {
        return listTransactions("ORDER", principal.getName());
    }

    @GetMapping("/customer/bills")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<TransactionView> customerBills(Principal principal) {
        return listTransactions("SALE", principal.getName());
    }

    @GetMapping("/customer/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerView customerProfile(Principal principal) {
        return jdbc.query("""
                SELECT username, display_name, phone, email, loyalty_points
                FROM app_users WHERE username = ? AND role = 'CUSTOMER'
                """, (rs, row) -> new CustomerView(rs.getString("username"), rs.getString("display_name"),
                rs.getString("phone"), rs.getString("email"), rs.getInt("loyalty_points")),
                principal.getName()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile not found."));
    }

    @PostMapping("/sales")
    @PreAuthorize("hasAnyRole('OWNER', 'CASHIER')")
    @Transactional
    public TransactionView completeSale(@RequestBody CheckoutRequest request, Principal principal) {
        return createTransaction(request, principal, "SALE");
    }

    @PostMapping("/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public TransactionView placeOrder(@RequestBody CheckoutRequest request, Principal principal) {
        return createTransaction(request, principal, "ORDER");
    }

    private TransactionView createTransaction(CheckoutRequest request, Principal principal, String type) {
        if (request == null || request.operationId() == null || request.operationId().isBlank()
                || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An operation ID and at least one item are required.");
        }

        TransactionView previous = findByOperationId(request.operationId());
        if (previous != null) {
            boolean sameOwner = type.equals(previous.type())
                    && (type.equals("ORDER")
                    ? principal.getName().equals(previous.customerUsername())
                    : principal.getName().equals(previous.cashier()));
            if (!sameOwner) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This operation ID has already been used.");
            }
            return previous;
        }

        String customerUsername = type.equals("ORDER") ? principal.getName() : request.customerUsername();
        String customerName = customerUsername == null || customerUsername.isBlank()
                ? "Walk-in Retail" : lookupCustomerName(customerUsername);
        String paymentMode = type.equals("ORDER") ? "Online order" : defaultText(request.paymentMode(), "Cash");
        String cashier = type.equals("ORDER") ? principal.getName() : principal.getName();

        BigDecimal subtotal = ZERO;
        List<PricedItem> pricedItems = new java.util.ArrayList<>();
        Map<Long, Long> requestedQuantities = new LinkedHashMap<>();
        for (CheckoutItem item : request.items()) {
            if (item == null || item.productId() <= 0 || item.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product IDs and quantities must be positive.");
            }
            long combinedQuantity = requestedQuantities.getOrDefault(item.productId(), 0L) + item.quantity();
            if (combinedQuantity > Integer.MAX_VALUE) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combined product quantity is too large.");
            }
            requestedQuantities.put(item.productId(), combinedQuantity);
        }

        for (Map.Entry<Long, Long> requestedItem : requestedQuantities.entrySet()) {
            int quantity = requestedItem.getValue().intValue();
            ProductView product = jdbc.query("""
                    SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                    FROM products WHERE id = ? AND active = TRUE FOR UPDATE
                    """, PRODUCT_MAPPER, requestedItem.getKey()).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "A requested product is unavailable."));
            if (product.stock() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        product.name() + " has only " + product.stock() + " units remaining.");
            }
            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            pricedItems.add(new PricedItem(product, quantity, lineTotal));
        }

        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = subtotal.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);

        if (Boolean.TRUE.equals(request.redeemLoyaltyPoints()) && type.equals("ORDER") && customerUsername != null) {
            Integer currentPoints = jdbc.queryForObject(
                    "SELECT loyalty_points FROM app_users WHERE username = ?", Integer.class, customerUsername);
            if (currentPoints != null && currentPoints > 0) {
                // 10 loyalty points = ₹1.00 discount
                BigDecimal maxPointValue = BigDecimal.valueOf(currentPoints).divide(BigDecimal.valueOf(10), 2, RoundingMode.FLOOR);
                BigDecimal remainingSubtotal = subtotal.subtract(discount);
                BigDecimal pointsDiscount = maxPointValue.min(remainingSubtotal);
                if (pointsDiscount.compareTo(ZERO) > 0) {
                    int pointsToDeduct = pointsDiscount.multiply(BigDecimal.valueOf(10)).intValue();
                    jdbc.update("UPDATE app_users SET loyalty_points = loyalty_points - ? WHERE username = ?",
                            pointsToDeduct, customerUsername);
                    discount = discount.add(pointsDiscount);
                }
            }
        }

        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(ZERO) < 0) {
            total = ZERO;
        }
        String invoice = (type.equals("ORDER") ? "ORD-" : "INV-") + UUID.randomUUID();
        String status = type.equals("ORDER") ? "PLACED" : "COMPLETED";

        jdbc.update("""
                INSERT INTO transactions (operation_id, invoice, transaction_type, customer_username, customer_name,
                    cashier_username, payment_mode, subtotal, discount, tax, total, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.operationId(), invoice, type, customerUsername, customerName, cashier, paymentMode,
                subtotal, discount, tax, total, status);
        long transactionId = jdbc.queryForObject("SELECT id FROM transactions WHERE operation_id = ?",
                Long.class, request.operationId());

        for (PricedItem item : pricedItems) {
            jdbc.update("""
                    INSERT INTO transaction_items (transaction_id, product_id, product_name, unit_price, quantity, line_total)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, transactionId, item.product().id(), item.product().name(), item.product().price(),
                    item.quantity(), item.lineTotal());
            jdbc.update("UPDATE products SET stock = stock - ? WHERE id = ?", item.quantity(), item.product().id());
        }

        if (type.equals("ORDER") && customerUsername != null) {
            int earnedPoints = total.divide(BigDecimal.valueOf(10), 0, RoundingMode.FLOOR).intValue();
            if (earnedPoints > 0) {
                jdbc.update("UPDATE app_users SET loyalty_points = loyalty_points + ? WHERE username = ?",
                        earnedPoints, customerUsername);
            }
        }

        return transactionById(transactionId);
    }

    private List<TransactionView> listTransactions(String type, String customerUsername) {
        String sql = """
                SELECT id, invoice, transaction_type, customer_username, customer_name, cashier_username,
                    payment_mode, subtotal, discount, tax, total, status, created_at
                FROM transactions WHERE transaction_type = ?
                """ + (customerUsername == null ? "" : " AND customer_username = ?") + " ORDER BY created_at DESC";
        List<TransactionHeader> headers = customerUsername == null
                ? jdbc.query(sql, HEADER_MAPPER, type)
                : jdbc.query(sql, HEADER_MAPPER, type, customerUsername);
        return headers.stream().map(this::withItems).toList();
    }

    private TransactionView findByOperationId(String operationId) {
        List<TransactionHeader> matches = jdbc.query("""
                SELECT id, invoice, transaction_type, customer_username, customer_name, cashier_username,
                    payment_mode, subtotal, discount, tax, total, status, created_at
                FROM transactions WHERE operation_id = ?
                """, HEADER_MAPPER, operationId);
        return matches.isEmpty() ? null : withItems(matches.get(0));
    }

    private TransactionView transactionById(long id) {
        TransactionHeader header = jdbc.queryForObject("""
                SELECT id, invoice, transaction_type, customer_username, customer_name, cashier_username,
                    payment_mode, subtotal, discount, tax, total, status, created_at
                FROM transactions WHERE id = ?
                """, HEADER_MAPPER, id);
        return withItems(header);
    }

    private TransactionView withItems(TransactionHeader header) {
        List<TransactionItemView> items = jdbc.query("""
                SELECT product_id, product_name, unit_price, quantity, line_total
                FROM transaction_items WHERE transaction_id = ? ORDER BY id
                """, (rs, row) -> new TransactionItemView(rs.getLong("product_id"), rs.getString("product_name"),
                rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total")), header.id());
        return new TransactionView(header.invoice(), header.type(), header.customerUsername(), header.customerName(),
                header.cashier(), header.paymentMode(), header.subtotal(), header.discount(), header.tax(),
                header.total(), header.status(), header.createdAt(), items);
    }

    private ProductView productById(long id) {
        return jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE id = ? AND active = TRUE
                """, PRODUCT_MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    private String lookupCustomerName(String username) {
        List<String> names = jdbc.query("SELECT display_name FROM app_users WHERE username = ? AND role = 'CUSTOMER'",
                (rs, row) -> rs.getString(1), username);
        if (!names.isEmpty()) {
            return names.get(0);
        }
        if (username.equals("Walk-in Retail")) {
            return username;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected customer account was not found.");
    }

    private static void validateProduct(ProductRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()
                || request.category() == null || request.category().isBlank()
                || request.barcode() == null || request.barcode().isBlank()
                || request.stock() < 0 || request.minStock() < 0
                || request.price() == null || request.price().signum() < 0
                || request.purchasePrice() == null || request.purchasePrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product details, prices, and stock must be valid.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record PricedItem(ProductView product, int quantity, BigDecimal lineTotal) {}

    public record ProductRequest(String name, String category, String brand, String barcode,
                                 BigDecimal purchasePrice, BigDecimal price, int stock, int minStock) {}
    public record OwnerDashboardView(BigDecimal salesToday, long billsToday, BigDecimal inventoryValue,
                                     long activeProducts, long lowStockProducts) {}
    public record RestockRequest(int quantity) {}
    public record CheckoutItem(long productId, int quantity) {}
    public record CheckoutRequest(String operationId, String customerUsername, String paymentMode,
                                  List<CheckoutItem> items, Boolean redeemLoyaltyPoints) {
        public CheckoutRequest(String operationId, String customerUsername, String paymentMode, List<CheckoutItem> items) {
            this(operationId, customerUsername, paymentMode, items, false);
        }
    }
    public record ProductView(long id, String name, String category, String brand, String barcode,
                              BigDecimal purchasePrice, BigDecimal price, int stock, int minStock,
                              String status, String imagePath) {}
    public record CustomerView(String username, String name, String phone, String email, int points) {}
    public record TransactionItemView(long productId, String name, BigDecimal price, int quantity, BigDecimal lineTotal) {}
    public record TransactionView(String invoice, String type, String customerUsername, String customer,
                                  String cashier, String paymentMode, BigDecimal subtotal, BigDecimal discount,
                                  BigDecimal tax, BigDecimal amount, String status, LocalDateTime date,
                                  List<TransactionItemView> items) {}

    private record TransactionHeader(long id, String invoice, String type, String customerUsername,
                                     String customerName, String cashier, String paymentMode, BigDecimal subtotal,
                                     BigDecimal discount, BigDecimal tax, BigDecimal total, String status,
                                     LocalDateTime createdAt) {}

    private static final RowMapper<ProductView> PRODUCT_MAPPER = (rs, row) -> new ProductView(
            rs.getLong("id"), rs.getString("name"), rs.getString("category"), rs.getString("brand"),
            rs.getString("barcode"), rs.getBigDecimal("purchase_price"), rs.getBigDecimal("price"),
            rs.getInt("stock"), rs.getInt("min_stock"), productStatus(rs.getInt("stock"), rs.getInt("min_stock")),
            rs.getString("image_path") == null
                    ? ProductImageLibrary.match(rs.getString("name"), rs.getString("category"))
                    : rs.getString("image_path"));

    private static final RowMapper<TransactionHeader> HEADER_MAPPER = (rs, row) -> new TransactionHeader(
            rs.getLong("id"), rs.getString("invoice"), rs.getString("transaction_type"),
            rs.getString("customer_username"), rs.getString("customer_name"), rs.getString("cashier_username"),
            rs.getString("payment_mode"), rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount"),
            rs.getBigDecimal("tax"), rs.getBigDecimal("total"), rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private static String productStatus(int stock, int minStock) {
        if (stock == 0) {
            return "Out of Stock";
        }
        return stock <= minStock ? "Low Stock" : "In Stock";
    }

    private static String csvValue(String value) {
        String cell = value == null ? "" : value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        return "\"" + cell.replace("\"", "\"\"") + "\"";
    }
}
