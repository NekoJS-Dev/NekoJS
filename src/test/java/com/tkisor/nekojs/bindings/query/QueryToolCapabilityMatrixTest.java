package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.capability.CapabilityStatus;
import com.tkisor.nekojs.api.data.BindingRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capability matrix across the loader axis (neoforge / fabric).
 *
 * <p>Each capability row comes from a <b>real probe</b>: a real plugin registered into a real
 * {@link BindingRegistry}, real class loadability and the real registration path. The status
 * vocabulary is common's three-state {@link CapabilityStatus}. The loader axis is decided by
 * runtime loadability: this file must compile from the active stonecutter node's sources, so
 * loader-specific classes are probed by reflection and never referenced at compile time. The
 * derived table is compared against the matching golden, read-only.
 *
 * <p>Golden update rule: change the behavior or the probe first, then update the golden and
 * record the golden diff in the report.
 */
class QueryToolCapabilityMatrixTest {

    private static final String GOLDEN_NEOFORGE = "/golden/query/capability-matrix-neoforge.txt";
    private static final String GOLDEN_FABRIC = "/golden/query/capability-matrix-fabric.txt";

    /** Loader-axis probe: NekoJSCorePlugin, the only DataMap registrar, exists only on neoforge. */
    private static boolean isNeoForge() {
        return loadClassQuiet("com.tkisor.nekojs.core.NekoJSCorePlugin") != null;
    }

    /**
     * Bare JUnit constraint (same fact as {@code VanillaRegistryProbe}): on the 26.x node without
     * an FML Loader, the vanilla/NeoForge registry class init chain throws. Capability probes
     * therefore check <b>class existence only</b> (initialize=false) and trigger no
     * &lt;clinit&gt;.
     */
    private static Class<?> loadClassQuiet(String name) {
        try {
            return Class.forName(name, false, QueryToolCapabilityMatrixTest.class.getClassLoader());
        } catch (ClassNotFoundException error) {
            return null;
        }
    }

    @Test
    void capabilityMatrixMatchesGolden() throws Exception {
        boolean neoforge = isNeoForge();
        Map<String, String> rows = new TreeMap<>();
        probeDataMapBinding(rows, neoforge);
        probeDataMapPortableAlternative(rows, neoforge);
        probeEntitySelectorsBinding(rows, neoforge);

        String actual = String.join("\n", rows.values());
        String goldenPath = neoforge ? GOLDEN_NEOFORGE : GOLDEN_FABRIC;
        assertEquals(readGolden(goldenPath), normalize(actual),
                "query tools capability matrix changed. 确认是刻意 capability 变更后，更新 " + goldenPath
                        + " 并在 REPORT golden 差异小节留记录");
    }

    // ---- probes ----

    /**
     * DataMap binding availability: on neoforge the binding registers into the real registry for
     * each ScriptType. On fabric the registration class does not exist and the row is explicitly
     * unavailable: the script-side preflight reports an undefined identifier instead of a silent
     * no-op.
     */
    private static void probeDataMapBinding(Map<String, String> rows, boolean neoforge) {
        if (neoforge) {
            Class<?> corePlugin = loadClassQuiet("com.tkisor.nekojs.core.NekoJSCorePlugin");
            assertNotNull(corePlugin);
            Class<?> dataMapJS = loadClassQuiet("com.tkisor.nekojs.bindings.static_access.DataMapJS");
            assertNotNull(dataMapJS, "DataMapJS must be loadable on neoforge");
            for (ScriptType type : ScriptType.values()) {
                String evidence;
                CapabilityStatus status;
                if (type == ScriptType.CLIENT) {
                    // A bare JVM cannot initialize the MC client classes of the client binding
                    // branch (same fact as VanillaRegistryProbe), and no client-side runtime smoke
                    // has run, so this row stays at the source-trace level. It is reported as
                    // PARTIAL: partly true under the stated conditions, with the difference
                    // recorded, and the evidence states only the source trace was collected and
                    // the client path was never measured live.
                    status = CapabilityStatus.PARTIAL;
                    evidence = "source trace only: same unconditional registerBinding path, but the "
                            + "client class init is not probeable in a bare JVM and no client runtime "
                            + "smoke was run -> not runtime-verified (REPORT §7 N6)";
                } else {
                    BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(type).scopedTo("nekojs");
                    newInstance(corePlugin).registerBinding(registry);
                    var binding = registry.viewRegistered().get("DataMap");
                    status = binding == null ? CapabilityStatus.UNAVAILABLE : CapabilityStatus.SUPPORTED;
                    evidence = "registerBinding(NekoJSCorePlugin) valueType="
                            + (binding == null ? "-" : binding.valueType().getName());
                }
                rows.put("DataMap|binding[" + type.name() + "]",
                        "DataMap | DataMap binding[" + type.name() + "] | " + status
                                + " | " + evidence);
            }
        } else {
            assertNull(loadClassQuiet("com.tkisor.nekojs.bindings.static_access.DataMapJS"),
                    "DataMapJS must not exist on fabric (neoforge data map API has no fabric counterpart)");
            for (ScriptType type : ScriptType.values()) {
                rows.put("DataMap|binding[" + type.name() + "]",
                        "DataMap | DataMap binding[" + type.name() + "] | " + CapabilityStatus.UNAVAILABLE
                                + " | neoforge-only: NekoJSCorePlugin/DataMapJS absent on fabric; "
                                + "script-side undefined identifier at preflight (explicit failure)");
            }
        }
    }

    /**
     * DataMap portable alternative (the Registry facade dataMap queries): on neoforge
     * NeoForgeRegistryQueryService exists (SUPPORTED). On fabric the IPlatform default method is
     * not overridden, so the query silently returns an empty value at runtime (a recorded
     * deviation, owner loader-port/W6).
     */
    private static void probeDataMapPortableAlternative(Map<String, String> rows, boolean neoforge) {
        if (neoforge) {
            CapabilityStatus api = loadClassQuiet(
                    "net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps") != null
                            ? CapabilityStatus.SUPPORTED : CapabilityStatus.UNAVAILABLE;
            rows.put("DataMap|neoforge-datamaps-api",
                    "DataMap | NeoForgeDataMaps builtin data maps | " + api
                            + " | platform data map API held by the NeoForge adapter side "
                            + "(DataMapJS is neoforge-guarded)");
            boolean servicePresent = loadClassQuiet(
                    "com.tkisor.nekojs.api.registry.NeoForgeRegistryQueryService") != null;
            rows.put("DataMap|registry-facade-alternative",
                    "DataMap | Registry.get(...).dataMapIds/dataMapValue alternative | "
                            + (servicePresent ? CapabilityStatus.SUPPORTED : CapabilityStatus.UNAVAILABLE)
                            + " | portable query surface is a managed contract symbol (RegistryView.dataMap*)");
        } else {
            assertNull(loadClassQuiet("com.tkisor.nekojs.api.registry.NeoForgeRegistryQueryService"),
                    "NeoForge registry query service must not exist on fabric");
            boolean overridesService = false;
            try {
                Method method = Class.forName("com.tkisor.nekojs.platform.FabricPlatform", false,
                        QueryToolCapabilityMatrixTest.class.getClassLoader())
                        .getDeclaredMethod("registryQueryService");
                overridesService = method != null;
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Not overridden means the default method semantics apply.
            }
            rows.put("DataMap|registry-facade-alternative",
                    "DataMap | Registry.get(...).dataMapIds/dataMapValue alternative | "
                            + CapabilityStatus.UNAVAILABLE
                            + " | IPlatform default method returns empty/null (silent no-op deviation, "
                            + "recorded in REPORT; owner loader-port/W6; override=" + overridesService + ")");
        }
    }

    /**
     * EntitySelectors binding availability: the shared-tree facade (no loader guard) is available
     * through the registration path. neoforge registers EntitySelectorsPlugin, discovered by
     * annotation scanning; fabric registers it from FabricPluginLoader's built-in list (a
     * reflection probe verifies the list member). It is not registered outside SERVER/TEST.
     */
    private static void probeEntitySelectorsBinding(Map<String, String> rows, boolean neoforge) throws Exception {
        Class<?> pluginClass = loadClassQuiet("com.tkisor.nekojs.util.selector.EntitySelectorsPlugin");
        assertNotNull(pluginClass, "EntitySelectorsPlugin must be loadable");
        String registrationEvidence;
        if (neoforge) {
            registrationEvidence = "registerBinding(EntitySelectorsPlugin) valueType=%s";
        } else {
            Class<?> loaderClass = loadClassQuiet("com.tkisor.nekojs.fabric.FabricPluginLoader");
            assertNotNull(loaderClass, "FabricPluginLoader must be loadable on fabric");
            Field builtinField = loaderClass.getDeclaredField("BUILTIN_PLUGINS");
            builtinField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Class<?>> builtinPlugins = (List<Class<?>>) builtinField.get(null);
            assertTrue(builtinPlugins.contains(pluginClass),
                    "EntitySelectorsPlugin must be in the fabric builtin plugin list");
            registrationEvidence =
                    "registered via FabricPluginLoader builtin list valueType=%s"
                            + "; runtime query smoke not exercised on fabric (REPORT §7)";
        }

        Class<?> facadeClass = loadClassQuiet("com.tkisor.nekojs.util.selector.EntitySelectorsJS");
        assertEquals("com.tkisor.nekojs.util.selector.EntitySelectorsJS", facadeClass.getName(),
                "shared-tree query facade FQN drift");
        String valueType = facadeClass.getName();

        for (ScriptType type : ScriptType.values()) {
            BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(type).scopedTo("nekojs");
            newInstance(pluginClass).registerBinding(registry);
            var binding = registry.viewRegistered().get("EntitySelectors");
            CapabilityStatus status =
                    binding == null ? CapabilityStatus.UNAVAILABLE : CapabilityStatus.SUPPORTED;
            String evidence = binding == null
                    ? "not registered (query needs a ServerLevel; startup/client have none)"
                    : String.format(registrationEvidence, valueType);
            rows.put("EntitySelectors|binding[" + type.name() + "]",
                    "EntitySelectors | EntitySelectors binding[" + type.name() + "] | " + status
                            + " | " + evidence);
        }
    }

    // ---- helpers ----

    private static com.tkisor.nekojs.api.NekoJSPlugin newInstance(Class<?> pluginClass) {
        try {
            Object instance = pluginClass.getDeclaredConstructor().newInstance();
            return (com.tkisor.nekojs.api.NekoJSPlugin) instance;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("cannot instantiate plugin " + pluginClass, error);
        }
    }

    private static String normalize(String value) {
        List<String> lines = new ArrayList<>(value.lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).sorted().toList());
        return String.join("\n", lines);
    }

    private static String readGolden(String path) throws IOException {
        try (InputStream stream = QueryToolCapabilityMatrixTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("缺少 golden 资源 " + path);
            }
            List<String> lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .sorted()
                    .toList();
            return String.join("\n", lines);
        }
    }
}
