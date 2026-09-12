package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC8（ticket 09）高级 Java 面 smoke：{@code Java.type}、{@code Java.loadClass} 别名、
 * Graal interop（List/Map 映射、Number targetTypeMapping）与当前 HostAccess/ClassFilter
 * 形态保持既有行为，不因契约整理收紧。
 *
 * <p>按 {@code NekoSandboxFactory.build} 的关键接线复制 Context（真实
 * {@link NekoSharedHostAccess} + 真实 {@link ClassFilter} + 相同 interop 选项），
 * 省略 logger/IO/node runtime 等与本 smoke 无关的外围。{@code java:} ESM 模块协议的
 * 解析语义由 {@code NekoModuleResolverTest} 覆盖。
 */
class AdvancedJavaInteropSmokeTest {

    /** HostAccess.ALL + adapter targetTypeMapping 的宿主侧探针。 */
    public static final class HostProbe {
        public String echo(String value) { return "echo:" + value; }
        public int sumInteger(Integer a, Integer b) { return a + b; }
        public int listSize(List<?> list) { return list.size(); }
        public int mapSize(Map<?, ?> map) { return map.size(); }
        public String joinList(List<String> parts) { return String.join("-", parts); }
    }

    private static Context sandboxLikeContext(HostProbe probe) {
        NekoSharedHostAccess hostAccess = new NekoSharedHostAccess(List.of());
        ClassFilter classFilter = new ClassFilter(SandboxConfig.defaultConfig());
        Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(hostAccess.get())
                .allowHostClassLookup(classFilter)
                .allowCreateProcess(false)
                .allowValueSharing(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.strict", "true")
                .build();
        context.getBindings("js").putMember("probe", probe);
        // 与 NekoSandboxFactory 相同的别名接线
        context.eval("js", "Java.loadClass = Java.type;");
        return context;
    }

    @Test
    void javaTypeResolvesAllowedClassesAndInvokesStatics() {
        try (Context context = sandboxLikeContext(new HostProbe())) {
            Value stringType = context.eval("js", "Java.type('java.lang.String')");
            assertTrue(stringType.canExecute() || stringType.isMetaObject(),
                    "Java.type must resolve allowed classes");
            assertEquals("42", context.eval("js", "String(Java.type('java.lang.String').valueOf(42))").asString());
        }
    }

    @Test
    void javaLoadClassAliasBehavesLikeJavaType() {
        try (Context context = sandboxLikeContext(new HostProbe())) {
            assertTrue(context.eval("js", "typeof Java.loadClass === 'function'").asBoolean(),
                    "Java.loadClass alias must be installed (NekoSandboxFactory wiring)");
            assertEquals(123, context.eval("js", "Java.loadClass('java.lang.Integer').parseInt('123')").asInt());
        }
    }

    @Test
    void classFilterStillDeniesBlacklistedHostClasses() {
        try (Context context = sandboxLikeContext(new HostProbe())) {
            boolean denied = false;
            try {
                context.eval("js", "Java.type('java.lang.Runtime')");
            } catch (PolyglotException expected) {
                denied = true;
            }
            assertTrue(denied, "default ClassFilter must keep denying blacklisted classes (java.lang.Runtime)");
            assertFalse(context.eval("js", "probe.echo('x') == null").asBoolean());
        }
    }

    @Test
    void graalInteropKeepsCurrentHostAccessShape() {
        try (Context context = sandboxLikeContext(new HostProbe())) {
            // 宿主对象成员可访问（HostAccess.ALL + allowPublicAccess 的既有形态）
            assertEquals("echo:hi", context.eval("js", "probe.echo('hi')").asString());

            // JS 数组 → java.util.List（allowListAccess/allowArrayAccess）
            assertEquals(3, context.eval("js", "probe.listSize(['a', 'b', 'c'])").asInt());
            assertEquals("a-b-c", context.eval("js", "probe.joinList(['a', 'b', 'c'])").asString());

            // JS 对象字面量 → java.util.Map（allowMapAccess）
            assertEquals(2, context.eval("js", "probe.mapSize({x: 1, y: 2})").asInt());

            // Number targetTypeMapping：JS number → Integer 形参（NekoSharedHostAccess 映射）
            assertEquals(30, context.eval("js", "probe.sumInteger(10, 20)").asInt());
        }
    }

    @Test
    void reflectionGatedPrefixesStayDeniedUnderDefaultConfig() {
        try (Context context = sandboxLikeContext(new HostProbe())) {
            boolean denied = false;
            try {
                context.eval("js", "Java.type('java.lang.reflect.Method')");
            } catch (PolyglotException expected) {
                denied = true;
            }
            assertTrue(denied, "reflection-gated prefixes must stay denied under default sandbox config");
        }
    }
}
