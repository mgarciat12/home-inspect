package com.homeinspect.auth.mfa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.homeinspect.auth.config.AuthProperties;
import com.homeinspect.auth.user.AuthUser;
import com.homeinspect.auth.user.AuthUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Two-step verification of the users: enrollment, verification at login and reset.
 * Secrets are encrypted at rest with AES-GCM (key derived from AUTH_KEYSTORE_PASSWORD).
 */
@Slf4j
@Service
public class TotpService {

    /** Fixed salt: the key strength comes from the keystore password (PBKDF2). */
    private static final String SALT = "9f3a6c2e81d4b07a";

    private final AuthUserRepository users;
    private final AuthProperties properties;
    private final TextEncryptor encryptor;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public TotpService(AuthUserRepository users, AuthProperties properties, JdbcTemplate jdbcTemplate, Clock clock) {
        this.users = users;
        this.properties = properties;
        this.encryptor = Encryptors.delux(properties.keys().password(), SALT);
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public boolean active() {
        return properties.mfa().active();
    }

    public boolean required() {
        return properties.mfa().required();
    }

    /** otpauth:// URI for the authenticator app. */
    public String otpauthUri(String username, String secret) {
        String issuer = properties.mfa().issuerName();
        String label = URLEncoder.encode(issuer + ":" + username, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
                + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=" + Totp.DIGITS + "&period=" + Totp.PERIOD_SECONDS;
    }

    /** QR code (PNG) of the otpauth URI. */
    public byte[] qrPng(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 260, 260,
                    Map.of(EncodeHintType.MARGIN, 2));
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Activates the verification once the user proves the app is set up with a valid code. */
    @Transactional
    public boolean enable(String username, String secret, String code) {
        AuthUser user = load(username);
        OptionalLong step = Totp.verify(secret, code, clock.instant().getEpochSecond(), null);
        if (step.isEmpty()) {
            return false;
        }
        user.setTotpSecret(encryptor.encrypt(secret));
        user.setTotpEnabled(true);
        user.setTotpLastStep(step.getAsLong());
        log.info("Two-step verification enabled for '{}'", user.getUsername());
        return true;
    }

    /** Code typed at login; a used code cannot be replayed. */
    @Transactional
    public boolean verify(String username, String code) {
        AuthUser user = load(username);
        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            return false;
        }
        OptionalLong step = Totp.verify(encryptor.decrypt(user.getTotpSecret()), code,
                clock.instant().getEpochSecond(), user.getTotpLastStep());
        step.ifPresent(user::setTotpLastStep);
        return step.isPresent();
    }

    @Transactional
    public void disable(String username) {
        clear(load(username));
    }

    /** Admin: removes the verification of a user (lost phone) and ends their sessions. */
    @Transactional
    public void reset(long userId) {
        clear(users.findById(userId).orElseThrow());
    }

    private void clear(AuthUser user) {
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpLastStep(null);
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", user.getUsername());
        log.info("Two-step verification removed for '{}'", user.getUsername());
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String username) {
        return users.findByUsernameIgnoreCase(username).map(AuthUser::isTotpEnabled).orElse(false);
    }

    private AuthUser load(String username) {
        return users.findByUsernameIgnoreCase(username).orElseThrow();
    }
}
