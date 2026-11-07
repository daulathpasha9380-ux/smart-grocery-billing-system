package com.billingsystem.billing_system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer-order-status-tests;DB_CLOSE_DELAY=-1",
        "app.auth.owner-password=TestOwnerPassword#2026",
        "app.auth.cashier-password=TestCashierPassword#2026",
        "app.auth.customer-password=TestCustomerPassword#2026"
})
@AutoConfigureMockMvc
class CustomerOrderStatusTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private long createTestProduct(String name, int stock, BigDecimal purchasePrice, BigDecimal price) {
        String barcode = "BAR-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock, active)
                VALUES (?, 'Produce', 'FreshLedger', ?, ?, ?, ?, 5, TRUE)
                """, name, barcode, purchasePrice, price, stock);
        return jdbc.queryForObject("SELECT id FROM products WHERE barcode = ?", Long.class, barcode);
    }

    private String placeCustomerOrder(long productId, int quantity, String customerUsername) throws Exception {
        String operationId = "op-order-" + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .with(user(customerUsername).roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "%s",
                                  "customerUsername": "%s",
                                  "paymentMode": "Online order",
                                  "items": [{"productId": %d, "quantity": %d}]
                                }
                                """.formatted(operationId, customerUsername, productId, quantity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("invoice").asText();
    }

    @Test
    @DisplayName("Order placement initializes order with status PLACED and decrements stock")
    void placeOrderInitializesPlaced() throws Exception {
        long productId = createTestProduct("Order Mango", 25, new BigDecimal("50.00"), new BigDecimal("80.00"));

        String invoice = placeCustomerOrder(productId, 4, "customer");
        assertThat(invoice).startsWith("ORD-");

        Integer stock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stock).isEqualTo(21);

        String status = jdbc.queryForObject("SELECT status FROM transactions WHERE invoice = ?", String.class, invoice);
        assertThat(status).isEqualTo("PLACED");
    }

    @Test
    @DisplayName("Owner can transition order through full valid lifecycle: PLACED -> CONFIRMED -> PROCESSING -> READY -> COMPLETED")
    void ownerCanTransitionFullLifecycle() throws Exception {
        long productId = createTestProduct("Order Orange", 30, new BigDecimal("40.00"), new BigDecimal("60.00"));
        String invoice = placeCustomerOrder(productId, 3, "customer");

        // PLACED -> CONFIRMED
        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // CONFIRMED -> PROCESSING
        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PROCESSING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        // PROCESSING -> READY
        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "READY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        // READY -> COMPLETED
        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // COMPLETED is terminal - cannot be transitioned
        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELLED"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Owner cancellation restores stock and creates RESTOCK stock movement record")
    void ownerCancellationRestoresStock() throws Exception {
        long productId = createTestProduct("Order Grapes", 20, new BigDecimal("60.00"), new BigDecimal("90.00"));
        String invoice = placeCustomerOrder(productId, 5, "customer");

        Integer stockAfterOrder = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stockAfterOrder).isEqualTo(15);

        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CANCELLED",
                                  "notes": "Customer requested cancellation via phone"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Integer stockAfterCancel = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stockAfterCancel).isEqualTo(20);

        List<Map<String, Object>> movements = jdbc.queryForList(
                "SELECT * FROM stock_movements WHERE product_id = ? AND reference_invoice = ? AND movement_type = 'RESTOCK'",
                productId, invoice);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).get("QUANTITY_CHANGE")).isEqualTo(5);
    }

    @Test
    @DisplayName("Customer can cancel own order in PLACED or CONFIRMED status")
    void customerCanCancelOwnOrder() throws Exception {
        long productId = createTestProduct("Order Kiwi", 18, new BigDecimal("70.00"), new BigDecimal("100.00"));
        String invoice = placeCustomerOrder(productId, 2, "customer");

        mockMvc.perform(post("/api/orders/" + invoice + "/cancel")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Integer stockAfterCancel = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stockAfterCancel).isEqualTo(18);
    }

    @Test
    @DisplayName("Customer cannot cancel another customer's order (403 Forbidden)")
    void customerCannotCancelAnotherCustomerOrder() throws Exception {
        long productId = createTestProduct("Order Papaya", 15, new BigDecimal("35.00"), new BigDecimal("50.00"));
        String invoice = placeCustomerOrder(productId, 2, "customer");

        // Create a second customer in DB
        jdbc.update("""
                INSERT INTO app_users (username, password_hash, role, display_name, email, phone, loyalty_points, enabled)
                VALUES ('customer2', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'CUSTOMER', 'Customer Two', 'cust2@freshledger.test', '9876500002', 0, TRUE)
                """);

        mockMvc.perform(post("/api/orders/" + invoice + "/cancel")
                        .with(user("customer2").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Customer cannot cancel order once in PROCESSING or READY status (409 Conflict)")
    void customerCannotCancelProcessingOrder() throws Exception {
        long productId = createTestProduct("Order Guava", 22, new BigDecimal("30.00"), new BigDecimal("45.00"));
        String invoice = placeCustomerOrder(productId, 3, "customer");

        // Owner advances order to PROCESSING
        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CONFIRMED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PROCESSING"}
                                """))
                .andExpect(status().isOk());

        // Customer attempts to cancel order in PROCESSING status
        mockMvc.perform(post("/api/orders/" + invoice + "/cancel")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Invalid status transition is rejected with 409 Conflict")
    void invalidStatusTransitionRejected() throws Exception {
        long productId = createTestProduct("Order Watermelon", 10, new BigDecimal("80.00"), new BigDecimal("120.00"));
        String invoice = placeCustomerOrder(productId, 1, "customer");

        // Attempt invalid skip from PLACED directly to READY
        mockMvc.perform(patch("/api/owner/orders/" + invoice + "/status")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "READY"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Customer can view own order details but not another customer's order")
    void customerOrderVisibility() throws Exception {
        long productId = createTestProduct("Order Pineapple", 12, new BigDecimal("60.00"), new BigDecimal("90.00"));
        String invoice = placeCustomerOrder(productId, 2, "customer");

        // Own order succeeds
        mockMvc.perform(get("/api/orders/" + invoice)
                        .with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice").value(invoice));

        // Another customer is forbidden
        mockMvc.perform(get("/api/orders/" + invoice)
                        .with(user("customer2").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
