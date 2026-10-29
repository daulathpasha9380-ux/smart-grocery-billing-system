package com.billingsystem.billing_system;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductRecognitionService {

    private static final long MAX_PHOTO_BYTES = 5 * 1024 * 1024; // 5 MB

    public record RecognitionResult(
            boolean success,
            String method, // "BARCODE", "OCR", "VISUAL_SIMILARITY", "NONE"
            String detectedBarcode,
            String extractedText,
            BillingDataController.ProductView product,
            List<BillingDataController.ProductView> candidates,
            boolean ambiguous,
            String feedback
    ) {}

    public record PhotoRecognitionResult(
            boolean success,
            String method,
            String detectedBarcode,
            String extractedText,
            BillingDataController.ProductView product,
            List<BillingDataController.ProductView> candidates,
            boolean ambiguous,
            String feedback
    ) {
        public static PhotoRecognitionResult from(RecognitionResult r) {
            return new PhotoRecognitionResult(
                    r.success(),
                    r.method(),
                    r.detectedBarcode(),
                    r.extractedText(),
                    r.product(),
                    r.candidates(),
                    r.ambiguous(),
                    r.feedback()
            );
        }
    }

    private static final RowMapper<BillingDataController.ProductView> PRODUCT_MAPPER = (rs, row) -> {
        int stock = rs.getInt("stock");
        int minStock = rs.getInt("min_stock");
        String name = rs.getString("name");
        String category = rs.getString("category");
        String imagePath = rs.getString("image_path");
        if (imagePath == null || imagePath.isBlank()) {
            imagePath = ProductImageLibrary.match(name, category);
        }
        String status = stock == 0 ? "Out of Stock" : (stock <= minStock ? "Low Stock" : "In Stock");
        return new BillingDataController.ProductView(
                rs.getLong("id"),
                name,
                category,
                rs.getString("brand"),
                rs.getString("barcode"),
                rs.getBigDecimal("purchase_price"),
                rs.getBigDecimal("price"),
                stock,
                minStock,
                status,
                imagePath
        );
    };

    private final JdbcTemplate jdbc;

    public ProductRecognitionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Process an uploaded photo using Barcode -> OCR -> Visual Similarity pipeline.
     */
    public RecognitionResult recognizeProduct(MultipartFile file) {
        validateImageFile(file);

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid or readable image.");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read the image file.", e);
        }

        // 1. Stage A: Barcode recognition via ZXing
        String detectedBarcode = decodeBarcode(image);
        if (detectedBarcode != null && !detectedBarcode.isBlank()) {
            BillingDataController.ProductView barcodeProduct = findProductByBarcode(detectedBarcode);
            if (barcodeProduct != null) {
                return new RecognitionResult(
                        true,
                        "BARCODE",
                        detectedBarcode,
                        "",
                        barcodeProduct,
                        List.of(barcodeProduct),
                        false,
                        "Identified by barcode (" + detectedBarcode + "): " + barcodeProduct.name()
                );
            }
        }

        // 2. Stage B: OCR text extraction & catalog matching
        String ocrText = extractTextFromImage(image);
        if (ocrText != null && !ocrText.isBlank()) {
            List<BillingDataController.ProductView> listMatches = new ArrayList<>();
            Set<Long> seenIds = new HashSet<>();
            List<BillingDataController.ProductView> allProducts = getAllActiveProducts();
            List<ProductCatalogMatcher.CatalogProduct> catalogProducts = allProducts.stream()
                    .map(p -> new ProductCatalogMatcher.CatalogProduct(
                            p.id(), p.name(), p.brand(), p.category(), p.barcode(),
                            p.price(), p.stock(), p.imagePath()))
                    .toList();

            // Check line by line for shopping list items
            String[] lines = ocrText.split("[\\r\\n]+");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.length() >= 2) {
                    List<ProductCatalogMatcher.ProductMatch> matches = ProductCatalogMatcher.match(trimmed, catalogProducts);
                    for (ProductCatalogMatcher.ProductMatch m : matches) {
                        if (seenIds.add(m.product().id())) {
                            findProductById(allProducts, m.product().id()).ifPresent(listMatches::add);
                        }
                    }
                }
            }

            // If line-by-line didn't match, search whole text
            if (listMatches.isEmpty()) {
                List<ProductCatalogMatcher.ProductMatch> matches = ProductCatalogMatcher.match(ocrText, catalogProducts);
                for (ProductCatalogMatcher.ProductMatch m : matches) {
                    if (seenIds.add(m.product().id())) {
                        findProductById(allProducts, m.product().id()).ifPresent(listMatches::add);
                    }
                }
            }

            if (!listMatches.isEmpty()) {
                BillingDataController.ProductView top = listMatches.get(0);
                boolean ambiguous = listMatches.size() > 1;
                String feedback = ambiguous
                        ? "Identified " + listMatches.size() + " product(s) from image. Select items to add to cart."
                        : "Identified from packaging text: " + top.name();
                return new RecognitionResult(
                        true,
                        "OCR",
                        detectedBarcode != null ? detectedBarcode : "",
                        ocrText,
                        top,
                        listMatches,
                        ambiguous,
                        feedback
                );
            }
        }

        // 3. Stage C: Visual image similarity
        List<VisualMatch> visualMatches = findVisualMatches(image);
        if (!visualMatches.isEmpty()) {
            List<BillingDataController.ProductView> candidates = visualMatches.stream()
                    .map(VisualMatch::product)
                    .toList();
            BillingDataController.ProductView top = candidates.get(0);
            boolean ambiguous = candidates.size() > 1;
            return new RecognitionResult(
                    true,
                    "VISUAL_SIMILARITY",
                    detectedBarcode != null ? detectedBarcode : "",
                    ocrText != null ? ocrText : "",
                    top,
                    candidates,
                    ambiguous,
                    ambiguous
                            ? "Visually matched packaging. Multiple candidates found."
                            : "Visually recognized: " + top.name()
            );
        }

        String fallbackFeedback = isTesseractConfigured()
                ? "Could not recognize product. Please ensure good lighting and clear product packaging."
                : "No barcode detected. To enable packaging & list OCR, place 'eng.traineddata' in '"
                  + Path.of(System.getProperty("user.home"), ".freshledger", "tessdata").toString()
                  + "' or configure TESSDATA_PREFIX. You can also search or use Smart Cart commands.";

        return new RecognitionResult(
                false,
                "NONE",
                detectedBarcode != null ? detectedBarcode : "",
                ocrText != null ? ocrText : "",
                null,
                Collections.emptyList(),
                false,
                fallbackFeedback
        );
    }

    /**
     * Alias for recognizeProduct to support concise service calls.
     */
    public RecognitionResult recognize(MultipartFile file) {
        return recognizeProduct(file);
    }

    /**
     * Decode 1D or 2D barcode using ZXing.
     */
    public String decodeBarcode(BufferedImage image) {
        if (image == null) return null;

        // Try standard image
        String result = tryDecodeBitmap(image);
        if (result != null) return result;

        // Try pre-processed grayscale variant
        BufferedImage grayscale = toGrayscale(image);
        result = tryDecodeBitmap(grayscale);
        if (result != null) return result;

        // Try rotated variants (90, 180, 270 degrees)
        for (int angle : List.of(90, 180, 270)) {
            BufferedImage rotated = rotateImage(image, angle);
            result = tryDecodeBitmap(rotated);
            if (result != null) return result;
        }

        return null;
    }

    private String tryDecodeBitmap(BufferedImage img) {
        try {
            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
                    com.google.zxing.BarcodeFormat.EAN_13,
                    com.google.zxing.BarcodeFormat.EAN_8,
                    com.google.zxing.BarcodeFormat.UPC_A,
                    com.google.zxing.BarcodeFormat.UPC_E,
                    com.google.zxing.BarcodeFormat.CODE_128,
                    com.google.zxing.BarcodeFormat.CODE_39,
                    com.google.zxing.BarcodeFormat.QR_CODE
            ));
            Result zResult = new MultiFormatReader().decode(bitmap, hints);
            return zResult != null ? zResult.getText().trim() : null;
        } catch (NotFoundException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Extract visible text from image packaging.
     * Safely checks for traineddata presence and catches any native JNA crashes.
     */
    public String extractTextFromImage(BufferedImage image) {
        if (image == null || !isTesseractConfigured()) {
            return "";
        }
        File tempFile = null;
        try {
            tempFile = File.createTempFile("ocr_input_", ".png");
            ImageIO.write(image, "png", tempFile);

            String datapath = getVerifiedTessdataPath();
            try {
                Class<?> clazz = Class.forName("net.sourceforge.tess4j.Tesseract");
                Object tesseract = clazz.getDeclaredConstructor().newInstance();
                if (datapath != null) {
                    Method setDatapath = clazz.getMethod("setDatapath", String.class);
                    setDatapath.invoke(tesseract, datapath);
                }
                Method doOCR = clazz.getMethod("doOCR", File.class);
                Object res = doOCR.invoke(tesseract, tempFile);
                if (res != null) {
                    return ((String) res).trim();
                }
            } catch (ClassNotFoundException e) {
                return extractViaCli(tempFile, datapath);
            }
            return "";
        } catch (Throwable t) {
            return "";
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    tempFile.delete();
                } catch (Exception ignored) {}
            }
        }
    }

    private String extractViaCli(File imageFile, String datapath) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("tesseract");
            cmd.add(imageFile.getAbsolutePath());
            cmd.add("stdout");
            if (datapath != null && !datapath.isBlank()) {
                cmd.add("--tessdata-dir");
                cmd.add(datapath);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                return sb.toString().trim();
            }
        } catch (Throwable ignored) {
            return "";
        }
    }

    public boolean isTesseractConfigured() {
        return getVerifiedTessdataPath() != null;
    }

    private String getVerifiedTessdataPath() {
        List<String> candidates = List.of(
                System.getenv("TESSDATA_PREFIX") != null ? System.getenv("TESSDATA_PREFIX") : "",
                System.getProperty("tessdata.prefix") != null ? System.getProperty("tessdata.prefix") : "",
                "tessdata",
                Path.of(System.getProperty("user.home"), ".freshledger", "tessdata").toString(),
                "C:\\Program Files\\Tesseract-OCR\\tessdata",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tessdata"
        );
        for (String p : candidates) {
            if (!p.isBlank()) {
                try {
                    Path path = Path.of(p);
                    if (Files.isDirectory(path) && (Files.exists(path.resolve("eng.traineddata")) || Files.exists(path.resolve("osd.traineddata")))) {
                        return path.toAbsolutePath().toString();
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Visual feature comparison between uploaded image and catalog images.
     */
    public record VisualMatch(BillingDataController.ProductView product, double similarity) {}

    public List<VisualMatch> findVisualMatches(BufferedImage queryImage) {
        List<BillingDataController.ProductView> products = getAllActiveProducts();
        long queryHash = computeDifferenceHash(queryImage);
        double[] queryHist = computeColorHistogram(queryImage);

        List<VisualMatch> matches = new ArrayList<>();
        for (BillingDataController.ProductView p : products) {
            BufferedImage catalogImage = loadCatalogImage(p);
            if (catalogImage == null) continue;

            long catalogHash = computeDifferenceHash(catalogImage);
            double[] catalogHist = computeColorHistogram(catalogImage);

            double hashSimilarity = 1.0 - (hammingDistance(queryHash, catalogHash) / 64.0);
            double histSimilarity = histogramIntersection(queryHist, catalogHist);

            // Combined similarity score [0.0 - 1.0]
            double score = (hashSimilarity * 0.4) + (histSimilarity * 0.6);
            if (score > 0.55) {
                matches.add(new VisualMatch(p, score));
            }
        }

        matches.sort(Comparator.comparingDouble(VisualMatch::similarity).reversed());
        return matches;
    }

    private BufferedImage loadCatalogImage(BillingDataController.ProductView p) {
        String path = p.imagePath();
        if (path == null) return null;

        try {
            if (path.startsWith("/product-images/")) {
                String fileName = path.substring("/product-images/".length());
                Path filePath = Path.of(System.getProperty("user.home"), ".freshledger", "product-images", fileName);
                if (Files.exists(filePath)) {
                    return ImageIO.read(filePath.toFile());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static long computeDifferenceHash(BufferedImage img) {
        BufferedImage small = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, 9, 8, null);
        g.dispose();

        long hash = 0L;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = small.getRGB(x, y) & 0xFF;
                int right = small.getRGB(x + 1, y) & 0xFF;
                if (left > right) {
                    hash |= (1L << (y * 8 + x));
                }
            }
        }
        return hash;
    }

    private static int hammingDistance(long h1, long h2) {
        return Long.bitCount(h1 ^ h2);
    }

    private static double[] computeColorHistogram(BufferedImage img) {
        // 4x4x4 RGB color bins = 64 bins
        double[] hist = new double[64];
        int w = img.getWidth();
        int h = img.getHeight();
        int step = Math.max(1, Math.max(w, h) / 100);

        int total = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int r = ((rgb >> 16) & 0xFF) / 64;
                int g = ((rgb >> 8) & 0xFF) / 64;
                int b = (rgb & 0xFF) / 64;
                int bin = (r * 16) + (g * 4) + b;
                if (bin >= 0 && bin < 64) {
                    hist[bin]++;
                    total++;
                }
            }
        }

        if (total > 0) {
            for (int i = 0; i < 64; i++) {
                hist[i] /= total;
            }
        }
        return hist;
    }

    private static double histogramIntersection(double[] h1, double[] h2) {
        double intersection = 0.0;
        for (int i = 0; i < Math.min(h1.length, h2.length); i++) {
            intersection += Math.min(h1[i], h2[i]);
        }
        return intersection;
    }

    public BillingDataController.ProductView findProductByBarcode(String barcode) {
        List<BillingDataController.ProductView> list = jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE barcode = ? AND active = TRUE
                """, PRODUCT_MAPPER, barcode.trim());
        return list.isEmpty() ? null : list.get(0);
    }

    public List<BillingDataController.ProductView> getAllActiveProducts() {
        return jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE active = TRUE
                """, PRODUCT_MAPPER);
    }

    private Optional<BillingDataController.ProductView> findProductById(List<BillingDataController.ProductView> list, long id) {
        return list.stream().filter(p -> p.id() == id).findFirst();
    }

    private static BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    private static BufferedImage rotateImage(BufferedImage src, int angle) {
        double radians = Math.toRadians(angle);
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage rotated = new BufferedImage(w, h, src.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : src.getType());
        Graphics2D g2d = rotated.createGraphics();
        g2d.rotate(radians, w / 2.0, h / 2.0);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return rotated;
    }

    private static void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please upload a photo of the product.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Photo must be 5 MB or smaller.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("image/") && !lower.equals("application/octet-stream")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file format. Upload a JPG, PNG, or WebP photo.");
            }
        }
    }
}
