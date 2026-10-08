//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 21 platform wiring source-trace fixture (ticket 17 trace style, plain file
 * reads): pins the wiring discipline so later changes cannot bend the dynamic registry
 * sync face —
 *
 * <ul>
 *   <li><b>register-once</b>: the common-phase registration of
 *       {@code DynamicRegistrySyncPacket} lives only in the two 26.x node compat impls
 *       (Nf261/Nf262PlatformCompat) — zero registration calls in the shared
 *       assembly/event/reload faces;</li>
 *   <li><b>receive-side hop</b>: both handlers (DynamicRegistrySyncWire.handleOnServer /
 *       DynamicRegistryClientSync.handleOnClient) must {@code enqueueWork} onto the
 *       platform main thread before touching the coordinator/participant (both are
 *       owner-thread-only by contract);</li>
 *   <li><b>no second channel / no second model</b>: protocol messages are encoded by
 *       common's {@code DynamicSyncWireCodec} into a single JSON body on the existing
 *       payload — the dynamic package registers no custom channel and hand-rolls no
 *       second wire encoding.</li>
 * </ul>
 */
class DynamicSyncWiringSourceTraceTest {

    // ---- repo root location (node test CWD = versions/<node>; walk up to the controller script) ----

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

    /** Keeps code lines only (drops javadoc/line-comment shapes). */
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

    // ---- register-once: registration lives only in the two node compat impls ----

    @Test
    void syncPayloadRegistrationLivesExactlyInTheTwoNodeCompatImpls() {
        Path root = repoRoot();
        int registrations = 0;
        List<Path> roots = List.of(
                root.resolve("src/main/java"),
                root.resolve("common/src/main/java"),
                root.resolve("versions/26.1.2/src/main/java"),
                root.resolve("versions/26.2.0/src/main/java"));
        for (Path dir : roots) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String code = codeLinesOnly(read(file));
                    registrations += (int) java.util.regex.Pattern.compile(
                            "(?:play|common)Bidirectional\\(\\s*DynamicRegistrySyncPacket\\.TYPE")
                            .matcher(code).results().count();
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        // Exactly one registration per node compat impl (the payload class's own TYPE is a declaration, not counted here)
        assertEquals(2, registrations,
                "the dynamic sync payload registers exactly in Nf261/Nf262 compat impls");
        String nf261 = read(root.resolve(
                "versions/26.1.2/src/main/java/com/tkisor/nekojs/platform/compat/Nf261PlatformCompat.java"));
        String nf262 = read(root.resolve(
                "versions/26.2.0/src/main/java/com/tkisor/nekojs/platform/compat/Nf262PlatformCompat.java"));
        // Script messages remain play-only; dynamic state must arrive before frozen-registry sync.
        for (String node : List.of(nf261, nf262)) {
            String code = codeLinesOnly(node);
            assertEquals(1, countOccurrences(code, "playBidirectional("));
            assertEquals(1, countOccurrences(code, "commonBidirectional("));
        }
    }

    @Test
    void assemblyAndEventFacesContainNoRegistrationCalls() {
        Path root = repoRoot();
        List<Path> wiringFaces = List.of(
                root.resolve("src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistrySyncWire.java"),
                root.resolve("src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistryClientSync.java"),
                root.resolve("src/main/java/com/tkisor/nekojs/dynamic/NeoForgeDynamicSyncTransport.java"),
                root.resolve("src/main/java/com/tkisor/nekojs/dynamic/NeoForgeDynamicRegistryAdapter.java"),
                root.resolve("src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistryFacade.java"));
        for (Path path : wiringFaces) {
            String code = codeLinesOnly(read(path));
            for (String token : List.of("playBidirectional(", "commonBidirectional(", "PayloadTypeRegistry",
                    "registerGlobalReceiver", "playToClient(", "configurationToClient(")) {
                assertFalse(code.contains(token),
                        () -> path.getFileName() + " must not register payloads itself"
                                + " (registration stays in the loader-native compat entry): '" + token + "'");
            }
        }
    }

    // ---- receive-side hop: both handlers enqueueWork before entering the owner-thread-only state machines ----

    @Test
    void bothReceiveHandlersHopThePlatformMainThreadBeforeTouchingTheProtocolState() {
        Path root = repoRoot();
        String serverHandler = read(root.resolve(
                "src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistrySyncWire.java"));
        assertTrue(serverHandler.contains("context.enqueueWork(() ->"),
                "the server receive handler must hop enqueueWork before the coordinator call");
        String clientHandler = read(root.resolve(
                "src/main/java/com/tkisor/nekojs/dynamic/DynamicRegistryClientSync.java"));
        assertTrue(clientHandler.contains("context.enqueueWork(() ->"),
                "the client receive handler must hop enqueueWork before the participant call");
    }

    // ---- no second model: wire encoding goes through the single common codec ----

    @Test
    void wireEncodingGoesThroughTheSingleCommonCodec() {
        Path root = repoRoot();
        try (Stream<Path> walk = Files.walk(root.resolve("src/main/java/com/tkisor/nekojs/dynamic"))) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = codeLinesOnly(read(file));
                assertEquals(0, countOccurrences(code, "new JsonObject"),
                        () -> file.getFileName() + " must not hand-roll a second JSON wire encoding");
                assertTrue(code.contains("DynamicSyncWireCodec.encode")
                        || !code.contains("DynamicRegistrySyncPacket(")
                        || file.getFileName().toString().equals("DynamicRegistrySyncWire.java")
                        || file.getFileName().toString().equals("DynamicRegistryClientSync.java"),
                        () -> file.getFileName() + " sends payloads only through the common codec");
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
//?}
//?}
