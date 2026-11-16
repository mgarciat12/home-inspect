package com.homeinspect.auth.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.OptionalLong;

/** RFC 6238 time-based one-time passwords (SHA-1, 6 digits, 30 s), as used by authenticator apps. */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long PERIOD_SECONDS = 30;
    /** Accepted clock drift: one step before / after. */
    static final int WINDOW = 1;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** New 160-bit secret, Base32 encoded (what the user types or scans). */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long step(long epochSeconds) {
        return Math.floorDiv(epochSeconds, PERIOD_SECONDS);
    }

    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Step matched by {@code code} around {@code now}, if any, and only if it is later than
     * {@code lastUsedStep} (a code cannot be replayed).
     */
    public static OptionalLong verify(String base32Secret, String code, long epochSeconds, Long lastUsedStep) {
        if (code == null) {
            return OptionalLong.empty();
        }
        String digits = code.replaceAll("\\s", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = step(epochSeconds);
        for (long candidate = current - WINDOW; candidate <= current + WINDOW; candidate++) {
            if ((lastUsedStep == null || candidate > lastUsedStep)
                    && java.security.MessageDigest.isEqual(code(base32Secret, candidate).getBytes(), digits.getBytes())) {
                return OptionalLong.of(candidate);
            }
        }
        return OptionalLong.empty();
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] decodeBase32(String text) {
        String clean = text.replaceAll("[\\s=-]", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return out.array();
    }
}
