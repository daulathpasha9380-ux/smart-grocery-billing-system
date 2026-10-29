package com.billingsystem.billing_system;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductImageService {
    private static final long MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private static final int THUMBNAIL_EDGE = 512;

    private final Path storageDirectory;

    public ProductImageService(@Value("${app.product-images.directory}") String directory) {
        storageDirectory = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Product image storage could not be initialized.", exception);
        }
    }

    Path storageDirectory() {
        return storageDirectory;
    }

    String store(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a product image to upload.");
        }
        if (image.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Product images must be 5 MB or smaller.");
        }

        Path storedPath = null;
        try (ImageInputStream input = ImageIO.createImageInputStream(image.getInputStream())) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String extension = switch (format) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png" -> "png";
                    default -> throw invalidImage();
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Product images must be no larger than 20 megapixels.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalidImage();
                }
                BufferedImage thumbnail = resize(decoded, extension.equals("png"));
                storedPath = storageDirectory.resolve(UUID.randomUUID() + "." + extension);
                if (!ImageIO.write(thumbnail, extension.equals("png") ? "png" : "jpeg", storedPath.toFile())) {
                    throw new IOException("No image writer is available for the uploaded format.");
                }
                return "/product-images/" + storedPath.getFileName();
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(storedPath);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is not a valid image.", exception);
        }
    }

    void deleteManagedImage(String imagePath) {
        if (imagePath == null || !imagePath.startsWith("/product-images/")) return;
        String fileName = imagePath.substring("/product-images/".length());
        Path candidate = storageDirectory.resolve(fileName).normalize();
        if (!candidate.getParent().equals(storageDirectory)) return;
        try {
            Files.deleteIfExists(candidate);
        } catch (IOException exception) {
            throw new IllegalStateException("The previous product image could not be removed.", exception);
        }
    }

    private static BufferedImage resize(BufferedImage original, boolean preserveAlpha) {
        double scale = Math.min(1d, (double) THUMBNAIL_EDGE / Math.max(original.getWidth(), original.getHeight()));
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        int type = preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage thumbnail = new BufferedImage(width, height, type);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!preserveAlpha) {
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return thumbnail;
    }

    private static ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid PNG or JPEG image.");
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("The invalid uploaded image could not be removed.", exception);
        }
    }
}
