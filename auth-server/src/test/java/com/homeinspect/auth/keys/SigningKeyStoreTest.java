package com.homeinspect.auth.keys;

import com.nimbusds.jose.jwk.JWKSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigningKeyStoreTest {

    @TempDir
    Path dir;

    @Test
    void generatesTheKeyOnceAndReloadsTheSameOne() throws Exception {
        JWKSet first = new SigningKeyStore(dir, "a-long-password").loadOrCreate();
        Path file = dir.resolve(SigningKeyStore.GENERATED_FILE);
        assertThat(file).exists();
        // Stored encrypted: the private exponent is not readable in the file
        assertThat(Files.readString(file)).doesNotContain("\"d\"");

        JWKSet second = new SigningKeyStore(dir, "a-long-password").loadOrCreate();
        assertThat(second.getKeys().getFirst().getKeyID()).isEqualTo(first.getKeys().getFirst().getKeyID());
        assertThat(second.getKeys().getFirst().isPrivate()).isTrue();
    }

    @Test
    void wrongPasswordFailsInsteadOfGeneratingANewKey() {
        new SigningKeyStore(dir, "a-long-password").loadOrCreate();
        assertThatThrownBy(() -> new SigningKeyStore(dir, "another-password").loadOrCreate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_KEYSTORE_PASSWORD");
    }
}
