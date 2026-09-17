package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 一个收集扩展点的完整自包含定义：id + 目标插件类型 + 环境谓词 + 累积器生命周期
 * + 依赖声明 + 合并策略（ADR-0001 V2 模型）。
 *
 * <p>扩展点由 {@link NekoPluginExtensionProvider} 在 bootstrap 的扩展点注册阶段经
 * {@link NekoPluginExtensionRegistry#register} 注册（{@code NekoJSPlugin} 的 14 个固有钩子
 * 也是这样注册的内置扩展点，id 为 {@code nekojs:*}），注册返回
 * {@link NekoPluginExtensionHandle}，bootstrap 完成后可凭它取回产物。
 *
 * <p><b>依赖（ADR-0002 双轨制）：</b>freeze 后 bootstrap 按 Kahn 拓扑序（同层按注册序，
 * 内置先行是图的根部天然保证）逐点收集。① 时序依赖经 builder
 * {@code dependsOn(point)}/{@code dependsOnId(id)} 声明——仅需对方先完成、不读产物；
 * 环 fail-fast（报错打印完整环路径）、未注册 id 在 freeze 早爆。② 数据依赖 = 在
 * initializer / collector 里 {@link NekoPluginExtensionContext#result} 读取先序点产物，
 * 免声明；违序读取（对方已注册但尚未 finish）立即抛 {@link IllegalStateException}
 * 并附"declare dependsOn"修复指引。③ 可选时序依赖经
 * {@code dependsOnOptional(point)}/{@code dependsOnOptionalId(id)} 声明：仅当对方
 * <b>已注册</b>时加入排序边（对方缺席的 bootstrap——如不含版本树插件的 common 测试
 * 轮——不建边、不早爆），用于跨层数据依赖"读对方产物、但对方并非所有环境都存在"的
 * 场合；对方在场合的序保证与硬依赖等同，违序读取仍按 ② 立即抛。
 *
 * <p><b>合并策略（ADR-0001 四档标准件）：</b>builder {@code merge(...)} 必填，从
 * {@link MergePolicy} 的 {@code append / firstWin / overrideWarn / failFast} 四档
 * 显式选择；键控累积器在冲突点调用 {@link MergePolicy#resolveDuplicate}。
 *
 * <p><b>reload 可重入：</b>每次 bootstrap 都会重新调用 {@code initializer} 新建累积器，
 * 扩展点自身不持有跨轮状态；产物经 {@code finisher} 从累积器快照而来，与累积器解耦。
 * finisher 运行后引擎对实现了 {@link Sealable} 的累积器调用密封（finish 后不再收集）。
 *
 * @param id          扩展点 id，同一次 bootstrap 内全局唯一（含与内置 {@code nekojs:*} 冲突，
 *                    重复注册抛 {@link IllegalArgumentException}）；第三方建议使用
 *                    {@code modid:name} 命名空间前缀。不允许为 null 或 blank
 * @param pluginType  目标插件类型过滤器：仅 {@code pluginType.isInstance(plugin)} 的插件会被收集，
 *                    其余插件直接跳过、collector 不被调用
 * @param enabled     环境谓词：每个扩展点收集前以当前 {@link NekoPluginExtensionContext} 测试一次，
 *                    为 {@code false} 时整个扩展点在当前环境（如专用服务器）跳过——
 *                    initializer/collector/finisher 都不执行，也不发布产物
 * @param initializer 累积器工厂：每轮 bootstrap 为本扩展点新建一个可变累积器 {@code A}；
 *                    接收当前 {@link NekoPluginExtensionContext}，故可按环境或先序点的产物
 *                    定制累积器初始状态。绝不能返回跨轮共享的可变单例
 * @param collector   收集回调：仅对同时通过环境谓词与类型过滤的插件调用一次，往累积器收集
 * @param finisher    收尾函数：累积器 → 不可变产物 {@code R}。bootstrap 在本扩展点收集完
 *                    所有插件后调用一次，产物随即发布；副作用型收尾（lock/静态注入等）
 *                    也应写在这里而不是 bootstrap 流程里
 * @param dependsOn   依赖声明：元素为 {@link NekoPluginExtensionPoint} 实例或 String id
 * @param <P>         目标插件类型（{@link NekoJSPlugin} 的子类型）
 * @param <A>         累积器类型（收集期的可变状态，每轮 bootstrap 新建）
 * @param <R>         产物类型（finisher 输出的不可变结果）
 */
public final class NekoPluginExtensionPoint<P extends NekoJSPlugin, A, R> {
    private final String id;
    private final Class<P> pluginType;
    private final Predicate<NekoPluginExtensionContext> enabled;
    private final Function<NekoPluginExtensionContext, A> initializer;
    private final BiConsumer<P, A> collector;
    private final Function<A, R> finisher;
    private final List<Object> dependsOn;
    private final List<Object> optionalDependsOn;
    private final MergePolicy mergePolicy;

    private NekoPluginExtensionPoint(
            String id,
            Class<P> pluginType,
            Predicate<NekoPluginExtensionContext> enabled,
            Function<NekoPluginExtensionContext, A> initializer,
            BiConsumer<P, A> collector,
            Function<A, R> finisher,
            List<Object> dependsOn,
            List<Object> optionalDependsOn,
            MergePolicy mergePolicy) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin extension point id must not be blank");
        }
        Objects.requireNonNull(pluginType, "pluginType");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(initializer, "initializer");
        Objects.requireNonNull(collector, "collector");
        Objects.requireNonNull(finisher, "finisher");
        Objects.requireNonNull(mergePolicy, "mergePolicy");
        this.id = id;
        this.pluginType = pluginType;
        this.enabled = enabled;
        this.initializer = initializer;
        this.collector = collector;
        this.finisher = finisher;
        this.dependsOn = List.copyOf(dependsOn);
        this.optionalDependsOn = List.copyOf(optionalDependsOn);
        this.mergePolicy = mergePolicy;
    }

    /** 扩展点 id（同轮 bootstrap 内全局唯一）。 */
    public String id() {
        return id;
    }

    /** 目标插件类型过滤器。 */
    public Class<P> pluginType() {
        return pluginType;
    }

    /** 环境谓词。 */
    public Predicate<NekoPluginExtensionContext> enabled() {
        return enabled;
    }

    /** 累积器工厂。 */
    public Function<NekoPluginExtensionContext, A> initializer() {
        return initializer;
    }

    /** 收集回调。 */
    public BiConsumer<P, A> collector() {
        return collector;
    }

    /** 收尾函数。 */
    public Function<A, R> finisher() {
        return finisher;
    }

    /** 依赖声明（元素为扩展点实例或 String id，不可变）。 */
    public List<Object> dependsOn() {
        return dependsOn;
    }

    /**
     * 可选时序依赖声明（元素为扩展点实例或 String id，不可变）：仅当对方已注册时
     * 参与拓扑排序；对方缺席的 bootstrap 不建边（也不 early-bang）。
     */
    public List<Object> optionalDependsOn() {
        return optionalDependsOn;
    }

    /** 合并策略。 */
    public MergePolicy mergePolicy() {
        return mergePolicy;
    }

    /** 依赖元素的 id 归一（实例或 String）。 */
    static String dependencyId(Object dependency) {
        if (dependency instanceof NekoPluginExtensionPoint<?, ?, ?> point) {
            return point.id();
        }
        return (String) dependency;
    }

    // ---- V2 builder（ADR-0001：唯一推荐入口，merge 必填）----

    /** 创建扩展点 builder。 */
    public static <P extends NekoJSPlugin, A, R> Builder<P, A, R> builder(String id, Class<P> pluginType) {
        return new Builder<>(id, pluginType);
    }

    /** V2 扩展点 builder：{@code merge} 必填（不给默认值），依赖可声明点实例或 id。 */
    public static final class Builder<P extends NekoJSPlugin, A, R> {
        private final String id;
        private final Class<P> pluginType;
        private Predicate<NekoPluginExtensionContext> enabled = context -> true;
        private Function<NekoPluginExtensionContext, A> initializer;
        private BiConsumer<P, A> collector;
        private Function<A, R> finisher;
        private final List<Object> dependencies = new ArrayList<>();
        private final List<Object> optionalDependencies = new ArrayList<>();
        private MergePolicy mergePolicy;

        private Builder(String id, Class<P> pluginType) {
            this.id = id;
            this.pluginType = pluginType;
        }

        /** 合并策略（必填）：append / firstWin / overrideWarn / failFast 四选一。 */
        public Builder<P, A, R> merge(MergePolicy policy) {
            this.mergePolicy = Objects.requireNonNull(policy, "mergePolicy");
            return this;
        }

        /** 环境谓词（默认全环境收集）。 */
        public Builder<P, A, R> enabledWhen(Predicate<NekoPluginExtensionContext> predicate) {
            this.enabled = Objects.requireNonNull(predicate, "predicate");
            return this;
        }

        /** 仅客户端收集（等价 {@code enabledWhen(NekoPluginExtensionContext::client)}）。 */
        public Builder<P, A, R> clientOnly() {
            return enabledWhen(NekoPluginExtensionContext::client);
        }

        /** 累积器工厂（必填）。 */
        public Builder<P, A, R> initializer(Function<NekoPluginExtensionContext, A> initializer) {
            this.initializer = Objects.requireNonNull(initializer, "initializer");
            return this;
        }

        /** 收集回调（必填）。 */
        public Builder<P, A, R> collector(BiConsumer<P, A> collector) {
            this.collector = Objects.requireNonNull(collector, "collector");
            return this;
        }

        /** 收尾函数（必填）。 */
        public Builder<P, A, R> finish(Function<A, R> finisher) {
            this.finisher = Objects.requireNonNull(finisher, "finisher");
            return this;
        }

        /** 时序依赖：仅需先完成的扩展点实例（ADR-0002 ①）。 */
        @SafeVarargs
        public final Builder<P, A, R> dependsOn(NekoPluginExtensionPoint<?, ?, ?>... points) {
            dependencies.addAll(List.of(points));
            return this;
        }

        /** 时序依赖：仅需先完成的扩展点 id（用于无法静态引用对方实例的场合）。 */
        public Builder<P, A, R> dependsOnId(String... pointIds) {
            dependencies.addAll(List.of(pointIds));
            return this;
        }

        /**
         * 可选时序依赖：仅当对方已注册时加入排序边（对方缺席的 bootstrap 不建边、
         * 不 early-bang）。用于"读对方产物、但对方并非所有环境都存在"的跨层数据依赖。
         */
        @SafeVarargs
        public final Builder<P, A, R> dependsOnOptional(NekoPluginExtensionPoint<?, ?, ?>... points) {
            optionalDependencies.addAll(List.of(points));
            return this;
        }

        /** 可选时序依赖的 id 形态（跨层引用对方点时唯一可行形态，见 {@link #dependsOnOptional}）。 */
        public Builder<P, A, R> dependsOnOptionalId(String... pointIds) {
            optionalDependencies.addAll(List.of(pointIds));
            return this;
        }

        /** 构建扩展点；merge / initializer / collector / finish 任缺即抛 {@link IllegalStateException}。 */
        public NekoPluginExtensionPoint<P, A, R> build() {
            if (mergePolicy == null) {
                throw new IllegalStateException("Plugin extension point '" + id
                        + "' requires an explicit merge policy (append/firstWin/overrideWarn/failFast)");
            }
            if (initializer == null) {
                throw new IllegalStateException("Plugin extension point '" + id + "' requires an initializer");
            }
            if (collector == null) {
                throw new IllegalStateException("Plugin extension point '" + id + "' requires a collector");
            }
            if (finisher == null) {
                throw new IllegalStateException("Plugin extension point '" + id + "' requires a finisher");
            }
            return new NekoPluginExtensionPoint<>(
                    id, pluginType, enabled, initializer, collector, finisher,
                    dependencies, optionalDependencies, mergePolicy);
        }
    }

}
