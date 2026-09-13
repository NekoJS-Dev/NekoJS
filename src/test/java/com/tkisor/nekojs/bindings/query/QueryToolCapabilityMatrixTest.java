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
 * Ticket 25 capability matrix（loader 轴：neoforge / fabric）：
 *
 * <p>每行 capability 由<b>真实探针</b>派生（真实插件注册进真实 {@link BindingRegistry}、
 * 真实类可加载性、真实注册路径），status 词汇 = common {@link CapabilityStatus} 三态。
 * loader 轴按运行时可加载性判定（本文件必须经 stonecutter active 节点原文编译，
 * 因此 loader 专属类一律走反射探针，不做编译期引用），派生表与对应 golden 只读对比。
 *
 * <p>golden 更新纪律：capability 变更须先改行为/探针，再更新 golden 并在
 * REPORT golden 差异小节留记录（09 REGENERATE 纪律在本域的落地）。
 */
class QueryToolCapabilityMatrixTest {

    private static final String GOLDEN_NEOFORGE = "/golden/query/capability-matrix-neoforge.txt";
    private static final String GOLDEN_FABRIC = "/golden/query/capability-matrix-fabric.txt";

    /** loader 轴探针：NekoJSCorePlugin（唯一 DataMap 注册者）只在 neoforge 存在。 */
    private static boolean isNeoForge() {
        return loadClassQuiet("com.tkisor.nekojs.core.NekoJSCorePlugin") != null;
    }

    /**
     * 裸 JUnit 约束（VanillaRegistryProbe 同款事实）：26.x 节点无 FML Loader 时
     * vanilla/NeoForge 注册表类初始化链会抛错。capability 探针只做**类存在性**判定
     * （initialize=false），不触发任何 &lt;clinit&gt;。
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
     * DataMap binding 可用性：
     * neoforge——真实注册进真实 registry（各 ScriptType）；fabric——注册类不存在
     * （显式 unavailable：脚本侧 preflight 报未定义标识符，非静默 no-op）。
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
                    // 裸 JVM 无法初始化 client 绑定分支的 MC client 类（同 VanillaRegistryProbe 事实），
                    // 也**没有**跑过 client 侧 runtime smoke → 本票只到 source trace 层。
                    // 工单 25 双轴审查修正：原先这里硬编码 SUPPORTED（自陈不可探），与
                    // 「每行由真实探针派生」的口径冲突；改为 PARTIAL（= 声明条件下部分成立、
                    // 差异已记录），并在 evidence 里写明「仅 source trace、未实测」。
                    status = CapabilityStatus.PARTIAL;
                    evidence = "source trace only: same unconditional registerBinding path, but the "
                            + "client class init is not probeable in a bare JVM and no client runtime "
                            + "smoke was run -> not runtime-verified (REPORT §7 N6)";
                } else {
                    BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(type);
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
     * DataMap portable 替代面（Registry facade dataMap 查询）：
     * neoforge——NeoForgeRegistryQueryService 存在（SUPPORTED）；fabric——IPlatform 默认方法
     * 未覆写（运行时静默空值，已记录 deviation，owner loader-port/W6）。
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
                // 未覆写即默认方法语义
            }
            rows.put("DataMap|registry-facade-alternative",
                    "DataMap | Registry.get(...).dataMapIds/dataMapValue alternative | "
                            + CapabilityStatus.UNAVAILABLE
                            + " | IPlatform default method returns empty/null (silent no-op deviation, "
                            + "recorded in REPORT; owner loader-port/W6; override=" + overridesService + ")");
        }
    }

    /**
     * EntitySelectors binding 可用性：共享树 facade（无 loader 守卫）经注册路径可用——
     * neoforge 走注解扫描发现的 EntitySelectorsPlugin；fabric 经 FabricPluginLoader
     * 内置清单注册（反射探针验证清单成员）。SERVER/TEST 之外按契约不注册。
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
            BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(type);
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
