package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AliasJSTypeAdapter}：惰性来源解析、透传 probe 面、优先级与环检测。
 *
 * <p>来源适配器全部用合成 lambda（不检查 Value 内容），因此测试不需要真实的 Graal 转换，
 * Value 用 {@code Value.asValue} 的普通值即可。
 */
class AliasJSTypeAdapterTest {

    /** 来源类型夹具。 */
    static class Src {}

    /** 目标类型夹具。 */
    static class Dst {}

    private static JSTypeAdapter<Src> srcAdapter() {
        return new JSTypeAdapter<>() {
            @Override
            public Class<Src> getTargetClass() {
                return Src.class;
            }

            @Override
            public boolean test(Value value) {
                return value.isString();
            }

            @Override
            public Src apply(Value value) {
                return new Src();
            }

            @Override
            public ConversionPrecedence getPrecedence() {
                return ConversionPrecedence.HIGH;
            }

            @Override
            public List<AdapterInputShape> inputShapes() {
                return List.of(self(), string());
            }

            @Override
            public Optional<String> syntaxDoc() {
                return Optional.of("src syntax");
            }
        };
    }

    private static JSTypeAdapterRegistry registryWith(JSTypeAdapter<?>... adapters) {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        for (JSTypeAdapter<?> adapter : adapters) registry.register(adapter);
        return registry;
    }

    @Test
    void applyConvertsViaSourceThenConverter() {
        JSTypeAdapterRegistry registry = registryWith(srcAdapter());
        AliasJSTypeAdapter<Src, Dst> alias = new AliasJSTypeAdapter<>(
                Dst.class, Src.class, src -> new Dst(), null, registry);
        registry.register(alias);

        assertTrue(alias.test(Value.asValue("hit")));
        assertFalse(alias.test(Value.asValue(42)));
        assertInstanceOf(Dst.class, alias.apply(Value.asValue("hit")));
    }

    @Test
    void precedenceAndProbeSurfacePassThroughToSource() {
        JSTypeAdapterRegistry registry = registryWith(srcAdapter());
        AliasJSTypeAdapter<Src, Dst> alias = new AliasJSTypeAdapter<>(
                Dst.class, Src.class, src -> new Dst(), null, registry);
        registry.register(alias);

        assertEquals(ConversionPrecedence.HIGH, alias.getPrecedence());
        assertEquals(srcAdapter().inputShapes(), alias.inputShapes());
        assertEquals(Optional.of("src syntax"), alias.syntaxDoc());
    }

    @Test
    void explicitPrecedenceOverridesSource() {
        JSTypeAdapterRegistry registry = registryWith(srcAdapter());
        AliasJSTypeAdapter<Src, Dst> alias = new AliasJSTypeAdapter<>(
                Dst.class, Src.class, src -> new Dst(), ConversionPrecedence.LOWEST, registry);
        registry.register(alias);

        assertEquals(ConversionPrecedence.LOWEST, alias.getPrecedence());
    }

    @Test
    void unresolvableSourceFallsBackRatherThanThrowing() {
        // 空注册表 + 未注册的 alias：probe/优先级读取回退默认值，test 不接受，apply 报配置错误
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        AliasJSTypeAdapter<Src, Dst> alias = new AliasJSTypeAdapter<>(
                Dst.class, Src.class, src -> new Dst(), null, registry);
        registry.register(alias);

        assertEquals(ConversionPrecedence.LOWEST, alias.getPrecedence());
        assertEquals(List.of(), alias.inputShapes());
        assertFalse(alias.test(Value.asValue("hit")));
        assertThrows(IllegalStateException.class, () -> alias.apply(Value.asValue("hit")));
    }

    @Test
    void aliasCanBeRegisteredBeforeItsSource() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        AliasJSTypeAdapter<Src, Dst> alias = new AliasJSTypeAdapter<>(
                Dst.class, Src.class, src -> new Dst(), null, registry);
        registry.register(alias);
        registry.register(srcAdapter());

        assertTrue(alias.test(Value.asValue("hit")));
        assertInstanceOf(Dst.class, alias.apply(Value.asValue("hit")));
        assertEquals(ConversionPrecedence.HIGH, alias.getPrecedence());
    }

    @Test
    void aliasChainResolvesToTerminalAdapter() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        // 链：Dst alias 自 Mid，Mid alias 自 Src（末端是真适配器）
        class Mid {}
        JSTypeAdapter<Src> source = srcAdapter();
        AliasJSTypeAdapter<Src, Mid> midAlias = new AliasJSTypeAdapter<>(
                Mid.class, Src.class, src -> new Mid(), null, registry);
        AliasJSTypeAdapter<Mid, Dst> topAlias = new AliasJSTypeAdapter<>(
                Dst.class, Mid.class, mid -> new Dst(), null, registry);
        registry.register(topAlias);
        registry.register(midAlias);
        registry.register(source);

        assertTrue(topAlias.test(Value.asValue("hit")));
        assertInstanceOf(Dst.class, topAlias.apply(Value.asValue("hit")));
        // probe 面从链末端透传
        assertEquals(srcAdapter().inputShapes(), topAlias.inputShapes());
    }

    @Test
    void aliasCycleIsRejectedOnUse() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        AliasJSTypeAdapter<Src, Dst> a = new AliasJSTypeAdapter<>(
                Dst.class, Src.class, src -> new Dst(), null, registry);
        AliasJSTypeAdapter<Dst, Src> b = new AliasJSTypeAdapter<>(
                Src.class, Dst.class, dst -> new Src(), null, registry);
        registry.register(a);
        registry.register(b);

        assertFalse(a.test(Value.asValue("hit")));
        assertThrows(IllegalStateException.class, () -> a.apply(Value.asValue("hit")));
    }

    @Test
    void selfAliasIsRejectedAtRegistration() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        assertThrows(IllegalArgumentException.class,
                () -> registry.registerAlias(Dst.class, Dst.class, dst -> dst));
    }

    @Test
    void registryEntryPointRegistersAndDelegates() {
        JSTypeAdapterRegistry.Impl registry = new JSTypeAdapterRegistry.Impl();
        registry.register(srcAdapter());
        registry.registerAlias(Dst.class, Src.class, src -> new Dst());

        JSTypeAdapter<?> alias = registry.view().stream()
                .filter(a -> a.getTargetClass() == Dst.class)
                .findFirst().orElseThrow();
        assertInstanceOf(Dst.class, alias.apply(Value.asValue("hit")));
    }
}
