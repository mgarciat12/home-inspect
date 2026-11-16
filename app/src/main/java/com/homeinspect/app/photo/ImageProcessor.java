package com.homeinspect.app.photo;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Turns an uploaded photo into the two files that are stored:
 * <ul>
 *     <li>an optimized JPEG (longest side {@code optimized-size}, 1600 px by default) for the app and the PDF,</li>
 *     <li>a thumbnail (400 px).</li>
 * </ul>
 * The EXIF orientation is applied to the pixels and the output is re-encoded without any metadata,
 * so GPS position and camera data are never served. Images are never enlarged.
 */
@Component
public class ImageProcessor {

    /** Refuse decompression bombs (a 48 MP phone photo is far below). */
    private static final long MAX_PIXELS = 200_000_000L;

    private final PhotoProperties properties;
    private final HeicConverter heicConverter;

    public ImageProcessor(PhotoProperties properties, HeicConverter heicConverter) {
        this.properties = properties;
        this.heicConverter = heicConverter;
    }

    public record ProcessedImage(byte[] optimized, byte[] thumbnail, int width, int height) {
    }

    public ProcessedImage process(byte[] data) {
        if (data.length > properties.maxFileSize().toBytes()) {
            throw new ImageRejectedException("photo.error.tooLarge");
        }
        ImageType type = ImageType.detect(data).orElseThrow(() -> new ImageRejectedException("photo.error.type"));
        int orientation = 1;
        if (type == ImageType.HEIC) {
            data = heicConverter.toPng(data); // rotation already applied by libheif
        } else {
            orientation = exifOrientation(data);
        }
        BufferedImage image = orient(decode(data, properties.optimizedSize()), orientation);
        BufferedImage optimized = fit(image, properties.optimizedSize());
        BufferedImage thumbnail = fit(optimized, properties.thumbnailSize());
        return new ProcessedImage(jpeg(optimized), jpeg(thumbnail), optimized.getWidth(), optimized.getHeight());
    }

    /** PNG keeping transparency (signatures, logo), longest side {@code maxSize}. */
    public byte[] processPng(byte[] data, int maxSize) {
        if (data.length > properties.maxFileSize().toBytes()) {
            throw new ImageRejectedException("photo.error.tooLarge");
        }
        ImageType type = ImageType.detect(data).orElseThrow(() -> new ImageRejectedException("photo.error.type"));
        if (type == ImageType.HEIC) {
            data = heicConverter.toPng(data);
        }
        int orientation = type == ImageType.JPEG ? exifOrientation(data) : 1;
        BufferedImage image = fit(orient(decode(data, maxSize), orientation), maxSize);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ImageRejectedException("photo.error.invalid", e);
        }
    }

    // ---- steps ----

    /** Decodes with subsampling for very large images, so a 48 MP photo does not need 200 MB of heap. */
    private static BufferedImage decode(byte[] data, int targetSize) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ImageRejectedException("photo.error.type");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new ImageRejectedException("photo.error.tooLarge");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / (targetSize * 2));
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImageRejectedException rejected) {
                throw rejected;
            }
            throw new ImageRejectedException("photo.error.invalid", e);
        }
    }

    static int exifOrientation(byte[] data) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception ignored) {
            // No readable metadata: keep the pixels as they are
        }
        return 1;
    }

    /** Applies one of the 8 EXIF orientations. */
    static BufferedImage orient(BufferedImage image, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = orientation >= 5;
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(-Math.PI / 2); t.scale(-1, 1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { }
        }
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage result = new BufferedImage(swap ? h : w, swap ? w : h, type);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, t, null);
        g.dispose();
        return result;
    }

    /** Scales down so that the longest side is at most {@code max}; never enlarges. */
    private static BufferedImage fit(BufferedImage image, int max) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= max) {
            return image;
        }
        try {
            return Thumbnails.of(image).size(max, max).keepAspectRatio(true).asBufferedImage();
        } catch (IOException e) {
            throw new ImageRejectedException("photo.error.invalid", e);
        }
    }

    /** Baseline JPEG without metadata; transparency is flattened on white. */
    private byte[] jpeg(BufferedImage image) {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(properties.jpegQuality());
            writer.write(null, new IIOImage(rgb, null, null), param);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new ImageRejectedException("photo.error.invalid", e);
        } finally {
            writer.dispose();
        }
    }
}
