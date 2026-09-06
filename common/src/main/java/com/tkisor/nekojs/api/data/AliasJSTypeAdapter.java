package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;

import graal.graalvm.polyglot.Value;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * alias 适配器：目标类型 X 复用来源类型 Y 的适配器做输入识别，值先转成 Y、再经
 * {@code converter: Y -> X} 得到最终值（KubeJS {@code registerAlias} 的对标）。
 *
 * <p>来源适配器在首次使用时从注册表按 {@code getTargetClass() == from} 惰性解析并记忆化，
 * 因此 alias 可以注册在来源适配器之前。alias 链（alias 指向 alias）经委托自然成立：
 * X→Y 的 alias 直接调用 Y 的适配器，而 Y 自己若是 alias 会继续委托——所以解析只走一步；
 * 环（X→Y→X）在首次使用时由调用期活跃集检测，抛 {@link IllegalStateException}——
 * {@link #test(Value)} 把它按"不接受"处理，{@link #apply(Value)} 原样抛出让配置错误尽快暴露。
 *
 * <p>probe 面（{@link #inputShapes()}/{@link #syntaxDoc()}）原样透传来源适配器的声明，
 * 由别名生成器以 X 自己的名字渲染 {@code $X_}（跨包场景下联合类型必须自含，不做 {@code $X_ = $Y_} 引用）。
 * 转换失败的报错来自来源适配器（目标是 Y）——失败原因发生在 Y 那一步，对排查更真实。
 */
public final class AliasJSTypeAdapter<F, T> implements JSTypeAdapter<T> {

    /** 调用期活跃集：同一 alias 在一次转换里被重入即成环。 */
    private static final ThreadLocal<Set<AliasJSTypeAdapter<?, ?>>> ACTIVE =
            ThreadLocal.withInitial(HashSet::new);

    private final Class<T> target;
    private final Class<F> from;
    private final Function<F, T> converter;
    private final ConversionPrecedence precedenceOverride;
    private final JSTypeAdapterRegistry registry;
    private JSTypeAdapter<F> resolved;

    public AliasJSTypeAdapter(Class<T> target, Class<F> from, Function<F, T> converter,
                              ConversionPrecedence precedenceOverride, JSTypeAdapterRegistry registry) {
        this.target = Objects.requireNonNull(target, "target");
        this.from = Objects.requireNonNull(from, "from");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.precedenceOverride = precedenceOverride;
        this.registry = Objects.requireNonNull(registry, "registry");
        if (target == from) {
            throw new IllegalArgumentException("alias target must differ from source: " + target.getName());
        }
    }

    @Override
    public Class<T> getTargetClass() {
        return target;
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        // 注册管线（hostAccess 构建）可能早于来源适配器可见，解析不到就回退 LOWEST
        if (precedenceOverride != null) return precedenceOverride;
        JSTypeAdapter<F> source = peekResolved();
        return source != null ? source.getPrecedence() : ConversionPrecedence.LOWEST;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        JSTypeAdapter<F> source = peekResolved();
        return source != null ? source.inputShapes() : List.of();
    }

    @Override
    public Optional<String> syntaxDoc() {
        JSTypeAdapter<F> source = peekResolved();
        return source != null ? source.syntaxDoc() : Optional.empty();
    }

    @Override
    public boolean test(Value value) {
        Set<AliasJSTypeAdapter<?, ?>> active = ACTIVE.get();
        if (!active.add(this)) return false; // 环：按"不接受"处理，apply 会给出明确报错
        try {
            return resolve().test(value);
        } catch (IllegalStateException unresolvable) {
            return false;
        } finally {
            active.remove(this);
        }
    }

    @Override
    public T apply(Value value) {
        Set<AliasJSTypeAdapter<?, ?>> active = ACTIVE.get();
        if (!active.add(this)) {
            throw new IllegalStateException("alias cycle while converting to " + target.getName());
        }
        try {
            return converter.apply(resolve().apply(value));
        } finally {
            active.remove(this);
        }
    }

    /** 只读式解析：不记忆化、解析不了就返回 null（供 probe/优先级这类"尽力而为"的读取路径）。 */
    private JSTypeAdapter<F> peekResolved() {
        try {
            return resolved != null ? resolved : find();
        } catch (IllegalStateException unresolvable) {
            return null;
        }
    }

    private JSTypeAdapter<F> resolve() {
        if (resolved != null) return resolved;
        JSTypeAdapter<F> source = find();
        resolved = source;
        return source;
    }

    @SuppressWarnings("unchecked")
    private JSTypeAdapter<F> find() {
        for (JSTypeAdapter<?> candidate : registry.view()) {
            if (candidate.getTargetClass() == from) {
                return (JSTypeAdapter<F>) candidate;
            }
        }
        throw new IllegalStateException("no adapter registered for alias source " + from.getName()
                + " (target " + target.getName() + ")");
    }
}
