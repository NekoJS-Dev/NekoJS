package com.tkisor.nekojs.api;

import graal.graalvm.polyglot.HostAccess;
import graal.graalvm.polyglot.Value;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * JS 类型适配器接口。<p>
 * 插件类可以实现此接口，以提供自定义的类型转换。
 */
public interface JSTypeAdapter<T> extends Predicate<Value>, Function<Value, T> {
    Class<T> getTargetClass();

    default HostAccess.TargetMappingPrecedence getPrecedence() {
        return HostAccess.TargetMappingPrecedence.LOWEST;
    }

    /**
     * 声明此适配器接受的输入形状，供 probe 生成宽松的 TypeScript 输入别名。
     *
     * <p>返回空列表（默认）表示未声明 —— probe 不会为该目标生成 {@code $Foo_} 别名，
     * 也不会放宽引用该类型的参数。建议实现此方法，使 {@link #test(Value)} 的接受范围
     * 与生成的类型声明保持一致。
     *
     * @see AdapterInputShape
     */
    default List<AdapterInputShape> inputShapes() {
        return List.of();
    }

    /**
     * 声明此适配器接受的输入语法说明，供 probe 渲染成 TypeScript 输入别名
     * ({@code $Foo_}) 上方的 JSDoc 注释，让 IDE 使用者了解可用的字符串/对象语法。
     *
     * <p>例如 ingredient 适配器可在此说明 {@code #tag} / {@code @mod} / {@code *} /
     * {@code /regex/} 前缀及 {@code { mod?, filter?, ... }} 对象形式。默认空（不附加文档）。
     */
    default Optional<String> syntaxDoc() {
        return Optional.empty();
    }

    /**
     * 判断 JS 值是否可以转换为目标类型
     */
    @Override
    boolean test(Value value);

    /**
     * 将 JS 值转换为目标类型，如果无法转换应直接抛出异常更为稳妥，而非返回null
     */
    @Override
    T apply(Value value);
}
