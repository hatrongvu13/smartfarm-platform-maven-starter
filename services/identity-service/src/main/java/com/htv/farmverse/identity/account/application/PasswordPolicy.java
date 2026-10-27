package com.htv.farmverse.identity.account.application;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final int MINIMUM_CHARACTERS = 12;
    public static final int MAXIMUM_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static int utf8Length(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length;
    }

    public static boolean hasSupportedLength(String password) {
        return password != null
                && password.length() >= MINIMUM_CHARACTERS
                && utf8Length(password) <= MAXIMUM_UTF8_BYTES;
    }
}
