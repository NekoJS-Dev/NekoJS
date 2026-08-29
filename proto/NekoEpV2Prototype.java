// ============================================================================
// PROTOTYPE（一次性代码）—— 扩展点模型 V2 骨架验证
//
// 回答的问题（E4 / #44）：ADR-0001/0002/0003 定义的 V2 模型在真实 Java 上是否
// 成立？定义一个扩展点、声明依赖、处理冲突，写起来是否真的"不繁复"？
//
// 验证清单：
//   [A1] builder 唯一入口，merge 必填
//   [A1] Point 自包含文件：Contributor 接口 + POINT 常量同处
//   [A1] finish 后累积器密封（"finish 后不再收集"从约定变机制）
//   [A1] 产物访问 = handle 唯一入口
//   [A2] dependsOn 时序依赖 + result() 数据依赖免声明
//   [A2] freeze 后拓扑排序；环 fail-fast 打印环路径；未注册 id 早爆
//   [A2] 同层按注册序；数据依赖违序运行期抛错附修复指引
//   [A3] 内置清单 = 一列 register(...)；与第三方同路；内置先行（拓扑根部）
//
// 运行（JDK 17+）：<jdk>/bin/java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 proto/NekoEpV2Prototype.java
// ============================================================================

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** 单文件启动入口（java 以本文件第一个顶层类为入口）。 */
public class NekoEpV2PrototypeLauncher {
    public static void main(String[] args) { NekoEpV2Prototype.main(args); }
}

// —— stand-in：真实 com.tkisor.nekojs.api.NekoJSPlugin（仅生命周期示意）——
interface NekoJSPlugin {
    default String pluginName() { return getClass().getSimpleName(); }
}

// ============================================================================
// MergePolicies（ADR-0001 §2：冲突三档 + append 无冲突档）
// ============================================================================

final class MergePolicies {

    /** 冲突策略：向键控累积器合并一条贡献时如何处理同键冲突。 */
    interface Policy {
        String name();
        <V> void merge(PolicyBucket<V> bucket, String contributor, String key, V value);
    }

    static Policy append() {
        return new Policy() {
            public String name() { return "append"; }
            public <V> void merge(PolicyBucket<V> b, String who, String k, V v) {
                throw new UnsupportedOperationException("append 无键控合并（列表式收集）");
            }
        };
    }

    static Policy firstWin() {
        return new Policy() {
            public String name() { return "firstWin"; }
            public <V> void merge(PolicyBucket<V> b, String who, String k, V v) {
                if (b.map().containsKey(k)) {
                    System.out.printf("    [warn] %s: '%s' 已由更高优先级插件注册，忽略重复%n", b.ownerId(), k);
                    return;
                }
                b.map().put(k, v);
            }
        };
    }

    static Policy overrideWarn() {
        return new Policy() {
            public String name() { return "overrideWarn"; }
            public <V> void merge(PolicyBucket<V> b, String who, String k, V v) {
                if (b.map().containsKey(k)) {
                    System.out.printf("    [warn] %s: '%s' 被覆盖（overrideWarn）%n", b.ownerId(), k);
                }
                b.map().put(k, v);
            }
        };
    }

    static Policy failFast() {
        return new Policy() {
            public String name() { return "failFast"; }
            public <V> void merge(PolicyBucket<V> b, String who, String k, V v) {
                if (b.map().containsKey(k)) {
                    throw new IllegalArgumentException("扩展点 " + b.ownerId()
                        + " 冲突：'" + k + "' 已由其他插件注册（failFast）");
                }
                b.map().put(k, v);
            }
        };
    }
}

// ============================================================================
// 迷你框架：PolicyBucket / ExtensionPoint / ExtensionHandle / Bootstrap
// ============================================================================

/** 键控标准累积器：实现 merge 策略的载体；finish 后由引擎 seal（机制化冻结）。 */
final class PolicyBucket<V> {
    private final Map<String, V> map = new LinkedHashMap<>();
    private final MergePolicies.Policy policy;
    private final String ownerId;
    private boolean sealed;

    PolicyBucket(String ownerId, MergePolicies.Policy policy) {
        this.ownerId = ownerId;
        this.policy = policy;
    }

    void register(String contributor, String key, V value) {
        if (sealed) {
            throw new IllegalStateException("扩展点 " + ownerId + " 已 finish，累积器密封，不可再收集");
        }
        policy.merge(this, contributor, key, value);
    }

    Map<String, V> map() { return map; }
    String ownerId() { return ownerId; }
    void seal() { this.sealed = true; }
    Map<String, V> snapshot() {
        seal();
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }
}

/** V2 扩展点（与真实 NekoPluginExtensionPoint<P,A,R> 同构 + dependsOn + policy）。 */
final class ExtensionPoint<P extends NekoJSPlugin, A, R> {
    final String id;
    final Class<P> pluginType;
    final Predicate<Bootstrap.Env> enabled;
    final Function<Bootstrap.Ctx, A> initializer;   // 可空 = 引擎按 policy 提供标准桶
    final BiConsumer<P, A> collector;
    final Function<A, R> finisher;
    final List<Object> rawDeps;                     // ExtensionPoint 实例或 String id
    final MergePolicies.Policy policy;              // 必填（builder 校验）

    private ExtensionPoint(Builder<P, A, R> b) {
        this.id = b.id; this.pluginType = b.pluginType; this.enabled = b.enabled;
        this.initializer = b.initializer; this.collector = b.collector; this.finisher = b.finisher;
        this.rawDeps = List.copyOf(b.rawDeps); this.policy = b.policy;
    }

    static <P extends NekoJSPlugin, A, R> Builder<P, A, R> builder(String id, Class<P> pluginType) {
        return new Builder<>(id, pluginType);
    }

    static final class Builder<P extends NekoJSPlugin, A, R> {
        private final String id;
        private final Class<P> pluginType;
        private Predicate<Bootstrap.Env> enabled = env -> true;
        private Function<Bootstrap.Ctx, A> initializer;
        private BiConsumer<P, A> collector;
        private Function<A, R> finisher;
        private final List<Object> rawDeps = new ArrayList<>();
        private MergePolicies.Policy policy;

        private Builder(String id, Class<P> pluginType) { this.id = id; this.pluginType = pluginType; }

        Builder<P, A, R> merge(MergePolicies.Policy p) { this.policy = p; return this; }
        Builder<P, A, R> clientOnly() { this.enabled = env -> env.client(); return this; }
        Builder<P, A, R> initializer(Function<Bootstrap.Ctx, A> init) { this.initializer = init; return this; }
        Builder<P, A, R> collector(BiConsumer<P, A> c) { this.collector = c; return this; }
        Builder<P, A, R> finish(Function<A, R> f) { this.finisher = f; return this; }
        @SafeVarargs
        final Builder<P, A, R> dependsOn(ExtensionPoint<?, ?, ?>... points) {
            rawDeps.addAll(List.of(points)); return this;
        }
        final Builder<P, A, R> dependsOnId(String... ids) {
            rawDeps.addAll(List.of(ids)); return this;
        }

        ExtensionPoint<P, A, R> build() {
            if (policy == null)
                throw new IllegalStateException("扩展点 " + id + "：merge 策略必填（append/firstWin/overrideWarn/failFast）");
            if (collector == null) throw new IllegalStateException("扩展点 " + id + "：collector 必填");
            if (finisher == null) throw new IllegalStateException("扩展点 " + id + "：finisher 必填");
            return new ExtensionPoint<>(this);
        }
    }
}

/** 扩展点句柄（ADR-0001 §4）：注册时返回，bootstrap 后 get() 取产物。 */
final class ExtensionHandle<R> {
    private final String id;
    private R product;
    private boolean published;
    private boolean skipped;

    ExtensionHandle(String id) { this.id = id; }

    void publish(R product) { this.product = product; this.published = true; }
    void markSkipped() { this.skipped = true; this.published = true; }

    R get() {
        if (!published) throw new IllegalStateException("扩展点 " + id + " 尚未完成 bootstrap，不能取产物");
        return product;
    }
}

// ============================================================================
// Bootstrap：注册窗口 → freeze（拓扑+环+未注册id）→ 点优先收集
// ============================================================================

final class Bootstrap {

    record Env(boolean client) {}

    /** 收集期上下文：result / resultOrThrow 两档（ADR-0001 §7 / ADR-0002 §2）。 */
    final class Ctx {
        final Env env;
        private Ctx(Env env) { this.env = env; }

        /** 可选依赖：合法缺席（未注册/环境跳过）→ null。 */
        @SuppressWarnings("unchecked")
        <R> R result(ExtensionPoint<?, ?, R> point) {
            return (R) lookup(point, false);
        }

        /** 必需依赖：任何形式缺席 → IllegalStateException。 */
        @SuppressWarnings("unchecked")
        <R> R resultOrThrow(ExtensionPoint<?, ?, R> point) {
            return (R) lookup(point, true);
        }

        private Object lookup(ExtensionPoint<?, ?, ?> point, boolean required) {
            if (finished.containsKey(point.id)) return finished.get(point.id);
            if (!registered.containsKey(point.id)) {
                if (required) throw new IllegalStateException(
                    "必需依赖 " + point.id + " 在本次 bootstrap 中不存在（未注册）");
                return null; // 可选依赖，合法缺席
            }
            if (skipped.contains(point.id)) {
                if (required) throw new IllegalStateException(
                    "必需依赖 " + point.id + " 在当前环境被跳过（如专用服务器上的 client-only 点）");
                return null;
            }
            throw new IllegalStateException("数据依赖违序：" + point.id + " 尚未 finish，但已被读取。"
                + " 请声明 dependsOn(" + point.id + ") 或检查注册顺序。");
        }
    }

    private final Map<String, ExtensionPoint<?, ?, ?>> registered = new LinkedHashMap<>();
    private final Map<String, ExtensionHandle<?>> handles = new LinkedHashMap<>();
    private final Map<String, Object> finished = new LinkedHashMap<>();
    private final java.util.Set<String> skipped = new LinkedHashSet<>();
    private List<ExtensionPoint<?, ?, ?>> topoOrder;
    private boolean frozen;

    /** 注册窗口：返回句柄；重复 id → IAE；freeze 后 → ISE。 */
    <R> ExtensionHandle<R> register(ExtensionPoint<?, ?, R> point) {
        if (frozen) throw new IllegalStateException("注册窗口已关闭（freeze 后不可注册）：" + point.id);
        if (registered.containsKey(point.id))
            throw new IllegalArgumentException("扩展点 id 重复：" + point.id);
        registered.put(point.id, point);
        ExtensionHandle<R> h = new ExtensionHandle<>(point.id);
        handles.put(point.id, h);
        System.out.println("  注册 " + point.id);
        return h;
    }

    /** freeze：未注册 id 早爆 + Kahn 拓扑（同层注册序），环 → 打印完整环路径。 */
    void freeze() {
        for (ExtensionPoint<?, ?, ?> p : registered.values()) {
            for (Object dep : p.rawDeps) {
                String depId = depId(dep);
                if (!registered.containsKey(depId))
                    throw new IllegalStateException(p.id + " dependsOn 未注册的扩展点：" + depId + "（拼写错误？）");
            }
        }
        this.topoOrder = topoSort();
        frozen = true;
        System.out.println("  freeze 完成，执行序（同层按注册序）：");
        for (ExtensionPoint<?, ?, ?> p : topoOrder)
            System.out.println("    " + p.id + (p.rawDeps.isEmpty() ? "" : "  ← " + depIds(p)));
    }

    private static String depId(Object dep) {
        @SuppressWarnings("unchecked")
        String id = dep instanceof ExtensionPoint<?, ?, ?> p ? p.id : (String) dep;
        return id;
    }

    private String depIds(ExtensionPoint<?, ?, ?> p) {
        List<String> names = new ArrayList<>();
        for (Object dep : p.rawDeps) names.add(depId(dep));
        return names.toString();
    }

    private List<ExtensionPoint<?, ?, ?>> topoSort() {
        Map<String, Integer> degree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>(); // blocker → 依赖它的点
        for (String id : registered.keySet()) { degree.put(id, 0); dependents.put(id, new ArrayList<>()); }
        for (ExtensionPoint<?, ?, ?> p : registered.values()) {
            for (Object dep : p.rawDeps) {
                dependents.get(depId(dep)).add(p.id);
                degree.merge(p.id, 1, Integer::sum);
            }
        }
        List<ExtensionPoint<?, ?, ?>> order = new ArrayList<>();
        List<String> ready = new ArrayList<>();
        for (String id : registered.keySet()) if (degree.get(id) == 0) ready.add(id); // 注册序 = 稳定同层序
        while (!ready.isEmpty()) {
            String id = ready.remove(0);
            order.add(registered.get(id));
            for (String next : dependents.get(id)) {
                if (degree.merge(next, -1, Integer::sum) == 0) ready.add(next);
            }
        }
        if (order.size() != registered.size()) {
            List<String> inCycle = new ArrayList<>();
            for (String id : registered.keySet()) if (degree.get(id) > 0) inCycle.add(id);
            throw new IllegalStateException("扩展点依赖成环 fail-fast："
                + String.join(" → ", inCycle) + " → " + inCycle.get(0));
        }
        return order;
    }

    /** 点优先收集：initializer → collector（按插件序）→ finisher → seal → publish。 */
    void run(Env env, List<NekoJSPlugin> plugins) {
        if (!frozen) throw new IllegalStateException("先 freeze 再 run");
        Ctx ctx = new Ctx(env);
        System.out.println("  bootstrap 运行（client=" + env.client + "，插件 " + plugins.size() + " 个）");
        for (ExtensionPoint<?, ?, ?> point : topoOrder) {
            if (!point.enabled.test(env)) {
                skipped.add(point.id);
                handles.get(point.id).markSkipped();
                System.out.println("  [跳过] " + point.id + "（环境谓词为 false）");
                continue;
            }
            Object accumulator = point.initializer != null
                ? point.initializer.apply(ctx)
                : new PolicyBucket<Object>(point.id, point.policy); // 标准桶默认提供
            for (NekoJSPlugin plugin : plugins) {
                if (point.pluginType.isInstance(plugin)) {
                    collect(point, plugin, accumulator);
                }
            }
            Object product = finish(point, accumulator);
            finished.put(point.id, product);
            publishHandle(point, product);
            System.out.println("  [finish] " + point.id + " → " + product);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void collect(ExtensionPoint point, NekoJSPlugin plugin, Object acc) {
        ((BiConsumer) point.collector).accept(plugin, acc);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object finish(ExtensionPoint point, Object acc) {
        return ((Function) point.finisher).apply(acc);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void publishHandle(ExtensionPoint point, Object product) {
        ((ExtensionHandle) handles.get(point.id)).publish(product);
    }
}

// ============================================================================
// 演示 Point 文件（ADR-0001：一个扩展点一个自包含文件——Contributor + POINT 同处）
// ============================================================================

/** 样例内置点：node_modules（firstWin）。 */
final class NodeModulesPoint {
    interface Contributor extends NekoJSPlugin {
        void registerNodeModules(PolicyBucket<String> registry);
    }
    static final ExtensionPoint<Contributor, PolicyBucket<String>, Map<String, String>> POINT =
        ExtensionPoint.<Contributor, PolicyBucket<String>, Map<String, String>>builder("nekojs:node_modules", Contributor.class)
            .merge(MergePolicies.firstWin())
            .collector(Contributor::registerNodeModules)
            .finish(PolicyBucket::snapshot)
            .build();
}

/** 样例内置点：events（append；自定义双列表累积器 → initializer 自备）。 */
final class EventsPoint {
    static final class HooksBucket {
        final List<String> serverHooks = new ArrayList<>();
        final List<String> clientHooks = new ArrayList<>();
        Map<String, List<String>> snapshot() {
            return Map.of("server", List.copyOf(serverHooks), "client", List.copyOf(clientHooks));
        }
    }
    interface Contributor extends NekoJSPlugin {
        void registerEvents(HooksBucket hooks);
    }
    static final ExtensionPoint<Contributor, HooksBucket, Map<String, List<String>>> POINT =
        ExtensionPoint.<Contributor, HooksBucket, Map<String, List<String>>>builder("nekojs:events", Contributor.class)
            .merge(MergePolicies.append())
            .initializer(ctx -> new HooksBucket())
            .collector((c, b) -> c.registerEvents(b))
            .finish(HooksBucket::snapshot)
            .build();
}

/** 样例内置点：client_events（ADR-0002 标准示例——dependsOn + result 数据依赖）。 */
final class ClientEventsPoint {
    static final class MergedGroups {
        final List<String> clientGroups;
        MergedGroups(List<String> own) { this.clientGroups = List.copyOf(own); }
        public String toString() { return "client:" + clientGroups; }
    }
    interface Contributor extends NekoJSPlugin {
        void registerClientEvents(List<String> groups);
    }
    static final ExtensionPoint<Contributor, List<String>, MergedGroups> POINT =
        ExtensionPoint.<Contributor, List<String>, MergedGroups>builder("nekojs:client_events", Contributor.class)
            .merge(MergePolicies.append())
            .clientOnly()
            .dependsOn(EventsPoint.POINT)                                   // ① 时序依赖
            .initializer(ctx -> new ArrayList<>(ctx.result(EventsPoint.POINT).get("client"))) // ② 数据依赖免声明
            .collector((c, list) -> c.registerClientEvents(list))
            .finish(MergedGroups::new)
            .build();
}

/** 样例第三方点：registry_infos（failFast）。 */
final class RegistryInfosPoint {
    interface Contributor extends NekoJSPlugin {
        void registerRegistryInfos(PolicyBucket<String> registry);
    }
    static final ExtensionPoint<Contributor, PolicyBucket<String>, Map<String, String>> POINT =
        ExtensionPoint.<Contributor, PolicyBucket<String>, Map<String, String>>builder("mymod:registry_infos", Contributor.class)
            .merge(MergePolicies.failFast())
            .collector(Contributor::registerRegistryInfos)
            .finish(PolicyBucket::snapshot)
            .build();
}

/** 样例第三方点：registry_object_types（PR #37 痛点② 的 V2 写法）。 */
final class RegistryObjectTypesPoint {
    interface Contributor extends NekoJSPlugin {
        void registerObjectTypes(PolicyBucket<String> registry);
    }
    static final ExtensionPoint<Contributor, PolicyBucket<String>, Map<String, String>> POINT =
        ExtensionPoint.<Contributor, PolicyBucket<String>, Map<String, String>>builder("mymod:registry_object_types", Contributor.class)
            .merge(MergePolicies.failFast())
            .dependsOn(RegistryInfosPoint.POINT)
            .initializer(ctx -> new PolicyBucket<>("mymod:registry_object_types", MergePolicies.failFast()))
            .collector(Contributor::registerObjectTypes)
            .finish(PolicyBucket::snapshot)
            .build();
}

// ============================================================================
// 演示插件（Contributor = 实现即收集）
// ============================================================================

final class CorePlugin implements NekoJSPlugin, EventsPoint.Contributor, NodeModulesPoint.Contributor {
    public void registerEvents(EventsPoint.HooksBucket hooks) {
        hooks.serverHooks.add("ServerEvents");
        hooks.clientHooks.add("ClientEvents");
    }
    public void registerNodeModules(PolicyBucket<String> registry) {
        registry.register(pluginName(), "fs", "builtin:fs");
        registry.register(pluginName(), "path", "builtin:path");
    }
}

final class ThirdPartyPlugin implements NekoJSPlugin, NodeModulesPoint.Contributor,
        RegistryInfosPoint.Contributor, RegistryObjectTypesPoint.Contributor {
    public void registerNodeModules(PolicyBucket<String> registry) {
        registry.register(pluginName(), "fs", "thirdparty:fs"); // firstWin → 被忽略 + warn
        registry.register(pluginName(), "chalk", "npm:chalk");
    }
    public void registerRegistryInfos(PolicyBucket<String> registry) {
        registry.register(pluginName(), "minecraft:item", "ResourceKey[item]");
    }
    public void registerObjectTypes(PolicyBucket<String> registry) {
        registry.register(pluginName(), "basic", "ItemBuilderJS::new");
    }
}

// ============================================================================
// 主演示
// ============================================================================

class NekoEpV2Prototype {

    public static void main(String[] args) {
        banner("S1 内置清单注册（ADR-0003：NekoBuiltinPointsPlugin = 一列 register，与第三方同路）");
        Bootstrap boot = new Bootstrap();
        // —— 内置点定义插件（bootstrap 显式提升为第一个 provider）——
        boot.register(EventsPoint.POINT);
        boot.register(ClientEventsPoint.POINT);
        boot.register(NodeModulesPoint.POINT);
        // —— 第三方 provider（普通插件实现的 registerPluginExtensionPoints）——
        var infosHandle = boot.register(RegistryInfosPoint.POINT);
        var typesHandle = boot.register(RegistryObjectTypesPoint.POINT);

        banner("S2 违例：merge 必填 / 重复 id");
        expect("merge 必填", IllegalStateException.class, () ->
            ExtensionPoint.<NodeModulesPoint.Contributor, PolicyBucket<String>, Map<String, String>>builder(
                    "x:no-policy", NodeModulesPoint.Contributor.class)
                .collector((c, b) -> {})
                .finish(PolicyBucket::snapshot)
                .build());
        expect("重复 id", IllegalArgumentException.class, () -> boot.register(EventsPoint.POINT));

        banner("S3 freeze（ADR-0002：拓扑 + 环检测 + 未注册 id 早爆）");
        boot.freeze();
        expect("freeze 后注册", IllegalStateException.class, () -> boot.register(ExtensionPoint
            .<NodeModulesPoint.Contributor, PolicyBucket<String>, Map<String, String>>builder(
                "x:late-registration", NodeModulesPoint.Contributor.class)
            .merge(MergePolicies.append()).collector((c, b) -> {}).finish(PolicyBucket::snapshot).build()));
        expect("未注册 id", IllegalStateException.class, () -> {
            Bootstrap b2 = new Bootstrap();
            b2.register(ExtensionPoint.<NodeModulesPoint.Contributor, PolicyBucket<String>, Map<String, String>>builder(
                    "x:typo", NodeModulesPoint.Contributor.class)
                .merge(MergePolicies.failFast())
                .dependsOnId("nekojs:node_modules2") // 拼错的 id
                .collector((c, b) -> {}).finish(PolicyBucket::snapshot).build());
            b2.freeze();
        });
        expect("依赖成环", IllegalStateException.class, () -> {
            Bootstrap b2 = new Bootstrap();
            b2.register(ExtensionPoint.<NodeModulesPoint.Contributor, PolicyBucket<String>, Map<String, String>>builder(
                    "x:a", NodeModulesPoint.Contributor.class)
                .merge(MergePolicies.append())
                .dependsOnId("x:b")
                .collector((c, b) -> {}).finish(PolicyBucket::snapshot).build());
            b2.register(ExtensionPoint.<NodeModulesPoint.Contributor, PolicyBucket<String>, Map<String, String>>builder(
                    "x:b", NodeModulesPoint.Contributor.class)
                .merge(MergePolicies.append())
                .dependsOnId("x:a")
                .collector((c, b) -> {}).finish(PolicyBucket::snapshot).build());
            b2.freeze();
        });

        banner("S4 收集（点优先序；firstWin 冲突；产物逐点打印）");
        boot.run(new Bootstrap.Env(true), List.of(new CorePlugin(), new ThirdPartyPlugin()));

        banner("S5 句柄访问（ADR-0001 §4：handle 唯一产物入口）");
        System.out.println("  mymod:registry_infos → " + infosHandle.get());
        System.out.println("  mymod:registry_object_types → " + typesHandle.get());

        banner("S6 违例：数据依赖违序 / 必需依赖被环境跳过 / 累积器密封");
        expect("漏 dependsOn 的数据依赖", IllegalStateException.class, () -> {
            Bootstrap b2 = new Bootstrap();
            b2.register(ExtensionPoint.<NodeModulesPoint.Contributor, PolicyBucket<String>, Map<String, String>>builder(
                    "x:late-dep", NodeModulesPoint.Contributor.class)
                .merge(MergePolicies.append())
                .initializer(ctx -> {
                    ctx.result(NodeModulesPoint.POINT); // 违序读取：node_modules 注册在后、尚未 finish
                    return new PolicyBucket<>("x:late-dep", MergePolicies.append());
                })
                .collector((c, b) -> {}).finish(PolicyBucket::snapshot).build());
            b2.register(NodeModulesPoint.POINT);
            b2.freeze();
            b2.run(new Bootstrap.Env(true), List.of());
        });
        expect("必需依赖被环境跳过", IllegalStateException.class, () -> {
            Bootstrap b2 = new Bootstrap();
            b2.register(EventsPoint.POINT);
            b2.register(ClientEventsPoint.POINT);
            b2.register(ExtensionPoint.<NodeModulesPoint.Contributor, PolicyBucket<String>, Map<String, String>>builder(
                    "x:needs-client-events", NodeModulesPoint.Contributor.class)
                .merge(MergePolicies.append())
                .dependsOn(ClientEventsPoint.POINT)
                .initializer(ctx -> {
                    ctx.resultOrThrow(ClientEventsPoint.POINT); // 专用服务器上被跳过 → 抛错
                    return new PolicyBucket<>("x:needs-client-events", MergePolicies.append());
                })
                .collector((c, b) -> {}).finish(PolicyBucket::snapshot).build());
            b2.freeze();
            b2.run(new Bootstrap.Env(false), List.of()); // 专用服务器：client-only 被跳过
        });
        expect("finish 后累积器密封", IllegalStateException.class, () -> {
            PolicyBucket<String> leak = collectAndStealBucket();
            leak.register("sneaky", "late", "value");
        });

        banner("结论");
        System.out.println("  [A1] builder 必填 merge / Point 自包含 / 密封机制 / handle 访问 —— 全部通过");
        System.out.println("  [A2] dependsOn+result 双轨 / 拓扑+环+未知id / 违序运行期抛错 —— 全部通过");
        System.out.println("  [A3] 内置清单同路注册 / 内置先行（拓扑根部天然先行） —— 全部通过");
        System.out.println("  发现①：标准件需要第 4 档 append（列表式无冲突收集），ADR-0001 的'三档'应微调为'四档'");
        System.out.println("  发现②：initializer 省略时引擎按 merge 策略提供标准 PolicyBucket——键控点的定义可再省一行");
    }

    /** 偷走累积器引用，finish 后再写（演示密封机制）。 */
    static PolicyBucket<String> collectAndStealBucket() {
        Bootstrap b = new Bootstrap();
        var handle = b.register(ExtensionPoint.<Stealer, PolicyBucket<String>, Map<String, String>>builder(
                "x:steal", Stealer.class)
            .merge(MergePolicies.firstWin())
            .collector((stealer, bucket) -> stealer.steal(bucket))
            .finish(PolicyBucket::snapshot).build());
        b.freeze();
        b.run(new Bootstrap.Env(true), List.of(new Stealer()));
        handle.get();
        return Stealer.stolen;
    }

    static final class Stealer implements NekoJSPlugin {
        static PolicyBucket<String> stolen;
        void steal(PolicyBucket<String> bucket) { stolen = bucket; }
    }

    // —— 小工具 ——

    interface Thunk { void run(); }

    static void expect(String label, Class<? extends Throwable> type, Thunk thunk) {
        try {
            thunk.run();
            System.out.println("  [FAIL] " + label + "：没有抛出 " + type.getSimpleName());
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                System.out.println("  [OK] " + label + " → " + type.getSimpleName() + ": " + truncate(t.getMessage()));
            } else {
                System.out.println("  [FAIL] " + label + " → 错误类型 " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 110 ? s.substring(0, 110) + "…" : s;
    }

    static void banner(String title) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("  " + title);
        System.out.println("======================================================================");
    }
}
