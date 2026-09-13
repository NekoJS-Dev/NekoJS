package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.LegacySurfaceAdapter;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.core.api.CoreManagedApiBootstrap;
import com.tkisor.nekojs.util.selector.EntitySelectorsJS;
import com.tkisor.nekojs.util.selector.EntitySelectorsPlugin;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 25（query tools）source contract 归类 fixture：
 *
 * <p>两个查询域（DataMap / EntitySelectors）按 09 号票机制归类为 <b>query binding</b>——
 * 经 BindingsPoint（{@code registerBinding}）收集、catalog 观察面（{@code global:} kind +
 * 占位签名）可见，但<b>不进入</b> portable-core managed contract（「当前入口存在 ≠
 * managed stable」）；二者均无事件 owner、无生命周期 Point、无事件包装器。
 *
 * <p>本测试全部从最高调用者 Seam 观察（{@code CoreManagedApiBootstrap.buildContract}、
 * {@code LegacySurfaceAdapter}、{@code BindingRegistry}），不触碰 golden。
 */
class QueryToolContractClassificationTest {

    private static final URI TEST_CODE_SOURCE = URI.create("file:///ticket25-query-tools.jar");

    /** catalog 观察面：查询 binding 以 global kind + 占位 void 签名进入 legacySurface（09 §1.3）。 */
    @Test
    void queryBindingIsObservedAsLegacyGlobalSymbolWithPlaceholderSignature() {
        BindingCatalogEntry entry =
                BindingCatalogEntry.of("EntitySelectors", ScriptType.SERVER, EntitySelectorsJS.class, false);

        List<ApiSymbol> legacy = LegacySurfaceAdapter.fromBindings(List.of(entry));

        assertEquals(1, legacy.size(), "one binding entry maps to exactly one observation symbol");
        ApiSymbolId id = legacy.get(0).id();
        assertEquals("global", id.kind(), "binding observation kind must be 'global'");
        assertEquals("EntitySelectors", id.qualifiedName());
        assertEquals(1, legacy.get(0).signatures().size(), "placeholder signature only");
        var signature = legacy.get(0).signatures().get(0);
        assertTrue(signature.parameters().isEmpty(), "observation signature must not invent parameters");
        assertEquals(ApiTypeRef.voidType(), signature.returnType(),
                "observation signature is a void placeholder, not a real contract signature");
    }

    /** managed contract 不收录查询全局（DataMap/EntitySelectors），但收录 Registry 替代查询面。 */
    @Test
    void queryToolGlobalsAreNotInManagedContractWhileRegistryAlternativeIs() {
        VerifiedApiContract contract = CoreManagedApiBootstrap.buildContract(TEST_CODE_SOURCE);
        Set<String> symbolIds = contract.contract().symbols().stream()
                .map(symbol -> symbol.id().value())
                .collect(Collectors.toSet());

        // 「当前入口存在 ≠ managed stable」：binding 全局不因收录而升级 managed stable
        assertFalse(symbolIds.contains("global:DataMap"),
                "DataMap query binding must stay on the observation plane, not the managed contract");
        assertFalse(symbolIds.contains("global:EntitySelectors"),
                "EntitySelectors query binding must stay on the observation plane, not the managed contract");

        // DataMap 的替代查询面（Registry facade dataMap 查询）是 managed contract 符号
        assertTrue(symbolIds.contains("global:Registry"),
                "Registry facade global must be in the managed contract");
        assertTrue(symbolIds.contains("member:RegistryView.dataMapIds"),
                "RegistryView.dataMapIds (portable data map query) must be a contract symbol");
        assertTrue(symbolIds.contains("member:RegistryView.dataMapValue"),
                "RegistryView.dataMapValue (portable data map query) must be a contract symbol");
    }

    /** EntitySelectorsPlugin 无事件 owner：不实现事件 Contributor，也不覆写事件注册钩子。 */
    @Test
    void entitySelectorsPluginCarriesNoEventOwnerAndNoLifecyclePoint() {
        Set<String> implemented = List.of(EntitySelectorsPlugin.class.getInterfaces()).stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(NekoJSPlugin.class.getName(), "com.tkisor.nekojs.core.plugin.BindingsPoint$Contributor"),
                implemented,
                "EntitySelectorsPlugin must only project the bindings collection path (no event/Point owner)");

        Set<String> declared = List.of(EntitySelectorsPlugin.class.getDeclaredMethods()).stream()
                .map(method -> method.getName() + "/" + method.getParameterCount())
                .collect(Collectors.toSet());
        assertFalse(declared.contains("registerEvents/1"),
                "query binding must not register events");
        assertFalse(declared.contains("registerClientEvents/1"),
                "query binding must not register client events");
    }

    /** 查询执行需要 ServerLevel：binding 只对 SERVER/TEST 脚本类型可见（工单 fixture 语义）。 */
    @Test
    void entitySelectorsBindingIsVisibleOnlyToServerAndTestScriptTypes() {
        for (ScriptType type : ScriptType.values()) {
            BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(type);
            new EntitySelectorsPlugin().registerBinding(registry);

            if (type == ScriptType.SERVER || type == ScriptType.TEST) {
                assertTrue(registry.viewRegistered().containsKey("EntitySelectors"),
                        "EntitySelectors must be registered for " + type);
                assertEquals(EntitySelectorsJS.class, registry.viewRegistered().get("EntitySelectors").valueType(),
                        "binding valueType must be the query facade class");
            } else {
                assertFalse(registry.viewRegistered().containsKey("EntitySelectors"),
                        "EntitySelectors must not be registered for " + type + " (no ServerLevel there)");
            }
        }
    }

    /** EntitySelectors 工厂与查询入口的 runtime member 锚点（declaration parity 的输入清单）。 */
    @Test
    void entitySelectorsFactoryAndQueryMembersExistAtRuntime() throws Exception {
        // factory 侧
        assertNotNull(EntitySelectorsJS.class.getMethod("create", java.util.function.Consumer.class));
        assertNotNull(EntitySelectorsJS.class.getMethod("builder"));
        for (String preset : List.of("allPlayers", "allEntities", "nearestPlayer",
                "nearestEntity", "randomPlayer", "randomEntity")) {
            assertNotNull(EntitySelectorsJS.class.getMethod(preset), "missing factory preset: " + preset);
        }
        // query 侧（裸 JVM 不初始化 MC 参数类：initialize=false，同 VanillaRegistryProbe 事实）
        ClassLoader loader = getClass().getClassLoader();
        Class<?> serverLevel = Class.forName("net.minecraft.server.level.ServerLevel", false, loader);
        Class<?> entitySelector =
                Class.forName("net.minecraft.commands.arguments.selector.EntitySelector", false, loader);
        assertNotNull(EntitySelectorsJS.class.getMethod("find", serverLevel, entitySelector));
        assertNotNull(EntitySelectorsJS.class.getMethod("find",
                serverLevel, entitySelector, double.class, double.class, double.class));
    }
}
