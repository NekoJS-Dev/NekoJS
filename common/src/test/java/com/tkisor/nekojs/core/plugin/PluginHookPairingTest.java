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
 * Pairing rules for the two-part model: {@link NekoJSPlugin} projects the built-in Points, and a
 * Point file is the single source of truth. This test guards the pairing protocol mechanically. A
 * new collecting channel needs four changes together (the Point file, the default hook on the base
 * interface, one line in the built-in list, and one row in the pairing table here); missing one
 * turns the test red. That keeps the base interface from growing back into a fat interface with
 * no source of truth.
 *
 * <p>Five rules:
 * <ol>
 *   <li>The collecting point ids registered by the built-in list
 *       ({@link NekoBuiltinPointsPlugin}) match the pairing table exactly;</li>
 *   <li>Every collecting point reports {@code NekoJSPlugin.class} from {@code pluginType()}, so it
 *       collects from all plugins; overriding takes part, an empty default is a no-op, and an
 *       explicit implements Contributor is equivalent to overriding the base interface;</li>
 *   <li>Every paired hook is declared on the base interface and is default (not abstract: without
 *       an override, the default body takes part);</li>
 *   <li>The base interface's {@code register*} methods equal the paired hooks plus the direct-call
 *       exception ({@code registerApiSurface}); a new {@code register*} method without a pairing
 *       turns the test red;</li>
 *   <li>Each point defines its collector in its own Point file (the lambda's runtime class carries
 *       the defining class name, which catches a collector pasted into another Point file).</li>
 * </ol>
 *
 * <p>Direct-call hooks of the callback families ({@code init}, {@code attachXxxData},
 * {@code generateXxx}, {@code modifyWorkspaceConfig}, {@code beforeRecipeLoading},
 * {@code afterRecipes}) do not start with {@code register} and stay outside this check. The
 * pairing table is keyed by extension point id: bindings and script_properties are closure
 * factories with no static POINT constant, so their instance identity cannot serve as the key.
 */
class PluginHookPairingTest {

    /**
     * Pairing table with one row per collecting built-in point: extension point id, facade hook,
     * signature and defining class. Adding a channel means adding a row here, and the signature is
     * kept in sync with the hook declaration.
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

    /**
     * The only non-collecting {@code register} hook on the base interface; bootstrap calls it
     * directly (see NekoPluginBootstrap).
     */
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
     * A collector must be defined in its own Point file. A lambda or method reference carries the
     * defining class FQCN in its runtime class name (on HotSpot it looks like
     * {@code <defining class>$$Lambda/0x…}, the stable hidden class form since JDK 9), so
     * stripping the {@code $Lambda} suffix gives the defining class. A collector pasted across
     * files (the EventsPoint collector pasted into ClientEventsPoint, say) lands in another
     * defining class and fails the check.
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

    /**
     * Captures every built-in point registered by the {@link NekoBuiltinPointsPlugin} list. The
     * handle slot returns null because nothing consumes it during collection.
     */
    private static List<NekoPluginExtensionPoint<?, ?, ?>> captureBuiltinRegistrations() {
        List<NekoPluginExtensionPoint<?, ?, ?>> captured = new ArrayList<>();
        // The SAM method declares generic type parameters, which a lambda cannot implement, so use an anonymous class
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
