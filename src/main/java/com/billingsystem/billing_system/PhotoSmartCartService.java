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
import java.util.Set;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PhotoSmartCartService {

    private static final long MAX_PHOTO_BYTES = 10 * 1024 * 1024; // 10 MB

    public record PhotoRecognitionResult(
            boolean success,
            String method, // "BARCODE", "OCR", "VISUAL_SIMILARITY", "NONE"
            String detectedBarcode,
            String extractedText,
            BillingDataController.ProductView product,
            List<BillingDataController.ProductView> candidates,
            boolean ambiguous,
            String feedback
    ) {}

    private final JdbcTemplate jdbc;
    private final VoiceSmartCartService voiceSmartCartService;

    public PhotoSmartCartService(JdbcTemplate jdbc, VoiceSmartCartService voiceSmartCartService) {
        this.jdbc = jdbc;
        this.voiceSmartCartService = voiceSmartCartService;
    }

    /**
     * Process an uploaded photo using Barcode -> OCR -> Visual Similarity pipeline.
     */
    public PhotoRecognitionResult recognizeProduct(MultipartFile file) {
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
                return new PhotoRecognitionResult(
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

        // 2. Stage B: OCR text extraction
        String ocrText = extractTextFromImage(image);
        if (ocrText != null && !ocrText.isBlank()) {
            List<BillingDataController.ProductView> listMatches = new ArrayList<>();
            Set<Long> seenIds = new HashSet<>();

            // Check line by line for shopping list items (e.g. "milk\nbread\negg")
            String[] lines = ocrText.split("[\\r\\n]+");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.length() >= 2) {
                    List<BillingDataController.ProductView> matches = voiceSmartCartService.findMatchingProducts(trimmed);
                    for (BillingDataController.ProductView p : matches) {
                        if (seenIds.add(p.id())) {
                            listMatches.add(p);
                        }
                    }
                }
            }

            // If line-by-line didn't match, search whole text
            if (listMatches.isEmpty()) {
                listMatches = voiceSmartCartService.findMatchingProducts(ocrText);
            }

            if (!listMatches.isEmpty()) {
                BillingDataController.ProductView top = listMatches.get(0);
                boolean ambiguous = listMatches.size() > 1;
                String feedback = ambiguous
                        ? "Identified " + listMatches.size() + " product(s) from image. Select items to add to cart."
                        : "Identified from packaging text: " + top.name();
                return new PhotoRecognitionResult(
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
            return new PhotoRecognitionResult(
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
                  + "' or configure TESSDATA_PREFIX. You can also search or use Voice Smart Cart.";

        return new PhotoRecognitionResult(
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
     * Decode 1D or 2D barcode using ZXing.
     */
    public String decodeBarcode(BufferedImage image) {
        if (image == null) return null;

        // Try standard image
        String result = tryDecodeBitmap(image);
        if (result != null) return result;

        // Try pre-processed grayscale and binarized variants
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
     * Safely checks for traineddata presence and catches any native JNA crashes (e.g. Invalid memory access).
     */
    public String extractTextFromImage(BufferedImage image) {
        if (image == null || !isTesseractConfigured()) {
            return "";
        }
        File tempFile = null;
        try {
            tempFile = File.createTempFile("ocr_input_", ".png");
            ImageIO.write(image, "png", tempFile);

            Tesseract tesseract = new Tesseract();
            String datapath = getVerifiedTessdataPath();
            if (datapath != null) {
                tesseract.setDatapath(datapath);
            }
            return tesseract.doOCR(tempFile).trim();
        } catch (Throwable t) {
            // Safely catch any UnsatisfiedLinkError, java.lang.Error (such as Invalid memory access), or Exception
            return "";
        } finally {
            if (tempFile != null && tempFile.exists()) {
                try {
                    tempFile.delete();
                } catch (Exception ignored) {}
            }
        }
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

    public boolean isTesseractConfigured() {
        return getVerifiedTessdataPath() != null;
    }

    /**
     * Visual feature comparison between uploaded image and catalog images.
     */
    private record VisualMatch(BillingDataController.ProductView product, double similarity) {}

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
        int step = Math.max(1, Math.max(w, h) / 100); // sample for speed

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

    private BillingDataController.ProductView findProductByBarcode(String barcode) {
        List<BillingDataController.ProductView> list = jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE barcode = ? AND active = TRUE
                """, (rs, row) -> new BillingDataController.ProductView(
                rs.getLong("id"), rs.getString("name"), rs.getString("category"), rs.getString("brand"),
                rs.getString("barcode"), rs.getBigDecimal("purchase_price"), rs.getBigDecimal("price"),
                rs.getInt("stock"), rs.getInt("min_stock"),
                rs.getInt("stock") == 0 ? "Out of Stock" : (rs.getInt("stock") <= rs.getInt("min_stock") ? "Low Stock" : "In Stock"),
                rs.getString("image_path") == null
                        ? ProductImageLibrary.match(rs.getString("name"), rs.getString("category"))
                        : rs.getString("image_path")
        ), barcode.trim());
        return list.isEmpty() ? null : list.get(0);
    }

    private List<BillingDataController.ProductView> getAllActiveProducts() {
        return jdbc.query("""
                SELECT id, name, category, brand, barcode, purchase_price, price, stock, min_stock, image_path
                FROM products WHERE active = TRUE
                """, (rs, row) -> new BillingDataController.ProductView(
                rs.getLong("id"), rs.getString("name"), rs.getString("category"), rs.getString("brand"),
                rs.getString("barcode"), rs.getBigDecimal("purchase_price"), rs.getBigDecimal("price"),
                rs.getInt("stock"), rs.getInt("min_stock"),
                rs.getInt("stock") == 0 ? "Out of Stock" : (rs.getInt("stock") <= rs.getInt("min_stock") ? "Low Stock" : "In Stock"),
                rs.getString("image_path") == null
                        ? ProductImageLibrary.match(rs.getString("name"), rs.getString("category"))
                        : rs.getString("image_path")
        ));
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
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Photo must be 10 MB or smaller.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("image/") && !lower.equals("application/octet-stream")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file format. Upload a JPG, PNG, or WebP photo.");
            }
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
