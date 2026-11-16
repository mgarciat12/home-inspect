package com.homeinspect.app.photo;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    final PhotoProperties properties = new PhotoProperties(DataSize.ofMegabytes(15), 1600, 400, 0.85f, "");
    final ImageProcessor processor = new ImageProcessor(properties, new HeicConverter(properties));

    @Test
    void appliesTheExifOrientationAndRemovesAllMetadata() {
        byte[] input = TestImages.jpegWithExif(TestImages.halves(300, 200), 6);
        assertThat(ImageProcessor.exifOrientation(input)).isEqualTo(6);

        ImageProcessor.ProcessedImage result = processor.process(input);

        // Rotated 90º clockwise: the red left half is now on top
        assertThat(result.width()).isEqualTo(200);
        assertThat(result.height()).isEqualTo(300);
        BufferedImage output = TestImages.read(result.optimized());
        assertThat(isRed(output.getRGB(100, 40))).isTrue();
        assertThat(isRed(output.getRGB(100, 260))).isFalse();
        // No EXIF (and so no GPS) in the stored files
        assertThat(new String(result.optimized(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        assertThat(new String(result.thumbnail(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        assertThat(ImageProcessor.exifOrientation(result.optimized())).isEqualTo(1);
    }

    @Test
    void resizesLargePhotosAndTheThumbnail() {
        ImageProcessor.ProcessedImage result = processor.process(TestImages.jpeg(TestImages.halves(4000, 3000)));
        assertThat(result.width()).isEqualTo(1600);
        assertThat(result.height()).isEqualTo(1200);
        BufferedImage thumbnail = TestImages.read(result.thumbnail());
        assertThat(thumbnail.getWidth()).isEqualTo(400);
        assertThat(thumbnail.getHeight()).isEqualTo(300);
    }

    @Test
    void neverEnlargesSmallPhotos() {
        ImageProcessor.ProcessedImage result = processor.process(TestImages.jpeg(TestImages.halves(800, 600)));
        assertThat(result.width()).isEqualTo(800);
        assertThat(TestImages.read(result.thumbnail()).getWidth()).isEqualTo(400);
    }

    @Test
    void convertsPngWithTransparencyToJpeg() {
        BufferedImage transparent = new BufferedImage(50, 40, BufferedImage.TYPE_INT_ARGB);
        ImageProcessor.ProcessedImage result = processor.process(TestImages.png(transparent));
        assertThat(ImageType.detect(result.optimized())).contains(ImageType.JPEG);
        assertThat(new Color(TestImages.read(result.optimized()).getRGB(10, 10))).isEqualTo(Color.WHITE);
    }

    @Test
    void rejectsFilesThatAreNotImagesWhateverTheirName() {
        assertThatThrownBy(() -> processor.process("<script>alert(1)</script>".getBytes()))
                .isInstanceOf(ImageRejectedException.class).hasMessage("photo.error.type");
        byte[] truncated = java.util.Arrays.copyOf(TestImages.jpeg(TestImages.halves(100, 100)), 40);
        assertThatThrownBy(() -> processor.process(truncated))
                .isInstanceOf(ImageRejectedException.class).hasMessage("photo.error.invalid");
    }

    @Test
    void heicWithoutConverterIsRejectedWithAClearMessage() {
        byte[] heic = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        assertThat(ImageType.detect(heic)).contains(ImageType.HEIC);
        assertThatThrownBy(() -> processor.process(heic))
                .isInstanceOf(ImageRejectedException.class).hasMessage("photo.error.heic");
    }

    @Test
    void enforcesTheMaximumFileSize() {
        PhotoProperties small = new PhotoProperties(DataSize.ofBytes(100), 1600, 400, 0.85f, "");
        ImageProcessor strict = new ImageProcessor(small, new HeicConverter(small));
        assertThatThrownBy(() -> strict.process(TestImages.jpeg(TestImages.halves(200, 200))))
                .isInstanceOf(ImageRejectedException.class).hasMessage("photo.error.tooLarge");
    }

    /** Every EXIF orientation against its definition, pixel by pixel. */
    @Test
    void allEightOrientations() {
        int w = 3;
        int h = 2;
        BufferedImage source = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                source.setRGB(x, y, (x * 40 + 10) << 16 | (y * 100 + 10) << 8);
            }
        }
        for (int orientation = 1; orientation <= 8; orientation++) {
            BufferedImage result = ImageProcessor.orient(source, orientation);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int[] d = target(orientation, x, y, w, h);
                    assertThat(result.getRGB(d[0], d[1]) & 0xFFFFFF)
                            .as("orientation %d, pixel %d,%d", orientation, x, y)
                            .isEqualTo(source.getRGB(x, y) & 0xFFFFFF);
                }
            }
        }
    }

    /** Where source pixel (x, y) must end up for the given EXIF orientation. */
    private static int[] target(int orientation, int x, int y, int w, int h) {
        return switch (orientation) {
            case 2 -> new int[]{w - 1 - x, y};
            case 3 -> new int[]{w - 1 - x, h - 1 - y};
            case 4 -> new int[]{x, h - 1 - y};
            case 5 -> new int[]{y, x};
            case 6 -> new int[]{h - 1 - y, x};
            case 7 -> new int[]{h - 1 - y, w - 1 - x};
            case 8 -> new int[]{y, w - 1 - x};
            default -> new int[]{x, y};
        };
    }

    private static boolean isRed(int rgb) {
        Color color = new Color(rgb);
        return color.getRed() > 200 && color.getBlue() < 60;
    }
}
