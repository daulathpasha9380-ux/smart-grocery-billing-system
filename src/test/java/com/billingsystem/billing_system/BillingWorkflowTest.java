package com.billingsystem.billing_system;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.util.UUID;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing-workflow-tests;DB_CLOSE_DELAY=-1",
        "app.auth.owner-password=TestOwnerPassword#2026",
        "app.auth.cashier-password=TestCashierPassword#2026",
        "app.auth.customer-password=TestCustomerPassword#2026"
})
@AutoConfigureMockMvc
class BillingWorkflowTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ownerCashierAndCustomerProductPagesStillRender() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Owner@123"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Cashier@123"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Customer@123"))));

        mockMvc.perform(get("/products").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("productImagePreview")));
        mockMvc.perform(get("/cashier/pos").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("posProductGrid")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btnConfirmUpiSuccess")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("btnOpenWhatsApp")));
        mockMvc.perform(get("/customer/home").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/images/products/rice.svg")));
    }

    @Test
    void catalogUsesRelevantLocalImagesAndUnknownItemsUsePlaceholder() throws Exception {
        mockMvc.perform(get("/api/products").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.barcode == '8901262010012')].imagePath")
                        .value(org.hamcrest.Matchers.hasItem("/images/products/dairy.svg")))
                .andExpect(jsonPath("$[?(@.barcode == '8906010501123')].imagePath")
                        .value(org.hamcrest.Matchers.hasItem("/images/products/rice.svg")));
        mockMvc.perform(get("/images/products/rice.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("image/svg+xml")));

        String barcode = "IMAGE-" + UUID.randomUUID();
        mockMvc.perform(post("/api/owner/products")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mystery Item","category":"Specialty","brand":"Test",
                                 "barcode":"%s","purchasePrice":1.00,"price":2.00,"stock":2,"minStock":1}
                                """.formatted(barcode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath").value("/images/products/placeholder.svg"));
    }

    @Test
    void uploadedProductImageIsOptimizedPersistedAndSharedAcrossRoles() throws Exception {
        String barcode = "UPLOAD-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/owner/products")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Handmade Test Product","category":"Specialty","brand":"Test",
                                 "barcode":"%s","purchasePrice":1.00,"price":2.00,"stock":2,"minStock":1}
                                """.formatted(barcode)))
                .andExpect(status().isOk())
                .andReturn();
        long productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        MockMultipartFile image = new MockMultipartFile(
                "image", "test.png", MediaType.IMAGE_PNG_VALUE, tinyPng());
        MockMultipartFile invalidImage = new MockMultipartFile(
                "image", "not-image.png", MediaType.IMAGE_PNG_VALUE, "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/owner/products/%d/image".formatted(productId))
                        .file(invalidImage)
                        .with(user("owner").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/owner/products/%d/image".formatted(productId))
                        .file(image)
                        .with(user("cashier").roles("CASHIER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        MvcResult uploaded = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/owner/products/%d/image".formatted(productId))
                        .file(image)
                        .with(user("owner").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String imagePath = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("imagePath").stringValue();
        org.assertj.core.api.Assertions.assertThat(imagePath).startsWith("/product-images/");

        for (String role : new String[]{"owner", "cashier", "customer"}) {
            mockMvc.perform(get("/api/products").with(user(role).roles(
                            role.equals("owner") ? "OWNER" : role.equals("cashier") ? "CASHIER" : "CUSTOMER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == %d)].imagePath".formatted(productId))
                            .value(org.hamcrest.Matchers.hasItem(imagePath)));
        }
        MvcResult servedImage = mockMvc.perform(get(imagePath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andReturn();
        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(servedImage.getResponse().getContentAsByteArray()));
        org.assertj.core.api.Assertions.assertThat(thumbnail.getWidth()).isLessThanOrEqualTo(512);
        org.assertj.core.api.Assertions.assertThat(thumbnail.getHeight()).isLessThanOrEqualTo(512);

        mockMvc.perform(delete("/api/owner/products/%d/image".formatted(productId))
                        .with(user("owner").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagePath").value("/images/products/placeholder.svg"));
    }

    private static byte[] tinyPng() throws Exception {
        BufferedImage image = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x2c8c63);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    @Test
    void ownerProductIsSharedWithCashierAndSaleIsIdempotent() throws Exception {
        BigDecimal salesBefore = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total), 0) FROM transactions
                WHERE transaction_type = 'SALE' AND CAST(created_at AS DATE) = CURRENT_DATE
                """, BigDecimal.class);
        long billsBefore = jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions
                WHERE transaction_type = 'SALE' AND CAST(created_at AS DATE) = CURRENT_DATE
                """, Long.class);
        String barcode = "TEST-" + UUID.randomUUID();
        MvcResult productResult = mockMvc.perform(post("/api/owner/products")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Integration Product","category":"Grocery","brand":"Test",
                                 "barcode":"%s","purchasePrice":10.00,"price":32.00,"stock":4,"minStock":1}
                                """.formatted(barcode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(4))
                .andReturn();
        JsonNode product = objectMapper.readTree(productResult.getResponse().getContentAsString());
        long productId = product.get("id").asLong();

        mockMvc.perform(get("/api/products").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.barcode == '%s')]".formatted(barcode)).exists());

        String operationId = UUID.randomUUID().toString();
        String checkout = """
                {"operationId":"%s","customerUsername":"customer","paymentMode":"Cash",
                 "items":[{"productId":%d,"quantity":2}]}
                """.formatted(operationId, productId);
        MvcResult sale = mockMvc.perform(post("/api/sales")
                        .with(user("cashier").roles("CASHIER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkout))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(62.08))
                .andExpect(jsonPath("$.customer").value("Sample Customer"))
                .andReturn();
        String invoice = objectMapper.readTree(sale.getResponse().getContentAsString()).get("invoice").stringValue();

        mockMvc.perform(post("/api/sales")
                        .with(user("cashier").roles("CASHIER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkout))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice").value(invoice));

        mockMvc.perform(get("/api/products").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].stock".formatted(productId)).value(2));
        mockMvc.perform(delete("/api/owner/products/%d".formatted(productId))
                        .with(user("owner").roles("OWNER"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/products").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(productId)).doesNotExist());
        mockMvc.perform(get("/api/sales").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.invoice == '%s')]".formatted(invoice)).exists());
        mockMvc.perform(get("/api/sales").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sales/export").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"sales.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invoice,Date,Customer")));
        mockMvc.perform(get("/api/sales/export").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sales/%s".formatted(invoice)).with(user("cashier").roles("CASHIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice").value(invoice));
        mockMvc.perform(get("/api/sales/%s".formatted(invoice)).with(user("other-cashier").roles("CASHIER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/owner/dashboard").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesToday").value(salesBefore.add(new BigDecimal("62.08"))))
                .andExpect(jsonPath("$.billsToday").value(billsBefore + 1));
        mockMvc.perform(get("/api/owner/dashboard").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customer/bills").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.invoice == '%s')]".formatted(invoice)).exists());
        mockMvc.perform(get("/api/customer/bills").with(user("owner").roles("OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateProductLinesAreCombinedBeforeStockValidation() throws Exception {
        String barcode = "DUPLICATE-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/owner/products")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Duplicate Line Product","category":"Grocery","brand":"Test",
                                 "barcode":"%s","purchasePrice":10.00,"price":20.00,"stock":3,"minStock":1}
                                """.formatted(barcode)))
                .andExpect(status().isOk())
                .andReturn();
        long productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String operationId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/sales")
                        .with(user("cashier").roles("CASHIER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operationId":"%s","paymentMode":"Cash","items":[
                                  {"productId":%d,"quantity":2},{"productId":%d,"quantity":2}]}
                                """.formatted(operationId, productId, productId)))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId)).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE operation_id = ?", Integer.class, operationId)).isZero();
    }

    @Test
    void concurrentSalesCannotOversellTheSameProduct() throws Exception {
        String barcode = "CONCURRENT-" + UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/api/owner/products")
                        .with(user("owner").roles("OWNER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Concurrent Sale Product","category":"Grocery","brand":"Test",
                                 "barcode":"%s","purchasePrice":10.00,"price":20.00,"stock":1,"minStock":1}
                                """.formatted(barcode)))
                .andExpect(status().isOk())
                .andReturn();
        long productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String firstOperationId = UUID.randomUUID().toString();
        String secondOperationId = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstSale = executor.submit(() -> submitSingleItemSale(
                    start, firstOperationId, productId));
            Future<Integer> secondSale = executor.submit(() -> submitSingleItemSale(
                    start, secondOperationId, productId));
            start.countDown();

            int firstStatus = firstSale.get(15, TimeUnit.SECONDS);
            int secondStatus = secondSale.get(15, TimeUnit.SECONDS);
            org.assertj.core.api.Assertions.assertThat(List.of(firstStatus, secondStatus))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT stock FROM products WHERE id = ?", Integer.class, productId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transactions
                WHERE operation_id IN (?, ?) AND transaction_type = 'SALE'
                """, Integer.class, firstOperationId, secondOperationId)).isEqualTo(1);
    }

    private int submitSingleItemSale(CountDownLatch start, String operationId, long productId) throws Exception {
        start.await();
        return mockMvc.perform(post("/api/sales")
                        .with(user("cashier").roles("CASHIER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operationId":"%s","paymentMode":"Cash",
                                 "items":[{"productId":%d,"quantity":1}]}
                                """.formatted(operationId, productId)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void customerOrderIsSavedAndOwnerCannotReadAnotherCustomersOrderFeed() throws Exception {
        String operationId = UUID.randomUUID().toString();
        String order = """
                {"operationId":"%s","items":[{"productId":1,"quantity":1}]}
                """.formatted(operationId);
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORDER"))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andReturn();
        String invoice = objectMapper.readTree(result.getResponse().getContentAsString()).get("invoice").stringValue();

        mockMvc.perform(post("/api/orders")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice").value(invoice));
        mockMvc.perform(post("/api/orders")
                        .with(user("another-customer").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(order))
                .andExpect(status().isConflict())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(invoice))));

        mockMvc.perform(get("/api/orders").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.invoice == '%s')]".formatted(invoice)).exists());
        mockMvc.perform(get("/api/orders").with(user("owner").roles("OWNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/owner/orders").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.invoice == '%s')]".formatted(invoice)).exists());
        mockMvc.perform(get("/api/owner/orders").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customer/profile").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("customer"))
                .andExpect(jsonPath("$.points").value(
                        jdbc.queryForObject("SELECT loyalty_points FROM app_users WHERE username = ?",
                                Integer.class, "customer")));
        mockMvc.perform(get("/api/customer/profile").with(user("owner").roles("OWNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customer/bills").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void customerOrderWithLoyaltyPointsRedemptionReducesPointsAndAppliesDiscount() throws Exception {
        // Set points for customer
        jdbc.update("UPDATE app_users SET loyalty_points = 50 WHERE username = 'customer'");

        String operationId = UUID.randomUUID().toString();
        String orderJson = """
                {"operationId":"%s","redeemLoyaltyPoints":true,"items":[{"productId":1,"quantity":1}]}
                """.formatted(operationId);

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORDER"))
                .andExpect(jsonPath("$.discount").value(7.56))
                .andReturn();

        // Verify updated points: 50 redeemed (-50), plus earned points (total 26.04 / 10 = 2)
        Integer updatedPoints = jdbc.queryForObject(
                "SELECT loyalty_points FROM app_users WHERE username = 'customer'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(updatedPoints).isEqualTo(2);
    }
}
