package com.billingsystem.billing_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:billing-product-recognition-tests;DB_CLOSE_DELAY=-1",
        "app.auth.owner-password=TestOwnerPassword#2026",
        "app.auth.cashier-password=TestCashierPassword#2026",
        "app.auth.customer-password=TestCustomerPassword#2026"
})
@AutoConfigureMockMvc
class ProductRecognitionServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRecognitionService productRecognitionService;

    @Test
    void barcodeRecognitionViaZXing() throws Exception {
        String barcode = "8901262010012";
        Code128Writer writer = new Code128Writer();
        BitMatrix matrix = writer.encode(barcode, BarcodeFormat.CODE_128, 300, 150);
        BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(matrix);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(barcodeImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile("image", "barcode.png", "image/png", imageBytes);

        var result = productRecognitionService.recognizeProduct(file);
        assertTrue(result.success());
        assertEquals("BARCODE", result.method());
        assertEquals(barcode, result.detectedBarcode());
        assertNotNull(result.product());
        assertEquals("Amul Taaza Milk 1L", result.product().name());
    }

    @Test
    void nonBarcodeImageFallbackGracefully() throws Exception {
        BufferedImage blank = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blank.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(blank, "png", baos);

        MockMultipartFile file = new MockMultipartFile("image", "blank.png", "image/png", baos.toByteArray());

        var result = productRecognitionService.recognizeProduct(file);
        assertFalse(result.success());
        assertEquals("NONE", result.method());
        assertNotNull(result.feedback());
    }

    @Test
    void smartCartEndpointsSecurityAndExecution() throws Exception {
        // Customer command endpoint
        mockMvc.perform(post("/api/smart-cart/command")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"add 2 packets of milk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("ADD_PRODUCT"))
                .andExpect(jsonPath("$.quantity").value(2));

        // Customer photo recognition endpoint
        String barcode = "8901262010012";
        Code128Writer writer = new Code128Writer();
        BitMatrix matrix = writer.encode(barcode, BarcodeFormat.CODE_128, 300, 150);
        BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(barcodeImage, "png", baos);

        MockMultipartFile file = new MockMultipartFile("image", "test-milk.png", "image/png", baos.toByteArray());

        mockMvc.perform(multipart("/api/smart-cart/photo/recognize")
                        .file(file)
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.method").value("BARCODE"))
                .andExpect(jsonPath("$.product.barcode").value(barcode));

        mockMvc.perform(multipart("/api/smart-cart/recognize")
                        .file(file)
                        .with(user("cashier").roles("CASHIER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.method").value("BARCODE"));
    }
}
