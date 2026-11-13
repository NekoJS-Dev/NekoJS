package com.tkisor.nekojs.core.node;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Host backend for node:crypto: randomUUID / randomBytes / createHash / createHmac /
 * timingSafeEqual.
 *
 * <p>Only pure computation primitives are exposed, with no I/O and no key material written to disk;
 * certificate and key file APIs, as well as network APIs, are outside the script sandbox.
 */
public final class NekoNodeCrypto {
    private final SecureRandom random = new SecureRandom();

    public String randomUUID() {
        return UUID.randomUUID().toString();
    }

    public NekoNodeBuffer randomBytes(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative: " + size);
        }
        NekoNodeBuffer.checkAllocSize(size);
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return new NekoNodeBuffer(bytes);
    }

    public NekoNodeHash createHash(String algorithm) {
        return new NekoNodeHash(digestAlgorithm(algorithm));
    }

    public NekoNodeHmac createHmac(String algorithm, NekoNodeBuffer key) {
        return new NekoNodeHmac(hmacAlgorithm(algorithm), key == null ? new byte[0] : key.bytes());
    }

    public boolean timingSafeEqual(NekoNodeBuffer a, NekoNodeBuffer b) {
        if (a == null || b == null || a.length() != b.length()) {
            throw new IllegalArgumentException("Input buffers must have the same length");
        }
        return MessageDigest.isEqual(a.bytes(), b.bytes());
    }

    static String digestAlgorithm(String algorithm) {
        String name = String.valueOf(algorithm == null ? "" : algorithm).toLowerCase().replace("-", "");
        return switch (name) {
            case "md5" -> "MD5";
            case "sha1" -> "SHA-1";
            case "sha224" -> "SHA-224";
            case "sha256" -> "SHA-256";
            case "sha384" -> "SHA-384";
            case "sha512" -> "SHA-512";
            case "sha3256" -> "SHA3-256";
            case "sha3512" -> "SHA3-512";
            default -> throw new IllegalArgumentException("Unsupported digest algorithm: " + algorithm);
        };
    }

    static String hmacAlgorithm(String algorithm) {
        String name = String.valueOf(algorithm == null ? "" : algorithm).toLowerCase().replace("-", "");
        return switch (name) {
            case "md5" -> "HmacMD5";
            case "sha1" -> "HmacSHA1";
            case "sha224" -> "HmacSHA224";
            case "sha256" -> "HmacSHA256";
            case "sha384" -> "HmacSHA384";
            case "sha512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException("Unsupported HMAC algorithm: " + algorithm);
        };
    }

    /**
     * The hash object: update calls chain to accumulate input and digest returns the output, after
     * which the state resets, matching Node.
     */
    public static final class NekoNodeHash {
        private final MessageDigest digest;

        NekoNodeHash(String algorithm) {
            try {
                this.digest = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Unsupported digest algorithm: " + algorithm, e);
            }
        }

        public NekoNodeHash updateBuffer(NekoNodeBuffer data) {
            digest.update(data == null ? new byte[0] : data.bytes());
            return this;
        }

        public NekoNodeHash updateString(String data, String encoding) {
            Charset charset = NekoNodeBuffer.charset(encoding);
            digest.update(String.valueOf(data == null ? "" : data).getBytes(charset));
            return this;
        }

        public NekoNodeBuffer digestBuffer() {
            return new NekoNodeBuffer(digest.digest());
        }

        public String digestString(String encoding) {
            byte[] out = digest.digest();
            return new NekoNodeBuffer(out).toString(encoding);
        }
    }

    /** The hmac object: the key is fixed at construction and update/digest behave like hash. */
    public static final class NekoNodeHmac {
        private final Mac mac;

        NekoNodeHmac(String algorithm, byte[] key) {
            try {
                this.mac = Mac.getInstance(algorithm);
                this.mac.init(new SecretKeySpec(key, algorithm));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to initialize HMAC " + algorithm, e);
            }
        }

        public NekoNodeHmac updateBuffer(NekoNodeBuffer data) {
            mac.update(data == null ? new byte[0] : data.bytes());
            return this;
        }

        public NekoNodeHmac updateString(String data, String encoding) {
            Charset charset = NekoNodeBuffer.charset(encoding);
            mac.update(String.valueOf(data == null ? "" : data).getBytes(charset));
            return this;
        }

        public NekoNodeBuffer digestBuffer() {
            return new NekoNodeBuffer(mac.doFinal());
        }

        public String digestString(String encoding) {
            return new NekoNodeBuffer(mac.doFinal()).toString(encoding);
        }
    }
}
