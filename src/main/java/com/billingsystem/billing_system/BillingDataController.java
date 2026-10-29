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
    private final int pointsPerHundred;
    private final BigDecimal valuePerPoint;

    public BillingDataController(
            JdbcTemplate jdbc,
            ProductImageService productImages,
            @org.springframework.beans.factory.annotation.Value("${app.loyalty.points-per-100:1}") int pointsPerHundred,
            @org.springframework.beans.factory.annotation.Value("${app.loyalty.value-per-point:1.00}") BigDecimal valuePerPoint) {
        this.jdbc = jdbc;
        this.productImages = productImages;
        if (pointsPerHundred < 0 || valuePerPoint.signum() <= 0) {
            throw new IllegalArgumentException("Loyalty earning and redemption settings must be positive.");
        }
        this.pointsPerHundred = pointsPerHundred;
        this.valuePerPoint = valuePerPoint;
    }

    @GetMapping("/products")
    public List<?> products(Authentication authentication) {
        List<ProductView> products = jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE active = TRUE ORDER BY name
                """, PRODUCT_MAPPER);
        boolean customer = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"));
        if (customer) {
            return products.stream().map(product -> new CustomerProductView(product.id(), product.name(),
                    product.category(), product.brand(), product.barcode(), product.price(), product.stock(),
                    product.minStock(), product.status(), product.imagePath())).toList();
        }
        return products;
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
    @Transactional
    public ProductView updateProduct(@PathVariable long id, @RequestBody ProductRequest request, Principal principal) {
        validateProduct(request);
        int previousStock = jdbc.query("SELECT stock FROM products WHERE id = ? AND active = TRUE",
                (rs, row) -> rs.getInt("stock"), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
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
            int stockDifference = request.stock() - previousStock;
            if (stockDifference != 0) {
                jdbc.update("""
                        INSERT INTO stock_movements
                            (product_id, actor_username, movement_type, quantity_change, reason)
                        VALUES (?, ?, 'ADJUSTMENT', ?, 'Product stock adjusted by owner')
                        """, id, principal.getName(), stockDifference);
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
    @Transactional
    public ProductView restock(@PathVariable long id, @RequestBody RestockRequest request, Principal principal) {
        if (request.quantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Restock quantity must be positive.");
        }
        if (jdbc.update("UPDATE products SET stock = stock + ? WHERE id = ? AND active = TRUE",
                request.quantity(), id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        jdbc.update("""
                INSERT INTO stock_movements
                    (product_id, actor_username, movement_type, quantity_change, reason)
                VALUES (?, ?, 'RESTOCK', ?, 'Owner restock')
                """, id, principal.getName(), request.quantity());
        return productById(id);
    }

    @PostMapping("/owner/products/{id}/waste")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional
    public WasteView recordWaste(@PathVariable long id, @RequestBody WasteRequest request, Principal principal) {
        if (request == null || request.quantity() <= 0 || request.reason() == null
                || !List.of("Expired", "Damaged", "Broken", "Lost", "Returned").contains(request.reason())
                || request.notes() != null && request.notes().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid waste quantity and reason.");
        }
        WasteProduct product = jdbc.query("""
                SELECT id, name, purchase_price, stock FROM products
                WHERE id = ? AND active = TRUE FOR UPDATE
                """, (rs, row) -> new WasteProduct(rs.getLong("id"), rs.getString("name"),
                rs.getBigDecimal("purchase_price"), rs.getInt("stock")), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        if (request.quantity() > product.stock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only " + product.stock() + " units of " + product.name() + " are in stock.");
        }
        jdbc.update("UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?",
                request.quantity(), id, request.quantity());
        String notes = request.notes() == null ? "" : request.notes().trim();
        String reason = request.reason() + (notes.isEmpty() ? "" : ": " + notes);
        jdbc.update("""
                INSERT INTO stock_movements
                    (product_id, actor_username, movement_type, quantity_change, reason)
                VALUES (?, ?, 'WASTE', ?, ?)
                """, id, principal.getName(), -request.quantity(), reason);
        jdbc.update("""
                INSERT INTO waste_records (product_id, actor_username, quantity, unit_cost, reason, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, principal.getName(), request.quantity(), product.purchasePrice(), request.reason(), notes);
        BigDecimal loss = product.purchasePrice().multiply(BigDecimal.valueOf(request.quantity()))
                .setScale(2, RoundingMode.HALF_UP);
        int remainingStock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, id);
        return new WasteView(id, product.name(), request.quantity(), product.purchasePrice(), loss,
                request.reason(), notes, remainingStock);
    }

    @GetMapping("/owner/products/{id}/movements")
    @PreAuthorize("hasRole('OWNER')")
    public List<StockMovementView> stockMovements(@PathVariable long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Integer.class, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found.");
        }
        return jdbc.query("""
                SELECT movement_type, quantity_change, reference_invoice, reason, actor_username, created_at
                FROM stock_movements WHERE product_id = ? ORDER BY created_at DESC, id DESC
                """, (rs, row) -> new StockMovementView(rs.getString("movement_type"),
                rs.getInt("quantity_change"), rs.getString("reference_invoice"), rs.getString("reason"),
                rs.getString("actor_username"), rs.getTimestamp("created_at").toLocalDateTime()), id);
    }

    @GetMapping("/owner/waste")
    @PreAuthorize("hasRole('OWNER')")
    public List<WasteView> wasteRecords() {
        return jdbc.query("""
                SELECT wr.product_id, p.name, wr.quantity, wr.unit_cost,
                    wr.unit_cost * wr.quantity AS loss, wr.reason, wr.notes, p.stock
                FROM waste_records wr JOIN products p ON p.id = wr.product_id
                ORDER BY wr.created_at DESC, wr.id DESC
                """, (rs, row) -> new WasteView(rs.getLong("product_id"), rs.getString("name"),
                rs.getInt("quantity"), rs.getBigDecimal("unit_cost"), rs.getBigDecimal("loss"),
                rs.getString("reason"), rs.getString("notes"), rs.getInt("stock")));
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

            @GetMapping("/customer/loyalty")
            @PreAuthorize("hasRole('CUSTOMER')")
            public LoyaltySummary customerLoyalty(Principal principal) {
            int balance = jdbc.queryForObject("""
                SELECT loyalty_points FROM app_users WHERE username = ? AND role = 'CUSTOMER'
                """, Integer.class, principal.getName());
            List<LoyaltyEntryView> entries = jdbc.query("""
                SELECT entry_type, points_delta, reason, created_at
                FROM loyalty_transactions WHERE username = ? ORDER BY created_at DESC, id DESC
                """, (rs, row) -> new LoyaltyEntryView(rs.getString("entry_type"), rs.getInt("points_delta"),
                rs.getString("reason"), rs.getTimestamp("created_at").toLocalDateTime()), principal.getName());
            return new LoyaltySummary(balance, entries);
            }

        @GetMapping("/loyalty/rules")
        @PreAuthorize("isAuthenticated()")
        public LoyaltyRulesView loyaltyRules() {
            return new LoyaltyRulesView(pointsPerHundred, valuePerPoint);
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
        int pointsToRedeem = request.pointsToRedeem() == null ? 0 : request.pointsToRedeem();
        if (pointsToRedeem < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points to redeem cannot be negative.");
        }
        int customerPoints = 0;
        if (pointsToRedeem > 0) {
            if (customerUsername == null || customerUsername.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a customer account to redeem points.");
            }
            List<Integer> balances = jdbc.query("""
                    SELECT loyalty_points FROM app_users
                    WHERE username = ? AND role = 'CUSTOMER' AND enabled = TRUE FOR UPDATE
                    """, (rs, row) -> rs.getInt("loyalty_points"), customerUsername);
            if (balances.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected customer account was not found.");
            }
            customerPoints = balances.get(0);
            if (pointsToRedeem > customerPoints) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The customer does not have enough loyalty points.");
            }
        }
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
        BigDecimal redemptionAmount = valuePerPoint.multiply(BigDecimal.valueOf(pointsToRedeem))
            .setScale(2, RoundingMode.HALF_UP);
        if (redemptionAmount.compareTo(subtotal.subtract(discount)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The selected points exceed the remaining bill amount.");
        }
        discount = discount.add(redemptionAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP);
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

        if (pointsToRedeem > 0) {
            int updatedPoints = jdbc.update("""
                UPDATE app_users SET loyalty_points = loyalty_points - ?
                WHERE username = ? AND role = 'CUSTOMER' AND loyalty_points >= ?
                """, pointsToRedeem, customerUsername, pointsToRedeem);
            if (updatedPoints != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The customer's points changed during checkout.");
            }
            jdbc.update("""
                INSERT INTO loyalty_transactions
                (reference_key, username, transaction_id, entry_type, points_delta, reason)
                VALUES (?, ?, ?, 'REDEEM', ?, 'Points redeemed at checkout')
                """, "sale:" + transactionId + ":redeem", customerUsername, transactionId,
                -pointsToRedeem);
        }

        if (type.equals("SALE") && customerUsername != null && !customerUsername.isBlank()
            && pointsPerHundred > 0) {
            int earnedPoints = total.multiply(BigDecimal.valueOf(pointsPerHundred))
                .divide(new BigDecimal("100"), 0, RoundingMode.DOWN).intValue();
            if (earnedPoints > 0) {
            jdbc.update("UPDATE app_users SET loyalty_points = loyalty_points + ? WHERE username = ? AND role = 'CUSTOMER'",
                earnedPoints, customerUsername);
            jdbc.update("""
                INSERT INTO loyalty_transactions
                    (reference_key, username, transaction_id, entry_type, points_delta, reason)
                VALUES (?, ?, ?, 'EARN', ?, 'Points earned on completed sale')
                """, "sale:" + transactionId + ":earn", customerUsername, transactionId, earnedPoints);
            }
        }

        for (PricedItem item : pricedItems) {
            jdbc.update("""
                    INSERT INTO transaction_items (transaction_id, product_id, product_name, unit_price, quantity, line_total)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, transactionId, item.product().id(), item.product().name(), item.product().price(),
                    item.quantity(), item.lineTotal());
            jdbc.update("UPDATE products SET stock = stock - ? WHERE id = ?", item.quantity(), item.product().id());
            jdbc.update("""
                    INSERT INTO stock_movements
                        (product_id, actor_username, movement_type, quantity_change, reference_invoice, reason)
                    VALUES (?, ?, 'SALE', ?, ?, 'Completed sale')
                    """, item.product().id(), principal.getName(), -item.quantity(), invoice);
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
    public record LoyaltySummary(int balance, List<LoyaltyEntryView> entries) {}
    public record LoyaltyEntryView(String type, int pointsDelta, String reason, LocalDateTime date) {}
    public record LoyaltyRulesView(int pointsPerHundred, BigDecimal valuePerPoint) {}
    public record RestockRequest(int quantity) {}
    public record WasteRequest(int quantity, String reason, String notes) {}
    public record WasteView(long productId, String productName, int quantity, BigDecimal unitCost,
                            BigDecimal loss, String reason, String notes, int remainingStock) {}
    public record StockMovementView(String type, int quantityChange, String invoice, String reason,
                                    String actor, LocalDateTime date) {}
    public record WasteProduct(long id, String name, BigDecimal purchasePrice, int stock) {}
    public record CheckoutItem(long productId, int quantity) {}
    public record CheckoutRequest(String operationId, String customerUsername, String paymentMode,
                                  List<CheckoutItem> items, Integer pointsToRedeem) {}
    public record ProductView(long id, String name, String category, String brand, String barcode,
                              BigDecimal purchasePrice, BigDecimal price, int stock, int minStock,
                              String status, String imagePath) {}
    public record CustomerProductView(long id, String name, String category, String brand, String barcode,
                                      BigDecimal price, int stock, int minStock, String status, String imagePath) {}
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
