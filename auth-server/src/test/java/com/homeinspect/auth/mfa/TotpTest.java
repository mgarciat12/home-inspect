package com.homeinspect.auth.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B (SHA-1 seed), last 6 digits. */
    static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfcTestVectors() {
        assertThat(RFC_SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.code(RFC_SECRET, Totp.step(59))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1111111109))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(1234567890))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(2000000000))).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfClockDriftAndRejectsReplays() {
        long now = 1_700_000_000L;
        String previous = Totp.code(RFC_SECRET, Totp.step(now) - 1);
        assertThat(Totp.verify(RFC_SECRET, previous, now, null)).hasValue(Totp.step(now) - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, Totp.step(now) - 3), now, null)).isEmpty();
        // Already used step (or older): refused
        String current = Totp.code(RFC_SECRET, Totp.step(now));
        assertThat(Totp.verify(RFC_SECRET, current, now, Totp.step(now))).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "12 34 5", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
    }

    @Test
    void newSecretsAreRandomAndDecodable() {
        String a = Totp.newSecret();
        assertThat(a).hasSize(32).isNotEqualTo(Totp.newSecret());
        assertThat(Totp.decodeBase32(a)).hasSize(20);
    }
}
