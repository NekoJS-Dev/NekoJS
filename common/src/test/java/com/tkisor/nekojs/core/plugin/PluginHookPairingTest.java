package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 双形态模型配对不变式（ADR-0010）：{@link NekoJSPlugin} 是内置 Point 的<b>门面投影</b>，
 * Point 文件是唯一<b>事实源</b>。本测试机械化守护配对协议——加收集型通道必须三件套同改
 * （Point 文件 + 基接口 default 钩子 + 内置清单一行 + 本测试配对表），漏一件即红，
 * 防止基接口退化为无事实源的 V1 式胖接口。
 *
 * <p>四条不变式：
 * <ol>
 *   <li>内置清单（{@link NekoBuiltinPointsPlugin}）注册的收集型扩展点 id 集合与配对表完全一致；</li>
 *   <li>每个收集型点的 {@code pluginType()} 为 {@code NekoJSPlugin.class}——收集面 = 所有插件，
 *       覆写即参与，空 default 为 no-op（显式 implements Contributor 与覆写基接口等价）；</li>
 *   <li>每个配对钩子真实声明在基接口上，且为 default（非 abstract）；</li>
 *   <li>基接口的 {@code register*} 方法集合 = 配对钩子 ∪ 直调豁免（{@code registerApiSurface}）——
 *       出现无配对的新 {@code register*} 即红。</li>
 * </ol>
 *
 * <p>回调族直调钩子（{@code init} 族 / {@code attachXxxData} / {@code generateXxx} /
 * {@code modifyWorkspaceConfig} / {@code beforeRecipeLoading} / {@code afterRecipes}）不以
 * {@code register} 开头，天然在守卫范围之外。配对表以扩展点 id 为键：bindings /
 * script_properties 两点是闭包工厂（无静态 POINT 常量），实例身份不可作键。
 */
class PluginHookPairingTest {

    /** 配对表：收集型内置点 id → 门面钩子方法名。加通道三件套时同步维护。 */
    private static final Map<String, String> PAIRING = Map.ofEntries(
            Map.entry(EventsPoint.ID, "registerEvents"),
            Map.entry(ClientEventsPoint.ID, "registerClientEvents"),
            Map.entry(BindingsPoint.ID, "registerBinding"),
            Map.entry(AdaptersPoint.ID, "registerAdapters"),
            Map.entry(TypeDocsPoint.ID, "registerTypeDocs"),
            Map.entry(NodeTypeDocsPoint.ID, "registerNodeTypeDocs"),
            Map.entry(NodeModulesPoint.ID, "registerNodeModules"),
            Map.entry(ScriptCompilersPoint.ID, "registerScriptCompilers"),
            Map.entry(ScriptPropertiesPoint.ID, "registerScriptProperty"),
            Map.entry(RecipeNamespacesPoint.ID, "registerRecipeNamespaces"),
            Map.entry(RecipeSchemasPoint.ID, "registerRecipeSchemas"),
            Map.entry(RecipeLifecyclePoint.ID, "registerRecipeLifecycleHooks"),
            Map.entry(LifecyclePoint.ID, "registerLifecycleHooks"),
            Map.entry(ProbeBackendsPoint.ID, "registerProbeBackends"));

    /** 基接口上唯一的非收集型 {@code register} 钩子（bootstrap 直调，见 NekoPluginBootstrap）。 */
    private static final String DIRECT_CALLED_REGISTER_HOOK = "registerApiSurface";

    @Test
    void builtinListMatchesPairingTable() {
        Set<String> ids = captureBuiltinRegistrations().stream()
                .map(NekoPluginExtensionPoint::id)
                .collect(Collectors.toSet());
        assertEquals(PAIRING.keySet(), ids,
                "内置清单注册的扩展点必须与配对表一致：三件套（Point 文件 + 基接口钩子 + 清单一行）缺一不可");
    }

    @Test
    void everyCollectedPointTargetsEveryPlugin() {
        for (NekoPluginExtensionPoint<?, ?, ?> point : captureBuiltinRegistrations()) {
            assertTrue(PAIRING.containsKey(point.id()), "未登记配对表的内置点: " + point.id());
            assertSame(NekoJSPlugin.class, point.pluginType(),
                    point.id() + " 的 pluginType 必须为 NekoJSPlugin.class（收集面 = 所有插件，覆写即参与）");
        }
    }

    @Test
    void facadeHooksExistOnBaseInterfaceAsDefaultNoOps() {
        for (Map.Entry<String, String> entry : PAIRING.entrySet()) {
            Method hook = Arrays.stream(NekoJSPlugin.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(entry.getValue()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            entry.getKey() + " 的门面钩子 " + entry.getValue() + " 未声明在 NekoJSPlugin 上"));
            assertFalse(Modifier.isAbstract(hook.getModifiers()),
                    entry.getValue() + " 必须是 default 空实现（未覆写的插件被收集时必须为 no-op）");
        }
    }

    @Test
    void baseInterfaceHasNoUnpairedRegisterHooks() {
        Set<String> registerHooks = declaredMethodNames().stream()
                .filter(name -> name.startsWith("register"))
                .collect(Collectors.toSet());
        Set<String> expected = new HashSet<>(PAIRING.values());
        expected.add(DIRECT_CALLED_REGISTER_HOOK);
        assertEquals(expected, registerHooks,
                "基接口出现无 Point 配对的 register* 方法：加收集型通道必须三件套同改并在本测试配对表登记，"
                        + "直调型钩子须先在此显式登记豁免");
    }

    /** 捕获 {@link NekoBuiltinPointsPlugin} 清单注册的全部内置点（handle 位置返回 null，收集期无人消费）。 */
    private static List<NekoPluginExtensionPoint<?, ?, ?>> captureBuiltinRegistrations() {
        List<NekoPluginExtensionPoint<?, ?, ?>> captured = new ArrayList<>();
        // SAM 方法带泛型类型参数，lambda 无法实现，用匿名类
        NekoPluginExtensionRegistry registry = new NekoPluginExtensionRegistry() {
            @Override
            public <P extends NekoJSPlugin, A, R> NekoPluginExtensionHandle<R> register(
                    NekoPluginExtensionPoint<P, A, R> extensionPoint) {
                captured.add(extensionPoint);
                return null;
            }
        };
        new NekoBuiltinPointsPlugin(new ScriptPropertyRegistry.Impl(), false)
                .registerPluginExtensionPoints(registry);
        return captured;
    }

    private static Set<String> declaredMethodNames() {
        return Arrays.stream(NekoJSPlugin.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
