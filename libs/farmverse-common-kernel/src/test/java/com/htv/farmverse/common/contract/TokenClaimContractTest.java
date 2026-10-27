package com.htv.farmverse.common.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenClaimContractTest {

    @Test
    void supportsLegacyTokensWithoutContractVersion() {
        assertTrue(TokenClaimContract.supportsVersion(null));
    }

    @Test
    void supportsCurrentContractVersion() {
        assertTrue(TokenClaimContract.supportsVersion(1));
        assertTrue(TokenClaimContract.supportsVersion(1L));
    }

    @Test
    void rejectsUnknownOrMalformedContractVersions() {
        assertFalse(TokenClaimContract.supportsVersion(2));
        assertFalse(TokenClaimContract.supportsVersion(1.5));
        assertFalse(TokenClaimContract.supportsVersion("1"));
    }
}
