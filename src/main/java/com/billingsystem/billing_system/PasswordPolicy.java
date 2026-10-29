package com.billingsystem.billing_system;

import java.nio.charset.StandardCharsets;

final class PasswordPolicy {
    private static final int CUSTOMER_MINIMUM_LENGTH = 8;
    private static final int STAFF_MINIMUM_LENGTH = 12;
    private static final int MAXIMUM_UTF8_BYTES = 72;

    private PasswordPolicy() {}

    static int minimumLength(String role) {
        return "CUSTOMER".equals(role) ? CUSTOMER_MINIMUM_LENGTH : STAFF_MINIMUM_LENGTH;
    }

    static boolean isValid(String password, String role) {
        return isValid(password, minimumLength(role));
    }

    static boolean isValid(String password, int minimumLength) {
        return password != null
                && password.length() >= minimumLength
                && password.getBytes(StandardCharsets.UTF_8).length <= MAXIMUM_UTF8_BYTES;
    }
}