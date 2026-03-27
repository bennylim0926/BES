package com.example.BES.utils;

import java.security.SecureRandom;

public class ReferenceCodeUtil {

    private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReferenceCodeUtil() {}

    /** Generates a unique 9-character reference code in the format XXXX-XXXX. */
    public static String generate() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        sb.append('-');
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
