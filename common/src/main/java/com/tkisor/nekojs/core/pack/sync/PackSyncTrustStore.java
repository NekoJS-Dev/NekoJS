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
 * 多人包分发的客户端信任存储：{@code nekojs/config/trusted-servers.json}。
 *
 * <pre>{@code
 * {
 *   "trustedServers": { "<sha256(地址)>": { "serverAddress": "...", "trustedAt": "..." } },
 *   "trustedKeys":    { "<keyId>": { "publicKey": "...", "fingerprint": "...",
 *                                    "serverAddress": "...", "trustedAt": "..." } }
 * }
 * }</pre>
 *
 * <p>bucket = 服务器地址（trim + 小写）的 sha256 十六进制。{@code /nekojs trust <address>}
 * 写入 trustedServers；v1 语义：信任服务器即信任其当前签名密钥——首次受信执行时把包内
 * 签名公钥 pinning 进 trustedKeys，此后同 keyId 换钥会被 {@link PackSignatureVerifier}
 * 拒绝（常量时间字节比较）。
 *
 * <p>实例可注入文件路径（测试用）；静态 {@link #get()} 跟随 {@link NekoJSPaths}。
 * 读失败（损坏 JSON 等）WARN 并按空存储处理；写失败 WARN 不抛（信任操作不允许炸掉网络线程）。
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

    /** 测试用：重置默认实例（下次 get() 按当前 NekoJSPaths 重建）。 */
    static synchronized void resetForTest() {
        instance = null;
    }

    /** 服务器地址 → bucket（sha256 十六进制）。地址 trim + 小写后哈希。 */
    public static String bucketFor(String serverAddress) {
        String normalized = serverAddress == null ? "" : serverAddress.trim().toLowerCase();
        return PackHasher.toHex(PackHasher.sha256().digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

    public synchronized boolean isServerTrusted(String bucket) {
        return readServers().has(bucket);
    }

    /** 信任服务器地址（写入 bucket → {serverAddress, trustedAt}）。 */
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

    /** 按 keyId 查询 pinning 的签名公钥（base64 文本）；未 pinning 返回 null。 */
    public synchronized String trustedPublicKey(String keyId) {
        JsonObject keys = readKeys();
        JsonElement entry = keys.get(keyId);
        if (entry == null || !entry.isJsonObject()) return null;
        JsonElement publicKey = entry.getAsJsonObject().get("publicKey");
        return publicKey != null && publicKey.isJsonPrimitive() && publicKey.getAsJsonPrimitive().isString()
            ? publicKey.getAsString()
            : null;
    }

    /** pinning 签名公钥（{publicKey, fingerprint, serverAddress, trustedAt}）。 */
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
