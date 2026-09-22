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
 * Config-phase bridge fixture for ticket 19 AC1/AC8 (pure file reads, runs on every node):
 * the push order of the NeoForge {@code PackSyncConfigurationTask} and the fabric
 * {@code FabricPackSync.PushTask} configuration bridges must be decided by a single shared
 * core point ({@code PackSyncServer}) — gather first, hash list sent first, and the bundle
 * sent only after {@code PackSyncServer.shouldSendBundle} allows it; on the receive side the
 * remote-address → trust-bucket normalization ({@code PackSyncClient.normalizeRemoteAddress})
 * and the CLIENT reload ({@code root.reload(ScriptType.CLIENT)}) likewise go only through the
 * shared routes.
 *
 * <p>Deletion condition (AC8): the hashOnly/emptiness checks and the InetSocketAddress
 * resolution routes previously duplicated per loader side were deleted after equivalence on
 * both sides (shared-core single point + both bridges delegating) was verified; this trace is
 * that verification surface — no local duplicated decision may reappear inside a bridge.
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

    /** Keep code lines only (drop javadoc and line-comment forms): a symbol inside a comment is not a wiring reference. */
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

    /** Assert one config-phase push bridge's shared ordering: gather → hash list first → bundle after the shared decision gate. */
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

    /** Assert one receive bridge: payload → shared pipeline + Outcome disconnect + shared address normalization, no local duplicated route. */
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

    /** Single-point existence of the shared core: the bundle decision and address normalization are defined in common; bridges only consume them. */
    @Test
    void sharedCoreOwnsTheBundleDecisionAndAddressNormalization() {
        String server = sharedMain("common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncServer.java");
        String client = sharedMain("common/src/main/java/com/tkisor/nekojs/core/pack/sync/PackSyncClient.java");
        assertTrue(server.contains("public static boolean shouldSendBundle("),
                "PackSyncServer must own the config-phase bundle decision");
        assertTrue(client.contains("public static String normalizeRemoteAddress("),
                "PackSyncClient must own the remote address → trust bucket normalization");
    }

    /** The bridge-to-core wiring set is finite: exactly one shouldSendBundle call site per bridge in main sources. */
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
