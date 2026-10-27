package com.htv.farmverse.identity.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsMaximumBcryptInputByUtf8ByteLength() {
        assertThat(PasswordPolicy.hasSupportedLength("a".repeat(72)))
                .isTrue();
        assertThat(PasswordPolicy.hasSupportedLength("é".repeat(36)))
                .isTrue();
    }

    @Test
    void rejectsPasswordsThatExceedBcryptInputLimitInUtf8() {
        assertThat(PasswordPolicy.hasSupportedLength("a".repeat(73)))
                .isFalse();
        assertThat(PasswordPolicy.hasSupportedLength("é".repeat(37)))
                .isFalse();
        assertThat(PasswordPolicy.hasSupportedLength("a".repeat(72) + "x"))
                .isFalse();
        assertThat(PasswordPolicy.utf8Length("é".repeat(37)))
                .isEqualTo(74);
    }

    @Test
    void enforcesMinimumCharacterLength() {
        assertThat(PasswordPolicy.hasSupportedLength("short"))
                .isFalse();
        assertThat(PasswordPolicy.hasSupportedLength("a".repeat(12)))
                .isTrue();
    }
}
