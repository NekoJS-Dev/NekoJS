package com.tkisor.nekojs.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 19 AC1/AC8 的配置期桥 fixture（纯文件读，全部节点同跑）：NeoForge
 * {@code PackSyncConfigurationTask} 与 fabric {@code FabricPackSync.PushTask} 两条配置期
 * 桥的推送次序必须由共享核（{@code PackSyncServer}）单点决定——先收集 gather、先发
 * 哈希清单，bundle 仅在 {@code PackSyncServer.shouldSendBundle} 放行后紧随发送；
 * 接收侧的远端地址 → bucket 信任输入归一（{@code PackSyncClient.normalizeRemoteAddress}）
 * 与 CLIENT 重载（{@code root.reload(ScriptType.CLIENT)}）同样只走共享路线。
 *
 * <p>删除条件（AC8）：loader 侧此前各自复制的 hashOnly/非空判定与 InetSocketAddress
 * 解析路线，在两侧行为等价（共享核单点 + 两桥委托）验证后删除；本 trace 即验证面——
 * 桥内不允许再出现本地重复判定。
 */
class PackSyncBridgeSourceTraceTest {

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /** 只保留代码行（去掉 javadoc/行注释形态的行）：符号出现在注释里不是接线引用。 */
    private static String codeLinesOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String sharedMain(String relative) {
        return codeLinesOnly(read(repoRoot().resolve(relative)));
    }

    private static String fabricRaw(String relative) {
        return codeLinesOnly(read(repoRoot().resolve("src/fabric/java").resolve(relative)));
    }

    /** 断言一条配置期推送桥的共享次序：gather → 哈希清单先行 → bundle 经共享判定门后随发。 */
    private static void assertPushOrderSharesCoreDecision(
            String bridgeCode, String bridgeName, String hashListSendMarker) {
        int gather = bridgeCode.indexOf("PackSyncServer.collectSyncPacks()");
        int hashList = bridgeCode.indexOf("PackHashListPayload.of(packs)");
        int hashListSend = bridgeCode.indexOf(hashListSendMarker);
        int bundleSend = bridgeCode.indexOf("PackBundlePayload.of(packs)");
        assertTrue(gather >= 0, bridgeName + " must gather packs through the shared core");
        assertTrue(hashList > gather, bridgeName + " must build the hash list from the shared gather");
        assertTrue(hashListSend > hashList, bridgeName + " must send the hash list first");
        assertTrue(bundleSend > hashListSend,
                bridgeName + " must send the bundle only after the hash list");
        int gate = bridgeCode.indexOf("PackSyncServer.shouldSendBundle(");
        assertTrue(gate > hashListSend && gate < bundleSend,
                bridgeName + " must gate the bundle behind the shared PackSyncServer.shouldSendBundle decision");
        assertFalse(bridgeCode.contains("PackSyncServer.hashOnly()"),
                bridgeName + " must not keep a local hashOnly copy of the bundle decision");
    }

    /** 断言一条接收桥：payload → 共享管线 + Outcome 断连 + 共享地址归一，无本地重复路线。 */
    private static void assertReceiveRouteSharesCore(String bridgeCode, String bridgeName) {
        assertTrue(bridgeCode.contains("PackSyncClient.handleHashList("),
                bridgeName + " must route hash lists through the shared client pipeline");
        assertTrue(bridgeCode.contains("PackSyncClient.handleBundle("),
                bridgeName + " must route bundles through the shared client pipeline");
        assertTrue(bridgeCode.contains("outcome.shouldDisconnect()"),
                bridgeName + " must surface pipeline rejections as disconnects");
        assertTrue(bridgeCode.contains("PackSyncClient.normalizeRemoteAddress("),
                bridgeName + " must resolve the trust bucket address through the shared normalizer");
        assertFalse(bridgeCode.contains("InetSocketAddress"),
                bridgeName + " must not keep a duplicated remote-address resolution route");
    }

    @Test
    void neoforgeConfigurationBridgeSharesTheCorePushOrderAndReceiveRoute() {
        assertPushOrderSharesCoreDecision(
                sharedMain("src/main/java/com/tkisor/nekojs/network/PackSyncConfigurationTask.java"),
                "the NeoForge configuration task", "listener.send(hashes)");
        assertReceiveRouteSharesCore(
                sharedMain("src/main/java/com/tkisor/nekojs/network/PackSyncMessageHandler.java"),
                "the NeoForge receiver");
    }

    @Test
    void fabricConfigurationBridgeSharesTheCorePushOrderAndReceiveRoute() {
        String fabricPackSync = fabricRaw("com/tkisor/nekojs/fabric/FabricPackSync.java");
        assertPushOrderSharesCoreDecision(fabricPackSync, "the fabric push task",
                "ServerConfigurationNetworking.send(handler, hashes)");
        assertReceiveRouteSharesCore(fabricPackSync, "the fabric receiver");
    }

    @Test
    void bothBridgesUseTheSameConfigurationTaskTypeId() {
        String neoforge = sharedMain("src/main/java/com/tkisor/nekojs/network/PackSyncConfigurationTask.java");
        String fabric = fabricRaw("com/tkisor/nekojs/fabric/FabricPackSync.java");
        assertTrue(neoforge.contains("PackSyncServer.TASK_ID"));
        assertTrue(fabric.contains("PackSyncServer.TASK_ID"),
                "both configuration tasks must derive their task identity from the shared core id");
    }

    @Test
    void bothClientReloadHooksRouteThroughTheRuntimeRoot() {
        String neoforge = sharedMain("src/main/java/com/tkisor/nekojs/network/PackSyncClientConnections.java");
        String fabric = fabricRaw("com/tkisor/nekojs/fabric/FabricPackSync.java");
        assertTrue(neoforge.contains("root.reload(ScriptType.CLIENT)"),
                "the NeoForge reload hook must reload CLIENT through the runtime root");
        assertTrue(fabric.contains("root.reload(ScriptType.CLIENT)"),
                "the fabric reload hook must reload CLIENT through the runtime root");
    }

    @Test
    void bothReceiversUseTheSharedMainThreadLatchProtocol() {
        String neoforge = sharedMain("src/main/java/com/tkisor/nekojs/network/PackSyncMessageHandler.java");
        String fabric = fabricRaw("com/tkisor/nekojs/fabric/FabricPackSync.java");
        assertTrue(neoforge.contains("PackSyncClient.prepareMainThreadWork()"));
        assertTrue(fabric.contains("PackSyncClient.prepareMainThreadWork()"),
                "both receivers must use the shared main-thread latch protocol");
    }

    /** 共享核心的单点存在性：bundle 判定与地址归一在 common 定义，桥只消费。 */
    @Test
    void sharedCoreOwnsTheBundleDecisionAndAddressNormalization() {
        String server = sharedMain("common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncServer.java");
        String client = sharedMain("common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java");
        assertTrue(server.contains("public static boolean shouldSendBundle("),
                "PackSyncServer must own the config-phase bundle decision");
        assertTrue(client.contains("public static String normalizeRemoteAddress("),
                "PackSyncClient must own the remote address → trust bucket normalization");
    }

    /** 桥与共享核的接线点数量是有限集：main 源里 shouldSendBundle 调用恰两处（两桥各一）。 */
    @Test
    void bundleDecisionHasExactlyOneCallSitePerBridge() throws IOException {
        int calls = 0;
        List<Path> roots = List.of(
                repoRoot().resolve("src/main/java"),
                repoRoot().resolve("src/fabric/java"),
                repoRoot().resolve("common/src/main/java"));
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String code = codeLinesOnly(read(file));
                    int index = 0;
                    while ((index = code.indexOf("PackSyncServer.shouldSendBundle(", index)) >= 0) {
                        calls++;
                        index += 1;
                    }
                }
            }
        }
        assertTrue(calls >= 2, "both bridges must call the shared decision (found " + calls + ")");
        assertTrue(calls <= 3, "no third push route may appear (found " + calls + ")");
    }
}
