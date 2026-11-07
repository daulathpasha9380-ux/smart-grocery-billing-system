package com.billingsystem.billing_system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-movement-tests;DB_CLOSE_DELAY=-1",
        "app.auth.owner-password=TestOwnerPassword#2026",
        "app.auth.cashier-password=TestCashierPassword#2026",
        "app.auth.customer-password=TestCustomerPassword#2026"
})
@AutoConfigureMockMvc
class StockMovementAndWasteTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long createTestProduct(String name, int stock, BigDecimal purchasePrice, BigDecimal price) {
        String barcode = "BAR-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO products (name, category, brand, barcode, purchase_price, price, stock, min_stock, active)
                VALUES (?, 'Groceries', 'FreshLedger', ?, ?, ?, ?, 5, TRUE)
                """, name, barcode, purchasePrice, price, stock);
        return jdbc.queryForObject("SELECT id FROM products WHERE barcode = ?", Long.class, barcode);
    }

    @Test
    @DisplayName("Sale generates audit record in stock_movements with negative quantity change")
    void saleGeneratesStockMovement() throws Exception {
        long productId = createTestProduct("Audit Apple", 50, new BigDecimal("80.00"), new BigDecimal("120.00"));
        String operationId = "op-sale-" + UUID.randomUUID();

        mockMvc.perform(post("/api/sales")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operationId": "%s",
                                  "customerUsername": "",
                                  "paymentMode": "Cash",
                                  "items": [{"productId": %d, "quantity": 5}]
                                }
                                """.formatted(operationId, productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Integer remainingStock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(remainingStock).isEqualTo(45);

        List<Map<String, Object>> movements = jdbc.queryForList(
                "SELECT * FROM stock_movements WHERE product_id = ? AND movement_type = 'SALE'", productId);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).get("QUANTITY_CHANGE")).isEqualTo(-5);
        assertThat(movements.get(0).get("ACTOR_USERNAME")).isEqualTo("owner");
        assertThat((String) movements.get(0).get("REFERENCE_INVOICE")).startsWith("INV-");
    }

    @Test
    @DisplayName("Restock generates audit record in stock_movements with positive quantity change")
    void restockGeneratesStockMovement() throws Exception {
        long productId = createTestProduct("Audit Milk", 20, new BigDecimal("40.00"), new BigDecimal("55.00"));

        mockMvc.perform(post("/api/owner/products/" + productId + "/restock")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 15,
                                  "reason": "Weekly vendor delivery"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(35));

        Integer updatedStock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(updatedStock).isEqualTo(35);

        List<Map<String, Object>> movements = jdbc.queryForList(
                "SELECT * FROM stock_movements WHERE product_id = ? AND movement_type = 'RESTOCK'", productId);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).get("QUANTITY_CHANGE")).isEqualTo(15);
        assertThat(movements.get(0).get("ACTOR_USERNAME")).isEqualTo("owner");
        assertThat((String) movements.get(0).get("REASON")).isEqualTo("Weekly vendor delivery");
    }

    @Test
    @DisplayName("Manual product stock adjustment creates ADJUSTMENT movement record")
    void manualProductUpdateCreatesStockMovement() throws Exception {
        long productId = createTestProduct("Audit Banana", 30, new BigDecimal("25.00"), new BigDecimal("40.00"));

        String barcode = jdbc.queryForObject("SELECT barcode FROM products WHERE id = ?", String.class, productId);

        mockMvc.perform(put("/api/owner/products/" + productId)
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Audit Banana Premium",
                                  "category": "Groceries",
                                  "brand": "FreshLedger",
                                  "barcode": "%s",
                                  "purchasePrice": 25.00,
                                  "price": 45.00,
                                  "stock": 42,
                                  "minStock": 5
                                }
                                """.formatted(barcode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(42));

        List<Map<String, Object>> movements = jdbc.queryForList(
                "SELECT * FROM stock_movements WHERE product_id = ? AND movement_type = 'ADJUSTMENT'", productId);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).get("QUANTITY_CHANGE")).isEqualTo(12);
        assertThat(movements.get(0).get("ACTOR_USERNAME")).isEqualTo("owner");
    }

    @Test
    @DisplayName("Waste recording decrements stock, records to waste_records and stock_movements")
    void recordWasteSuccess() throws Exception {
        long productId = createTestProduct("Audit Tomato", 40, new BigDecimal("30.00"), new BigDecimal("45.00"));

        mockMvc.perform(post("/api/owner/waste")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 8,
                                  "reason": "SPOILED",
                                  "notes": "Overripe during weekend storage"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(8))
                .andExpect(jsonPath("$.unitCost").value(30.00))
                .andExpect(jsonPath("$.totalLoss").value(240.00))
                .andExpect(jsonPath("$.reason").value("SPOILED"));

        Integer remainingStock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(remainingStock).isEqualTo(32);

        List<Map<String, Object>> wasteList = jdbc.queryForList(
                "SELECT * FROM waste_records WHERE product_id = ?", productId);
        assertThat(wasteList).hasSize(1);
        assertThat(wasteList.get(0).get("QUANTITY")).isEqualTo(8);
        assertThat(wasteList.get(0).get("ACTOR_USERNAME")).isEqualTo("owner");

        List<Map<String, Object>> movementList = jdbc.queryForList(
                "SELECT * FROM stock_movements WHERE product_id = ? AND movement_type = 'WASTE'", productId);
        assertThat(movementList).hasSize(1);
        assertThat(movementList.get(0).get("QUANTITY_CHANGE")).isEqualTo(-8);
    }

    @Test
    @DisplayName("Waste recording rejects excess quantity with 409 Conflict")
    void recordWasteRejectsExcessQuantity() throws Exception {
        long productId = createTestProduct("Audit Bread", 5, new BigDecimal("20.00"), new BigDecimal("30.00"));

        mockMvc.perform(post("/api/owner/waste")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 10,
                                  "reason": "EXPIRED",
                                  "notes": "Past expiration date"
                                }
                                """.formatted(productId)))
                .andExpect(status().isConflict());

        Integer stock = jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, productId);
        assertThat(stock).isEqualTo(5);
    }

    @Test
    @DisplayName("Owner can query waste records and stock movements")
    void ownerCanListWasteAndStockMovements() throws Exception {
        long productId = createTestProduct("Audit Sugar", 60, new BigDecimal("42.00"), new BigDecimal("52.00"));

        mockMvc.perform(post("/api/owner/waste")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 3,
                                  "reason": "DAMAGED",
                                  "notes": "Bag torn in transport"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/owner/waste")
                        .with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.productId == " + productId + ")].reason").value("DAMAGED"));

        mockMvc.perform(get("/api/owner/products/" + productId + "/stock-movements")
                        .with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movementType").value("WASTE"))
                .andExpect(jsonPath("$[0].quantityChange").value(-3));
    }
}
