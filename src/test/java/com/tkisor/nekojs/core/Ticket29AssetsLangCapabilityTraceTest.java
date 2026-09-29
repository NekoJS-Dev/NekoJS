//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.wrapper.AssetGeneratorJS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 29 AC8 capability source-trace fixture for the assets/lang surfaces: pins, per node,
 * what actually exists so the platform differences are recorded facts instead of silent gaps —
 *
 * <ul>
 *   <li><b>event surface</b>: the five-node ticket-33 golden rows are read back (ordinary tests
 *       only read the golden, never update it): every NeoForge node carries
 *       {@code ClientEvents.generateAssets} and {@code ClientEvents.lang}; both fabric nodes
 *       carry neither (documented unavailable, no silent parity);</li>
 *   <li><b>typed {@code Assets} binding</b>: the only production registration line sits inside a
 *       {@code >=26} guard inside the NeoForge-only core plugin, so 1.21.1 evaluates it
 *       away — the binding is absent on 1.21.1 while both events remain (asserted at runtime by
 *       this fixture's 26.x leg and its 1.21.1 companion), and no fabric source tree registers
 *       the binding or bridges either event;</li>
 *   <li><b>runtime pins on the executing node</b>: this 26.x-shared fixture asserts the binding
 *       and both buses resolve through the production paths; its 1.21.1 node-local companion
 *       ({@code versions/1.21.1/src/test/.../Ticket29AssetsAbsentOn1211Test}) asserts the
 *       binding is absent there while both buses stay.</li>
 * </ul>
 *
 * <p>This fixture records the existing differences; it does not widen or narrow any platform
 * surface (fabric parity for assets/lang stays with its owners, tickets 31/32).
 */
class Ticket29AssetsLangCapabilityTraceTest {

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

    /** Keeps code lines only (drops javadoc/line-comment shapes; stonecutter markers handled separately). */
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

    // ---- stonecutter guard tracing (same evaluation shape the node preprocessors apply) ----

    private static final Pattern GUARD_OPEN = Pattern.compile("//\\? if (.+) \\{");

    /** Strips a javadoc edge so closers glued to comment ends still match (javadoc-star then marker). */
    private static String stripCommentEdge(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("*/")) {
            trimmed = trimmed.substring(2).trim();
        }
        if (trimmed.startsWith("*")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }

    /**
     * Returns the active guard expressions at the (unique) code line containing {@code needle}.
     * Openers push, closers pop, else-openers re-branch — the same nesting the per-node
     * evaluation follows when it decides which lines survive.
     */
    private static List<String> guardStackAtLine(Path file, String needle) {
        List<String> stack = new ArrayList<>();
        List<String> atNeedle = null;
        int matches = 0;
        // Marker prefix built by concatenation so this file's own stonecutter markers stay
        // the only lexical occurrences of the prefix.
        String marker = "/" + "/?";
        for (String raw : read(file).split("\n", -1)) {
            String line = stripCommentEdge(raw);
            Matcher open = GUARD_OPEN.matcher(line);
            if (open.matches()) {
                stack.add(open.group(1).trim());
                continue;
            }
            if (line.equals(marker + "} else {")) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                stack.add("<else>");
                continue;
            }
            if (line.equals(marker + "}")) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
                continue;
            }
            if (raw.trim().startsWith("*") || raw.trim().startsWith("/*") || raw.trim().startsWith("//")) {
                continue; // comment line, never code
            }
            if (raw.contains(needle)) {
                matches++;
                atNeedle = List.copyOf(stack);
            }
        }
        assertEquals(1, matches, "needle '" + needle + "' must match exactly one code line in " + file);
        return atNeedle;
    }

    // ---- 1. event surface: five-node golden readback ----

    @Test
    void eventSurfaceGoldenPinsAssetsAndLangStancePerNode() throws IOException {
        List<String[]> rows = readGoldenRows("/nekojs/platform-gates/event-surface-domains.txt");
        assertFalse(rows.isEmpty(), "the ticket-33 cross-node event surface baseline must exist");

        Set<String> clientEventsNodes = new LinkedHashSet<>();
        for (String[] row : rows) {
            String group = row[0], node = row[1], buses = row[2];
            if (!group.equals("ClientEvents")) {
                continue;
            }
            clientEventsNodes.add(node);
            List<String> names = Arrays.asList(buses.split(","));
            if (node.endsWith("-fabric")) {
                assertFalse(names.contains("generateAssets"),
                        "Fabric " + node + " explicitly has no generateAssets event (documented unavailable,"
                                + " no silent parity)");
                assertFalse(names.contains("lang"),
                        "Fabric " + node + " explicitly has no lang event (documented unavailable, no silent parity)");
            } else {
                assertEquals(1, names.stream().filter("generateAssets"::equals).count(),
                        "ClientEvents.generateAssets must be declared exactly once for " + node);
                assertEquals(1, names.stream().filter("lang"::equals).count(),
                        "ClientEvents.lang must be declared exactly once for " + node);
            }
        }
        assertEquals(Set.of("1.21.1", "26.1.2", "26.2.0", "26.1.2-fabric", "26.2.0-fabric"),
                clientEventsNodes,
                "the assets/lang capability rows must stay derived from all five baseline nodes"
                        + " (a node list change must revisit the ticket 29 capability table too)");
    }

    /** Golden row format: {@code <group> | <node> = present | buses=a,b,c}; comment lines start with #. */
    private static List<String[]> readGoldenRows(String resource) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream stream = Ticket29AssetsLangCapabilityTraceTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "golden resource missing: " + resource);
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("|")) {
                    continue;
                }
                String[] parts = trimmed.split("\\|");
                if (parts.length < 3 || !parts[2].contains("buses=")) {
                    continue;
                }
                rows.add(new String[] {
                        parts[0].trim(),
                        parts[1].trim().replace("= present", "").trim(),
                        parts[2].trim().replaceFirst("(?i)buses=", "")
                });
            }
        }
        return rows;
    }

    // ---- 2. typed Assets binding: the guard shape that makes it a >=26 NeoForge surface ----

    @Test
    void assetsBindingRegistrationIsGuardedOutOf1211AndFabricByShape() {
        Path corePlugin = repoRoot().resolve("src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java");
        List<String> stack = guardStackAtLine(corePlugin, "new AssetGeneratorJS()");
        assertTrue(stack.contains(">=26"),
                "the Assets registration line must sit inside a '>=26' guard (1.21.1 evaluates it away),"
                        + " active guards: " + stack);
        assertTrue(stack.contains("neoforge"),
                "the Assets registration line must sit inside the NeoForge-only core plugin"
                        + " (fabric trees never see it), active guards: " + stack);
    }

    @Test
    void generateAssetsAndLangDeclarationsStayInsideTheNeoForgeOnlyClientEventsGroup() {
        Path clientEvents = repoRoot().resolve(
                "src/main/java/com/tkisor/nekojs/bindings/event/client/ClientEvents.java");
        List<String> assetsStack = guardStackAtLine(clientEvents, "GROUP.client(\"generateAssets\"");
        List<String> langStack = guardStackAtLine(clientEvents, "GROUP.client(\"lang\"");
        assertTrue(assetsStack.contains("neoforge"),
                "generateAssets is declared in the NeoForge-only ClientEvents group (the whole file is"
                        + " loader-guarded; fabric never evaluates it), active guards: " + assetsStack);
        assertTrue(langStack.contains("neoforge"),
                "lang is declared in the NeoForge-only ClientEvents group, active guards: " + langStack);
    }

    // ---- 3. fabric trees: no Assets binding, no assets/lang event bridge ----

    @Test
    void fabricTreesRegisterNoAssetsBindingAndBridgeNeitherAssetsNorLang() throws IOException {
        Path root = repoRoot();
        int assetRegistrations = 0;
        try (Stream<Path> walk = walkAll(root, List.of(
                root.resolve("src/fabric/java"),
                root.resolve("versions/26.1.2-fabric/src"),
                root.resolve("versions/26.2.0-fabric/src")))) {
            for (Path file : walk.toList()) {
                String code = codeLinesOnly(read(file));
                assetRegistrations += countOccurrences(code, "new AssetGeneratorJS(");
                assetRegistrations += countOccurrences(code, "register(\"Assets\"");
            }
        }
        assertEquals(0, assetRegistrations,
                "no fabric source may register the Assets typed binding (its only registration is the"
                        + " >=26-guarded NeoForge core plugin line)");

        String fabricBridges = codeLinesOnly(read(root.resolve(
                "src/fabric/java/com/tkisor/nekojs/fabric/event/FabricClientEventBindings.java")));
        Set<String> busNames = new LinkedHashSet<>();
        Matcher buses = Pattern.compile("CLIENT_EVENTS\\.client\\(\"([^\"]+)\"").matcher(fabricBridges);
        while (buses.find()) {
            busNames.add(buses.group(1));
        }
        assertEquals(Set.of("tickPre", "tickPost", "tick"), busNames,
                "the fabric ClientEvents subset stays the tick-only bridge: no generateAssets/lang bridge"
                        + " may appear without a fabric reload/generation lifecycle to back it");
    }

    private static Stream<Path> walkAll(Path root, List<Path> dirs) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files.stream();
    }

    // ---- 4. runtime pins on the executing node (production registration paths) ----

    @BeforeAll
    static void initPlatform() {
        // Root-test-tree Platform stub (Platform.init is defensive: already initialized stays put);
        // ScriptType's static init needs NekoJSPaths, so the stub must come first.
        try {
            Platform.init(new StubPlatform());
        } catch (RuntimeException ignored) {
            // another test already initialized Platform
        }
    }

    /** Production registration path (same entry the bootstrap BindingsPoint collection uses). */
    private static BindingRegistry.BindingRegistryImpl productionBindings(ScriptType scriptType) {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(scriptType);
        new NekoJSCorePlugin().registerBinding(registry);
        return registry;
    }

    /**
     * 26.x NeoForge runtime leg: both events AND the typed {@code Assets} binding resolve through
     * the production paths — capability row "supported". The 1.21.1 counterpart (binding absent,
     * both events present) is a node-local test in {@code versions/1.21.1/src/test}: the shared
     * tree's active node compiles verbatim, so the other version shape cannot live behind an
     * inner guard here (whole-file guard shape follows Ticket27ClientGuiRenderSurfaceTest).
     */
    @Test
    void on26xNeoForgeBothEventsAndTheAssetsBindingResolve() {
        Binding assets = productionBindings(ScriptType.CLIENT).viewRegistered().get("Assets");
        assertNotNull(assets, "26.x NeoForge nodes must expose the Assets typed binding");
        assertEquals(AssetGeneratorJS.class, assets.valueType(),
                "the Assets binding must carry the AssetGeneratorJS member surface");

        assertEquals("generateAssets", ClientEvents.GENERATE_ASSETS.eventName());
        assertEquals("ClientEvents", ClientEvents.GENERATE_ASSETS.groupName());
        assertEquals(ScriptType.CLIENT, ClientEvents.GENERATE_ASSETS.scriptType());
        assertEquals("lang", ClientEvents.LANG.eventName());
        assertEquals(ScriptType.CLIENT, ClientEvents.LANG.scriptType());
    }

    /** Root-test-tree minimal Platform stub (shape follows KeyBindEventsTest / QueryToolDeclarationParityTest). */
    private static final class StubPlatform implements IPlatform {
        @Override
        public boolean isClient() {
            return true;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public String getMcVersion() {
            return "test";
        }

        @Override
        public Path getGameDir() {
            return com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket29-capability");
        }

        @Override
        public String getLoaderId() {
            return "test";
        }

        @Override
        public String getLoaderVersion() {
            return "0";
        }

        @Override
        public java.util.Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() {
            return java.util.Map.of();
        }

        @Override
        public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) {
            return null;
        }

        @Override
        public java.util.Set<com.tkisor.nekojs.platform.PlatformCapability> capabilities() {
            return java.util.Set.of();
        }
    }
}
//?}
//?}
