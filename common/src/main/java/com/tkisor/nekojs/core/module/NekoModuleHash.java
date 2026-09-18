package com.tkisor.nekojs.core.module;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Shared content hashing for module identity and cache stamps. */
public final class NekoModuleHash {
    private NekoModuleHash() {}

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the JDK; an absent implementation is an environment failure.
            throw new IllegalStateException("SHA-256 digest is not available on this JVM", e);
        }
    }
}
