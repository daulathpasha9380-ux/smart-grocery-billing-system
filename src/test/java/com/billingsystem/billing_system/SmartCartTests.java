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
        "spring.datasource.url=jdbc:h2:mem:billing-smart-cart-tests;DB_CLOSE_DELAY=-1",
        "app.auth.owner-password=TestOwnerPassword#2026",
        "app.auth.cashier-password=TestCashierPassword#2026",
        "app.auth.customer-password=TestCustomerPassword#2026"
})
@AutoConfigureMockMvc
class SmartCartTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoiceSmartCartService voiceService;

    @Autowired
    private PhotoSmartCartService photoService;

    @Test
    void voiceCommandNormalizationAndIntentDetection() {
        assertEquals("add two packets of amul milk", voiceService.normalize("  Add Two Packets Of Amul Milk!! "));
        assertEquals(VoiceSmartCartService.VoiceIntent.ADD_PRODUCT, voiceService.detectIntent("add two packets of milk"));
        assertEquals(VoiceSmartCartService.VoiceIntent.REMOVE_PRODUCT, voiceService.detectIntent("remove amul butter"));
        assertEquals(VoiceSmartCartService.VoiceIntent.UPDATE_QUANTITY, voiceService.detectIntent("increase milk quantity to three"));
        assertEquals(VoiceSmartCartService.VoiceIntent.SEARCH_PRODUCT, voiceService.detectIntent("search for rice"));
        assertEquals(VoiceSmartCartService.VoiceIntent.VIEW_CART, voiceService.detectIntent("show my cart"));
        assertEquals(VoiceSmartCartService.VoiceIntent.GET_TOTAL, voiceService.detectIntent("what is my total bill?"));
        assertEquals(VoiceSmartCartService.VoiceIntent.CLEAR_CART, voiceService.detectIntent("clear my cart"));
    }

    @Test
    void quantityExtractionWordsAndDigits() {
        var q1 = voiceService.parseQuantityAndProduct("two packets of amul milk");
        assertEquals(2, q1.quantity());
        assertEquals("amul milk", q1.product());

        var q2 = voiceService.parseQuantityAndProduct("3 maggi packets");
        assertEquals(3, q2.quantity());
        assertEquals("maggi", q2.product());

        var q3 = voiceService.parseQuantityAndProduct("one kilogram of sugar");
        assertEquals(1, q3.quantity());
        assertEquals("sugar", q3.product());

        var q4 = voiceService.parseQuantityAndProduct("amul butter");
        assertEquals(1, q4.quantity());
        assertEquals("amul butter", q4.product());
    }

    @Test
    void productAliasAndFuzzyMatching() {
        // "Amul milk" -> "Amul Taaza Milk 1L"
        var r1 = voiceService.processCommand("Add two packets of Amul milk");
        assertTrue(r1.success());
        assertEquals(VoiceSmartCartService.VoiceIntent.ADD_PRODUCT, r1.intent());
        assertEquals(2, r1.quantity());
        assertNotNull(r1.product());
        assertTrue(r1.product().name().contains("Amul Taaza Milk"));

        // "Add three Maggi packets" -> "Maggi 2-Min Noodles 4-Pack"
        var r2 = voiceService.processCommand("Add three Maggi packets");
        assertTrue(r2.success());
        assertEquals(3, r2.quantity());
        assertNotNull(r2.product());
        assertTrue(r2.product().name().contains("Maggi"));

        // "Search for rice" -> "Basmati Rice 5kg"
        var r3 = voiceService.processCommand("Search for rice");
        assertTrue(r3.success());
        assertEquals(VoiceSmartCartService.VoiceIntent.SEARCH_PRODUCT, r3.intent());
        assertNotNull(r3.product());
        assertTrue(r3.product().name().contains("Basmati Rice"));

        // "Remove Amul Butter"
        var r4 = voiceService.processCommand("Remove Amul Butter");
        assertTrue(r4.success());
        assertEquals(VoiceSmartCartService.VoiceIntent.REMOVE_PRODUCT, r4.intent());
        assertNotNull(r4.product());
        assertTrue(r4.product().name().contains("Amul Butter"));

        // "Increase milk quantity to three"
        var r5 = voiceService.processCommand("Increase milk quantity to three");
        assertTrue(r5.success());
        assertEquals(VoiceSmartCartService.VoiceIntent.UPDATE_QUANTITY, r5.intent());
        assertEquals(3, r5.quantity());
    }

    @Test
    void multilingualCommandsSupport() {
        // Hindi: "Do packets Amul milk add karo"
        var hindiResult = voiceService.processCommand("Do packets Amul milk add karo");
        assertTrue(hindiResult.success());
        assertEquals(VoiceSmartCartService.VoiceIntent.ADD_PRODUCT, hindiResult.intent());
        assertEquals(2, hindiResult.quantity());

        // Kannada: "Eradu packets Amul milk serisi"
        var kannadaResult = voiceService.processCommand("Eradu packets Amul milk serisi");
        assertTrue(kannadaResult.success());
        assertEquals(VoiceSmartCartService.VoiceIntent.ADD_PRODUCT, kannadaResult.intent());
        assertEquals(2, kannadaResult.quantity());
    }

    @Test
    void ambiguousAndUnknownProducts() {
        // Ambiguous command "milk"
        var rAmbiguous = voiceService.processCommand("add milk");
        assertTrue(rAmbiguous.success());

        // Unknown product
        var rUnknown = voiceService.processCommand("Add some quantum hyperdrive widget");
        assertFalse(rUnknown.success());
        assertTrue(rUnknown.feedback().contains("No product found"));
    }

    @Test
    void barcodeRecognitionViaZXing() throws Exception {
        // Generate valid barcode image for Amul Milk: "8901262010012"
        String barcode = "8901262010012";
        Code128Writer writer = new Code128Writer();
        BitMatrix matrix = writer.encode(barcode, BarcodeFormat.CODE_128, 300, 150);
        BufferedImage barcodeImage = MatrixToImageWriter.toBufferedImage(matrix);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(barcodeImage, "png", baos);
        byte[] imageBytes = baos.toByteArray();

        MockMultipartFile file = new MockMultipartFile("image", "barcode.png", "image/png", imageBytes);

        var result = photoService.recognizeProduct(file);
        assertTrue(result.success());
        assertEquals("BARCODE", result.method());
        assertEquals(barcode, result.detectedBarcode());
        assertNotNull(result.product());
        assertEquals("Amul Taaza Milk 1L", result.product().name());
    }

    @Test
    void smartCartEndpointsSecurityAndExecution() throws Exception {
        // Customer can parse voice command
        mockMvc.perform(post("/api/smart-cart/voice/parse")
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"Add two packets of Amul milk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.intent").value("ADD_PRODUCT"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.product.name").value("Amul Taaza Milk 1L"));

        // Cashier can also parse voice command
        mockMvc.perform(post("/api/smart-cart/voice/parse")
                        .with(user("cashier").roles("CASHIER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"Add three Maggi packets\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.product.name").value("Maggi 2-Min Noodles 4-Pack"));

        // Customer can upload barcode photo
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
    }

    @Test
    void testAllSixRequiredVoiceCommands() {
        // 1. "Add two packets of Amul milk"
        var r1 = voiceService.processCommand("Add two packets of Amul milk");
        assertTrue(r1.success(), "Command 1 failed: " + r1.feedback());
        assertEquals(VoiceSmartCartService.VoiceIntent.ADD_PRODUCT, r1.intent());
        assertEquals(2, r1.quantity());
        assertNotNull(r1.product());
        assertTrue(r1.product().name().toLowerCase().contains("amul") && r1.product().name().toLowerCase().contains("milk"));

        // 2. "Add bread"
        var r2 = voiceService.processCommand("Add bread");
        assertTrue(r2.success(), "Command 2 failed: " + r2.feedback());
        assertEquals(VoiceSmartCartService.VoiceIntent.ADD_PRODUCT, r2.intent());
        assertEquals(1, r2.quantity());
        assertNotNull(r2.product());
        assertTrue(r2.product().name().toLowerCase().contains("bread"));

        // 3. "Remove milk"
        var r3 = voiceService.processCommand("Remove milk");
        assertTrue(r3.success(), "Command 3 failed: " + r3.feedback());
        assertEquals(VoiceSmartCartService.VoiceIntent.REMOVE_PRODUCT, r3.intent());
        assertNotNull(r3.product());
        assertTrue(r3.product().name().toLowerCase().contains("milk"));

        // 4. "Show my cart"
        var r4 = voiceService.processCommand("Show my cart");
        assertTrue(r4.success(), "Command 4 failed: " + r4.feedback());
        assertEquals(VoiceSmartCartService.VoiceIntent.VIEW_CART, r4.intent());

        // 5. "What is my total?"
        var r5 = voiceService.processCommand("What is my total?");
        assertTrue(r5.success(), "Command 5 failed: " + r5.feedback());
        assertEquals(VoiceSmartCartService.VoiceIntent.GET_TOTAL, r5.intent());

        // 6. "Clear my cart"
        var r6 = voiceService.processCommand("Clear my cart");
        assertTrue(r6.success(), "Command 6 failed: " + r6.feedback());
        assertEquals(VoiceSmartCartService.VoiceIntent.CLEAR_CART, r6.intent());
    }

    @Test
    void testNonBarcodePhotoSafeFallbackWithoutNativeCrash() throws Exception {
        // Create an image with grocery text drawn on it: milk, bread, egg
        BufferedImage textImage = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        var g = textImage.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 300, 150);
        g.setColor(java.awt.Color.BLACK);
        g.drawString("milk", 20, 40);
        g.drawString("bread", 20, 80);
        g.drawString("egg", 20, 120);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(textImage, "png", baos);
        MockMultipartFile file = new MockMultipartFile("image", "grocery-list.png", "image/png", baos.toByteArray());

        // Ensure Photo Smart Cart does NOT crash with "Invalid memory access" or 500 error
        mockMvc.perform(multipart("/api/smart-cart/photo/recognize")
                        .file(file)
                        .with(user("customer").roles("CUSTOMER"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").isNotEmpty());
    }
}
