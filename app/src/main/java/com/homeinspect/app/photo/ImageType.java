package com.homeinspect.app.photo;

import java.util.Optional;

/** Image formats accepted on upload, detected from the content (magic bytes), not the file name. */
public enum ImageType {
    JPEG, PNG, HEIC;

    public static Optional<ImageType> detect(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return Optional.of(PNG);
        }
        // ISO BMFF: "ftyp" box at offset 4 with a HEIF brand
        if (data.length >= 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') {
            String brand = new String(data, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.equals("heic") || brand.equals("heix") || brand.equals("heim") || brand.equals("heis")
                    || brand.equals("hevc") || brand.equals("hevx") || brand.equals("mif1") || brand.equals("msf1")) {
                return Optional.of(HEIC);
            }
        }
        return Optional.empty();
    }
}
