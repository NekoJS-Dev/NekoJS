package com.tkisor.nekojs.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source trace for registration timing and for the loader's explicit payload subset (plain file
 * reads, so every node runs it):
 *
 * <ul>
 *   <li>Platform network registration happens only at a loader's own initialization entry:
 *       NeoForge's {@code RegisterPayloadHandlersEvent} subscriber ({@code NekoJSNetwork}) and
 *       the fabric entrypoints ({@code NekoJSFabricMod#onInitialize} /
 *       {@code NekoJSFabricClient}). {@code PlayPacketDispatchers.install} appears exactly twice
 *       in the main sources, once per loader, and the reload and command files contain no call
 *       that registers a payload. Together these pin that property across repeated
 *       SERVER/CLIENT reloads: the reload path registers nothing, and the lifecycle tests plus
 *       this routing fixture never touch the dispatcher. The platform event firing only once is
 *       a loader rule pinned by characterization.</li>
 *   <li>The fabric network side is an explicit subset: it registers exactly script_payload
 *       (both directions), client_data_sync, pdata_sync, pack_hashes and pack_bundle, and
 *       {@code ShowErrorListPacket} (error panel / display) has zero references under
 *       {@code src/fabric}. The fabric side does not imitate editor, dashboard or client
 *       display parity.</li>
 * </ul>
 *
 * <p><b>Later addition (2026-09-29, platform wiring review)</b>: the NeoForge side registers a
 * seventh payload type, {@code nekojs:dynamic_registry_sync} (both directions, batch
 * transactions for the dynamic registry), at the same {@code RegisterPayloadHandlersEvent}
 * point, through the {@code McPlatformCompat.Impl#registerDynamicSyncPayload} facade (the 26.x
 * implementation registers it; the 1.21.1 default is empty, since that node has no dynamic
 * registry). Same channel, same registration point, purely additional type. The fabric explicit
 * subset stays at 6 calls / 5 types.
 */
class NetworkRegistrationSourceTraceTest {

    // ---- Locating the repo root (a node test runs with CWD versions/<node>; walk up to the controller script) ----

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

    /** Keeps code lines only, dropping javadoc and line-comment lines: a class name inside a comment
     * is not a wiring reference. */
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

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    /** Lists the java files under every main source root: shared src/main, common main, the fabric root
     * and every node main tree. */
    private static List<Path> mainJavaFiles() {
        Path root = repoRoot();
        Stream<Path> shared = Stream.of(root.resolve("src/main/java"));
        Stream<Path> common = Stream.of(root.resolve("common/src/main/java"));
        Stream<Path> fabric = Stream.of(root.resolve("src/fabric/java"));
        Stream<Path> nodes = java.util.Arrays.stream(root.resolve("versions").toFile().listFiles())
                .filter(nodeDir -> nodeDir.isDirectory()
                        && Files.isDirectory(nodeDir.toPath().resolve("src/main/java")))
                .map(nodeDir -> nodeDir.toPath().resolve("src/main/java"));
        List<Path> roots = Stream.concat(Stream.concat(Stream.concat(shared, common), fabric), nodes).toList();
        return roots.stream()
                .filter(Files::isDirectory)
                .flatMap(dir -> {
                    try (Stream<Path> walk = Files.walk(dir)) {
                        return walk.filter(p -> p.toString().endsWith(".java")).toList().stream();
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot walk " + dir, e);
                    }
                })
                .toList();
    }

    // ---- Registration happens only at the loader's own initialization ----

    @Test
    void neoforgeRegistrationLivesInLoaderEventSubscriber() {
        String source = read(repoRoot().resolve("src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java"));
        assertTrue(source.contains("@EventBusSubscriber(modid = NekoJS.MODID"),
                "payload registration must stay in the FML event subscriber (loader-native timing)");
        assertTrue(source.contains("RegisterPayloadHandlersEvent"),
                "payload registration must hook the loader's payload registration event");
        assertEquals(1, countOccurrences(source, "PlayPacketDispatchers.install("),
                "the dispatcher is assembled exactly once in the NeoForge network entry");
    }

    /** Pins the registration direction of each payload on the NeoForge side: S2C, bidirectional, or configuration-phase S2C. */
    @Test
    void neoforgePayloadDirectionsStayOnLegacyRegistration() {
        String source = read(repoRoot().resolve("src/main/java/com/tkisor/nekojs/network/NekoJSNetwork.java"));
        List<String> expectedDirections = List.of(
                "registrar.playToClient(ShowErrorListPacket.TYPE",
                "registrar.playToClient(PDataSyncPacket.TYPE",
                "registrar.playToClient(ClientDataSyncPacket.TYPE",
                "registrar.configurationToClient(PackHashListPayload.TYPE",
                "registrar.configurationToClient(PackBundlePayload.TYPE");
        for (String line : expectedDirections) {
            assertTrue(source.contains(line), "NeoForge registration line missing: " + line);
        }
        // The script custom channel registers in both directions through the version compat facade
        // (26.x uses the 4-arg playBidirectional, 1.21.1 the 3-arg form plus a flow check). The
        // exact shape is pinned by ScriptPayloadRegistrationShapeTest; this pins that it does go
        // through the facade.
        assertTrue(source.contains("McPlatformCompat.get().registerScriptPayload(registrar)"),
                "the bidirectional script payload must be registered through the version compat facade");
        // The dynamic registry batch-transaction payload registers through the compat facade at the
        // same loader event and on the same channel, so there is no second registration point
        // (26.x registers it, 1.21.1 defaults to an empty implementation). The wire golden lives
        // in DynamicSyncPayloadWireFormatTest.
        assertTrue(source.contains("McPlatformCompat.get().registerDynamicSyncPayload(registrar)"),
                "the dynamic registry sync payload must register at the same single loader event"
                        + " through the version compat facade (no second registration point)");
    }

    /** Pins the registration direction of the explicit fabric subset. */
    @Test
    void fabricPayloadDirectionsStayOnLegacyRegistration() {
        Path root = repoRoot();
        String play = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java"));
        assertTrue(play.contains("PayloadTypeRegistry.serverboundPlay().register("),
                "script channel must stay serverbound-registered on fabric");
        assertTrue(play.contains("PayloadTypeRegistry.clientboundPlay().register("),
                "script channel / client data must stay clientbound-registered on fabric");
        String pdata = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPDataSync.java"));
        assertTrue(pdata.contains("PayloadTypeRegistry.clientboundPlay().register("),
                "pdata sync must stay clientbound on fabric");
        String pack = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPackSync.java"));
        assertEquals(2, countOccurrences(pack, "PayloadTypeRegistry.clientboundConfiguration().register("),
                "pack sync payloads must stay configuration-phase clientbound on fabric");
    }

    @Test
    void dispatcherInstallAppearsExactlyOncePerLoaderInMainSources() {
        int installs = 0;
        for (Path file : mainJavaFiles()) {
            installs += countOccurrences(read(file), "PlayPacketDispatchers.install(");
        }
        assertEquals(2, installs,
                "expected exactly two dispatcher installs in main sources (NeoForge NekoJSNetwork + FabricPlayNetwork)");
    }

    @Test
    void fabricRegistrationRunsOnceFromLoaderEntrypoints() {
        Path root = repoRoot();
        String mod = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricMod.java"));
        assertEquals(1, countOccurrences(mod, "FabricPlayNetwork.registerServer();"),
                "FabricPlayNetwork.registerServer must be called exactly once (mod entrypoint)");
        assertTrue(mod.indexOf("void onInitialize()") < mod.indexOf("FabricPlayNetwork.registerServer();"),
                "the single registerServer call must live inside onInitialize");

        String client = read(root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricClient.java"));
        assertEquals(1, countOccurrences(client, "FabricPlayNetwork.registerClient();"),
                "FabricPlayNetwork.registerClient must be called exactly once (client entrypoint)");
    }

    @Test
    void reloadAndCommandPathsContainNoPayloadRegistration() {
        // Entry points a reload can reach: the command surface (three nodes) and the lifecycle owners
        // (root and manager). A platform registration call in any of these files means a reload
        // registers a second time, and the test fails.
        Path root = repoRoot();
        List<Path> reloadFacing = List.of(
                root.resolve("src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java"),
                root.resolve("common/src/main/java/com/tkisor/nekojs/core/lifecycle/NekoRuntimeRoot.java"),
                root.resolve("common/src/main/java/com/tkisor/nekojs/script/ScriptManager.java"));
        for (Path path : reloadFacing) {
            String code = codeLinesOnly(read(path));
            for (String token : List.of("PlayPacketDispatchers.install", "PayloadTypeRegistry",
                    "registerGlobalReceiver", "playBidirectional", "playToClient", "configurationToClient")) {
                assertFalse(code.contains(token),
                        () -> path.getFileName() + " must not contain network registration call '" + token + "'");
            }
        }
    }

    // ---- The explicit fabric subset ----

    @Test
    void fabricRegistersExactlyTheExplicitPayloadSubset() {
        Path root = repoRoot();
        List<Path> fabricFiles;
        try (Stream<Path> walk = Files.walk(root.resolve("src/fabric/java"))) {
            fabricFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertFalse(fabricFiles.isEmpty());

        int registryCalls = 0;
        for (Path file : fabricFiles) {
            String source = read(file);
            // Count call sites only ("PayloadTypeRegistry."), not import lines
            registryCalls += countOccurrences(source, "PayloadTypeRegistry.");
        }
        // FabricPlayNetwork(3: client_data_sync and script_payload in both directions) + FabricPDataSync(1)
        // + FabricPackSync(2: pack_hashes/pack_bundle) = 6 registration calls for 5 payload types
        assertEquals(6, registryCalls,
                "fabric payload type registrations must stay the explicit six calls / five types");
    }

    @Test
    void fabricOmitsEditorDashboardAndDisplayPackets() {
        Path root = repoRoot();
        try (Stream<Path> walk = Files.walk(root.resolve("src/fabric"))) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeLinesOnly(read(file));
                assertFalse(code.contains("ShowErrorListPacket"),
                        () -> "display-domain packet leaked into fabric face: " + file);
                assertFalse(code.contains("ScriptSyncFiles"),
                        () -> "editor sync packet leaked into fabric face: " + file);
                assertFalse(code.contains("FetchScriptContent"),
                        () -> "editor fetch packet leaked into fabric face: " + file);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fabricScriptChannelReceiversHopMainThreadBeforeNeutralDispatch() {
        String source = read(repoRoot().resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java"));
        // Both receivers, server and client, must hop to the platform main thread before entering the
        // neutral dispatch core.
        Pattern receiverHop = Pattern.compile(
                "context\\.(server|client)\\(\\)\\.execute\\(\\(\\) ->\\s*[^;]*NetworkMessageHandler\\.post(Server|Client)Event");
        Matcher matcher = receiverHop.matcher(source);
        int hopped = 0;
        while (matcher.find()) {
            hopped++;
        }
        assertEquals(2, hopped,
                "both fabric script-channel receivers must hop the platform main thread before neutral dispatch");
        assertEquals(2, countOccurrences(source, "NetworkMessageHandler.post"),
                "every dispatch into NetworkMessageHandler on fabric must be inside a main-thread hop");
    }
}
