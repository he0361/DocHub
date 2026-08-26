package com.dochub.workbench.modelconfig.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelCredentialCipherTest {

    private final ModelCredentialCipher cipher = new ModelCredentialCipher(
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void roundTripsWithoutDeterministicCiphertext() {
        String first = cipher.encrypt("sk-secret");
        String second = cipher.encrypt("sk-secret");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("sk-secret");
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("sk-secret");

        assertThatThrownBy(() -> cipher.decrypt(encrypted + "x"))
            .isInstanceOf(IllegalStateException.class);
    }
}
