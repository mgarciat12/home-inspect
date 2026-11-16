package com.homeinspect.auth.keys;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.PasswordBasedDecrypter;
import com.nimbusds.jose.crypto.PasswordBasedEncrypter;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Persistent RSA keys used to sign the JWTs, so tokens stay valid across container restarts.
 * <ol>
 *     <li>If {@code <dir>/jwt-signing.p12} exists (a keystore you created with keytool), its RSA key is used.</li>
 *     <li>Otherwise {@code <dir>/jwks.jwe} is used: a JWK set encrypted with the keystore password
 *         (PBES2 + AES-GCM). It is generated on the first start.</li>
 * </ol>
 */
@Slf4j
public final class SigningKeyStore {

    public static final String KEYSTORE_FILE = "jwt-signing.p12";
    public static final String GENERATED_FILE = "jwks.jwe";

    private final Path dir;
    private final char[] password;

    public SigningKeyStore(Path dir, String password) {
        Assert.notNull(dir, "The keys directory is required (AUTH_KEYS_DIR)");
        Assert.hasText(password, "The keystore password is required (AUTH_KEYSTORE_PASSWORD)");
        Assert.isTrue(password.length() >= 8, "AUTH_KEYSTORE_PASSWORD must have at least 8 characters");
        this.dir = dir;
        this.password = password.toCharArray();
    }

    public JWKSet loadOrCreate() {
        try {
            Path keystore = dir.resolve(KEYSTORE_FILE);
            if (Files.isRegularFile(keystore)) {
                return loadKeystore(keystore);
            }
            Path generated = dir.resolve(GENERATED_FILE);
            if (Files.isRegularFile(generated)) {
                JWKSet set = decrypt(Files.readString(generated, StandardCharsets.US_ASCII));
                log.info("JWT signing key loaded from {} (kid {})", generated, set.getKeys().getFirst().getKeyID());
                return set;
            }
            return generate(generated);
        } catch (IOException | GeneralKeyException e) {
            throw new IllegalStateException("Cannot load the JWT signing keys from " + dir + ": " + e.getMessage(), e);
        }
    }

    private JWKSet loadKeystore(Path file) throws IOException, GeneralKeyException {
        try (InputStream in = Files.newInputStream(file)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(in, password);
            List<JWK> keys = JWKSet.load(keyStore, alias -> password).getKeys().stream()
                    .filter(key -> key instanceof RSAKey && key.isPrivate())
                    .toList();
            if (keys.isEmpty()) {
                throw new GeneralKeyException("the keystore has no RSA private key");
            }
            log.info("JWT signing key loaded from keystore {} (kid {})", file, keys.getFirst().getKeyID());
            return new JWKSet(keys);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralKeyException(e.getMessage(), e);
        }
    }

    private JWKSet generate(Path target) throws IOException, GeneralKeyException {
        try {
            RSAKey key = new RSAKeyGenerator(2048)
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .issueTime(Date.from(Instant.now()))
                    .generate();
            JWKSet set = new JWKSet(key);
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "jwks", ".tmp");
            restrictPermissions(temp);
            Files.writeString(temp, encrypt(set), StandardCharsets.US_ASCII);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("New JWT signing key generated and stored in {} (kid {})", target, key.getKeyID());
            return set;
        } catch (JOSEException e) {
            throw new GeneralKeyException(e.getMessage(), e);
        }
    }

    private String encrypt(JWKSet set) throws JOSEException {
        JWEObject jwe = new JWEObject(
                new JWEHeader(JWEAlgorithm.PBES2_HS512_A256KW, EncryptionMethod.A256GCM),
                new Payload(set.toString(false)));
        jwe.encrypt(new PasswordBasedEncrypter(new String(password), 16, 310_000));
        return jwe.serialize();
    }

    private JWKSet decrypt(String serialized) throws GeneralKeyException {
        try {
            JWEObject jwe = JWEObject.parse(serialized.trim());
            jwe.decrypt(new PasswordBasedDecrypter(new String(password)));
            return JWKSet.parse(jwe.getPayload().toString());
        } catch (ParseException | JOSEException e) {
            throw new GeneralKeyException("wrong AUTH_KEYSTORE_PASSWORD or corrupted file (" + e.getMessage() + ")", e);
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX file system: nothing to do
        }
    }

    private static final class GeneralKeyException extends Exception {
        GeneralKeyException(String message) {
            super(message);
        }

        GeneralKeyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
