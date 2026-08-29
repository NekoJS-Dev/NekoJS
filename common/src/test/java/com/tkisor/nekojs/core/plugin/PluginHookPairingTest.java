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
import java.util.function.Function;
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
 * <p>五条不变式：
 * <ol>
 *   <li>内置清单（{@link NekoBuiltinPointsPlugin}）注册的收集型扩展点 id 集合与配对表完全一致；</li>
 *   <li>每个收集型点的 {@code pluginType()} 为 {@code NekoJSPlugin.class}——收集面 = 所有插件，
 *       覆写即参与，空 default 为 no-op（显式 implements Contributor 与覆写基接口等价）；</li>
 *   <li>每个配对钩子真实声明在基接口上，且为 default（非 abstract，未覆写即按默认体参与）；</li>
 *   <li>基接口的 {@code register*} 方法集合 = 配对钩子 ∪ 直调豁免（{@code registerApiSurface}）——
 *       出现无配对的新 {@code register*} 即红；</li>
 *   <li>每个点的 collector 定义在自己的 Point 文件里（lambda 运行时类携带定义类名，
 *       防跨文件复制粘贴指错钩子）。</li>
 * </ol>
 *
 * <p>回调族直调钩子（{@code init} 族 / {@code attachXxxData} / {@code generateXxx} /
 * {@code modifyWorkspaceConfig} / {@code beforeRecipeLoading} / {@code afterRecipes}）不以
 * {@code register} 开头，天然在守卫范围之外。配对表以扩展点 id 为键：bindings /
 * script_properties 两点是闭包工厂（无静态 POINT 常量），实例身份不可作键。
 */
class PluginHookPairingTest {

    /**
     * 配对表：收集型内置点单条目（扩展点 id + 门面钩子 + 签名 + 定义类）。加通道三件套时
     * 在此追加一行即可，签名与钩子声明同源维护。
     */
    private record Channel(String pointId, String hook, String pointClass, List<String> paramTypes) {
    }

    private static final List<Channel> CHANNELS = List.of(
            new Channel(EventsPoint.ID, "registerEvents", "EventsPoint", List.of("EventGroupRegistry")),
            new Channel(ClientEventsPoint.ID, "registerClientEvents", "ClientEventsPoint", List.of("EventGroupRegistry")),
            new Channel(BindingsPoint.ID, "registerBinding", "BindingsPoint", List.of("BindingRegistry")),
            new Channel(AdaptersPoint.ID, "registerAdapters", "AdaptersPoint", List.of("JSTypeAdapterRegistry")),
            new Channel(TypeDocsPoint.ID, "registerTypeDocs", "TypeDocsPoint", List.of("TypeDocsRegister")),
            new Channel(NodeTypeDocsPoint.ID, "registerNodeTypeDocs", "NodeTypeDocsPoint", List.of("TypeDocsRegister")),
            new Channel(NodeModulesPoint.ID, "registerNodeModules", "NodeModulesPoint", List.of("NodeModuleRegister")),
            new Channel(ScriptCompilersPoint.ID, "registerScriptCompilers", "ScriptCompilersPoint", List.of("ScriptCompilerRegistry")),
            new Channel(ScriptPropertiesPoint.ID, "registerScriptProperty", "ScriptPropertiesPoint", List.of("ScriptPropertyRegistry")),
            new Channel(RecipeNamespacesPoint.ID, "registerRecipeNamespaces", "RecipeNamespacesPoint", List.of("RecipeNamespaceRegister")),
            new Channel(RecipeSchemasPoint.ID, "registerRecipeSchemas", "RecipeSchemasPoint", List.of("RecipeSchemaRegister")),
            new Channel(RecipeLifecyclePoint.ID, "registerRecipeLifecycleHooks", "RecipeLifecyclePoint", List.of("RecipeLifecycleRegister")),
            new Channel(LifecyclePoint.ID, "registerLifecycleHooks", "LifecyclePoint", List.of("PluginLifecycleRegister")),
            new Channel(ProbeBackendsPoint.ID, "registerProbeBackends", "ProbeBackendsPoint", List.of("ProbeBackendRegistry")));

    /** 基接口上唯一的非收集型 {@code register} 钩子（bootstrap 直调，见 NekoPluginBootstrap）。 */
    private static final String DIRECT_CALLED_REGISTER_HOOK = "registerApiSurface";

    @Test
    void builtinListMatchesPairingTable() {
        Set<String> ids = captureBuiltinRegistrations().stream()
                .map(NekoPluginExtensionPoint::id)
                .collect(Collectors.toSet());
        assertEquals(CHANNELS.stream().map(Channel::pointId).collect(Collectors.toSet()), ids,
                "内置清单注册的扩展点必须与配对表一致：三件套（Point 文件 + 基接口钩子 + 清单一行）缺一不可");
    }

    @Test
    void everyCollectedPointTargetsEveryPlugin() {
        for (NekoPluginExtensionPoint<?, ?, ?> point : captureBuiltinRegistrations()) {
            assertTrue(CHANNELS.stream().anyMatch(c -> c.pointId().equals(point.id())),
                    "未登记配对表的内置点: " + point.id());
            assertSame(NekoJSPlugin.class, point.pluginType(),
                    point.id() + " 的 pluginType 必须为 NekoJSPlugin.class（收集面 = 所有插件，覆写即参与）");
        }
    }

    @Test
    void facadeHooksExistOnBaseInterfaceAsDefaults() {
        for (Channel channel : CHANNELS) {
            Method hook = declaredHook(channel.hook());
            assertFalse(Modifier.isAbstract(hook.getModifiers()),
                    channel.hook() + " 必须是 default 方法（未覆写的插件被收集时按默认体参与，不能是 abstract）");
        }
    }

    @Test
    void facadeHookSignaturesAreFrozen() {
        Map<String, List<String>> expected = CHANNELS.stream().collect(Collectors.toMap(
                Channel::hook, Channel::paramTypes, (a, b) -> a));
        expected.put(DIRECT_CALLED_REGISTER_HOOK, List.of("ApiContributionRegistry"));
        expected.forEach((hookName, expectedParams) -> {
            List<String> actualParams = Arrays.stream(declaredHook(hookName).getParameterTypes())
                    .map(Class::getSimpleName)
                    .toList();
            assertEquals(expectedParams, actualParams,
                    "作者面签名漂移（" + hookName + "）：变更必须走破坏性变更评审并同步本冻结表");
        });
    }

    @Test
    void baseInterfaceHasNoUnpairedRegisterHooks() {
        Set<String> registerHooks = declaredMethodNames().stream()
                .filter(name -> name.startsWith("register"))
                .collect(Collectors.toSet());
        Set<String> expected = new HashSet<>(CHANNELS.stream().map(Channel::hook).toList());
        expected.add(DIRECT_CALLED_REGISTER_HOOK);
        assertEquals(expected, registerHooks,
                "基接口出现无 Point 配对的 register* 方法：加收集型通道必须三件套同改并在本测试配对表登记，"
                        + "直调型钩子须先在此显式登记豁免");
    }

    /**
     * collector 必须定义在自己的 Point 文件里。实现依据：lambda/方法引用的运行时类名携带
     * 定义类 FQCN（HotSpot 形如 {@code <定义类>$$Lambda/0x…}，JDK 9+ 的 hidden class 稳定形态）；
     * 剥掉 {@code $Lambda} 后缀即得定义类。跨文件复制粘贴 collector（如 EventsPoint 的
     * collector 被贴进 ClientEventsPoint）会命中别的定义类而变红。
     */
    @Test
    void collectorsAreDeclaredInTheirOwnPointFiles() {
        Map<String, NekoPluginExtensionPoint<?, ?, ?>> byId = captureBuiltinRegistrations().stream()
                .collect(Collectors.toMap(NekoPluginExtensionPoint::id, Function.identity()));
        for (Channel channel : CHANNELS) {
            NekoPluginExtensionPoint<?, ?, ?> point = byId.get(channel.pointId());
            String lambdaClass = point.collector().getClass().getName();
            assertTrue(lambdaClass.contains("$Lambda"),
                    channel.pointId() + " 的 collector 应为 lambda/方法引用，实际: " + lambdaClass);
            String enclosing = lambdaClass.substring(0, lambdaClass.indexOf("$Lambda"));
            enclosing = enclosing.replaceAll("\\$+$", "");
            assertTrue(enclosing.endsWith("." + channel.pointClass()),
                    channel.pointId() + " 的 collector 应定义在 " + channel.pointClass()
                            + " 内，实际定义类: " + enclosing);
        }
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

    private static Method declaredHook(String name) {
        return Arrays.stream(NekoJSPlugin.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("钩子 " + name + " 未声明在 NekoJSPlugin 上"));
    }

    private static Set<String> declaredMethodNames() {
        return Arrays.stream(NekoJSPlugin.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
