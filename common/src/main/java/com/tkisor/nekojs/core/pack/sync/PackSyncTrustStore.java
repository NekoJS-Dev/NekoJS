package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Client-side trust store for multi-player pack distribution:
 * {@code nekojs/config/trusted-servers.json}.
 *
 * <pre>{@code
 * {
 *   "trustedServers": { "<sha256(address)>": { "serverAddress": "...", "trustedAt": "..." } },
 *   "trustedKeys":    { "<keyId>": { "publicKey": "...", "fingerprint": "...",
 *                                    "serverAddress": "...", "trustedAt": "..." } }
 * }
 * }</pre>
 *
 * <p>bucket is the sha256 hex of the server address after trim and lowercasing.
 * {@code /nekojs trust <address>} writes trustedServers. v1 semantics: trusting a server means
 * trusting its current signing key. The first trusted run pins the signing public key from the
 * pack into trustedKeys; from then on the same keyId coming with a different key is rejected by
 * {@link PackSignatureVerifier} (constant-time byte comparison).
 *
 * <p>An instance can be given a file path (for tests); the static {@link #get()} follows
 * {@link NekoJSPaths}. A read failure (corrupt JSON and the like) logs a WARN and is treated as
 * an empty store; a write failure logs a WARN and does not throw, because a trust operation must
 * not kill the network thread.
 */
public final class PackSyncTrustStore {

    public static final String FILE_NAME = "trusted-servers.json";

    private static volatile PackSyncTrustStore instance;

    private final Path file;

    public PackSyncTrustStore(Path file) {
        this.file = file;
    }

    public static PackSyncTrustStore get() {
        PackSyncTrustStore store = instance;
        if (store == null) {
            synchronized (PackSyncTrustStore.class) {
                store = instance;
                if (store == null) {
                    instance = store = new PackSyncTrustStore(
                        NekoJSPaths.get().config().resolve(FILE_NAME));
                }
            }
        }
        return store;
    }

    /** For tests: resets the default instance. The next get() rebuilds it from the current
     * NekoJSPaths. */
    static synchronized void resetForTest() {
        instance = null;
    }

    /** Maps a server address to its bucket (sha256 hex). The address is trimmed and lowercased before
     * hashing. */
    public static String bucketFor(String serverAddress) {
        String normalized = serverAddress == null ? "" : serverAddress.trim().toLowerCase();
        return PackHasher.toHex(PackHasher.sha256().digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

    public synchronized boolean isServerTrusted(String bucket) {
        return readServers().has(bucket);
    }

    /** Trusts a server address: writes bucket → {serverAddress, trustedAt}. */
    public synchronized void trustServer(String serverAddress) {
        String address = serverAddress.trim();
        JsonObject root = readRoot();
        JsonObject servers = root.getAsJsonObject("trustedServers");
        if (servers == null) {
            servers = new JsonObject();
            root.add("trustedServers", servers);
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("serverAddress", address);
        entry.addProperty("trustedAt", Instant.now().toString());
        servers.add(bucketFor(address), entry);
        writeRoot(root);
    }

    /** Returns the pinned signing public key (base64 text) for a keyId, or null when the key is not
     * pinned. */
    public synchronized String trustedPublicKey(String keyId) {
        JsonObject keys = readKeys();
        JsonElement entry = keys.get(keyId);
        if (entry == null || !entry.isJsonObject()) return null;
        JsonElement publicKey = entry.getAsJsonObject().get("publicKey");
        return publicKey != null && publicKey.isJsonPrimitive() && publicKey.getAsJsonPrimitive().isString()
            ? publicKey.getAsString()
            : null;
    }

    /** Pins a signing public key: {publicKey, fingerprint, serverAddress, trustedAt}. */
    public synchronized void trustPublicKey(String keyId, String publicKey, String fingerprint, String serverAddress) {
        JsonObject root = readRoot();
        JsonObject keys = root.getAsJsonObject("trustedKeys");
        if (keys == null) {
            keys = new JsonObject();
            root.add("trustedKeys", keys);
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("publicKey", publicKey);
        entry.addProperty("fingerprint", fingerprint);
        entry.addProperty("serverAddress", serverAddress);
        entry.addProperty("trustedAt", Instant.now().toString());
        keys.add(keyId, entry);
        writeRoot(root);
    }

    /** Capture the persisted trust file before a bundle transaction mutates key pinning. */
    synchronized byte[] snapshotBytes() throws java.io.IOException {
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    /** Restore the persisted trust file after a rejected physical/runtime replacement. */
    synchronized void restoreBytes(byte[] snapshot) throws java.io.IOException {
        if (snapshot == null) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".rollback.tmp");
        try {
            Files.write(temp, snapshot);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private JsonObject readServers() {
        JsonObject servers = readRoot().getAsJsonObject("trustedServers");
        return servers != null ? servers : new JsonObject();
    }

    private JsonObject readKeys() {
        JsonObject keys = readRoot().getAsJsonObject("trustedKeys");
        return keys != null ? keys : new JsonObject();
    }

    private JsonObject readRoot() {
        if (!Files.isRegularFile(file)) return new JsonObject();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to read pack sync trust store {}: {}", file, e.toString());
            return new JsonObject();
        }
    }

    private void writeRoot(JsonObject root) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to write pack sync trust store {}: {}", file, e.toString());
        }
    }
}
