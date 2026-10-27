package com.htv.farmverse.identity.mfa.crypto;

public interface SecretCipher {
    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
