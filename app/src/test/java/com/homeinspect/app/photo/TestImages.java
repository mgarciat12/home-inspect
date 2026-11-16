package com.homeinspect.app.photo;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Builds test images: JPEG / PNG, optionally with an EXIF segment (orientation + GPS). */
public final class TestImages {

    private TestImages() {
    }

    /** Left half red, right half blue. */
    public static BufferedImage halves(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.BLUE);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return image;
    }

    public static byte[] jpeg(BufferedImage image) {
        return encode(image, "jpeg");
    }

    public static byte[] png(BufferedImage image) {
        return encode(image, "png");
    }

    /** JPEG with an APP1/EXIF segment holding the orientation and a GPS latitude. */
    public static byte[] jpegWithExif(BufferedImage image, int orientation) {
        byte[] jpeg = jpeg(image);
        ByteBuffer tiff = ByteBuffer.allocate(56); // big endian ("MM")
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        // IFD0 at 8: Orientation + GPS IFD pointer
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(38);
        tiff.putInt(0);
        // GPS IFD at 38: GPSLatitudeRef = "N"
        tiff.putShort((short) 1);
        tiff.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0).putShort((short) 0);
        tiff.putInt(0);

        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + exifHeader.length + tiff.capacity();
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + 2 + length);
        out.put(jpeg, 0, 2); // SOI
        out.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(exifHeader).put(tiff.array());
        out.put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    public static BufferedImage read(byte[] data) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
