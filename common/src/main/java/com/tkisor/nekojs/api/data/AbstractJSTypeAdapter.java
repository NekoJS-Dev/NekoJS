package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.JSTypeAdapter;
import graal.graalvm.polyglot.Value;

/**
 * 套路化类型适配器基类，固化 {@link #test(Value)} / {@link #apply(Value)} 的通用模板：
 *
 * <pre>{@code
 *   null      -> acceptNull() ? defaultValue() : throw
 *   string    -> fromString(s)
 *   hostObj   -> fromHostObject(obj)   (返回 null 表示不识别)
 *   other     -> acceptOther(v) ? fromOther(v) : throw
 * }</pre>
 *
 * <p>子类只需覆盖必要的 hook（通常 {@link #fromString}、{@link #fromHostObject}、
 * {@link #defaultValue}），无需重复写 null/instanceof/throw 分发样板。
 *
 * <p><b>无 MC 依赖</b>：{@link #fromHostObject(Object)} 收 {@code Object}（来自
 * {@link Value#asHostObject()}），子类在 platform 侧做 instanceof，本基类不 import 任何
 * Minecraft 类，可安全驻留 common 模块。
 *
 * <p><b>test/apply 一致性</b>：{@link #fromHostObject(Object)} 返回 {@code null} 约定为
 * "不识别此宿主对象"——{@code test} 据此返回 {@code false}（GraalJS 不会转发该 value），
 * {@code apply} 据此抛 {@link ValueConversionException}。两者不会再出现"test 通过但 apply
 * 返回 null"的旧 bug。<b>不要把 {@code null} 作为合法转换结果</b>——适配器返回 null 会让
 * 下游 NPE；若需表达"空值"，请用各类型的空常量（{@code ItemStack.EMPTY}、{@code Items.AIR}）。
 */
public abstract class AbstractJSTypeAdapter<T> implements JSTypeAdapter<T> {

    // ===================== 可配置 hook（子类按需覆盖） =====================

    /**
     * null / 空输入时返回的默认值。默认 {@code null} 表示<b>不接受</b> null 输入
     * （配合 {@link #acceptNull()} 默认实现）。子类若要接受 null（如 Item/Block 返回 AIR），
     * 覆盖此方法返回非 null 的空常量。
     */
    protected T defaultValue() {
        return null;
    }

    /**
     * 是否接受 null 输入。默认依据 {@link #defaultValue()} 是否非 null。
     * 子类可显式覆盖（例如接受 null 但默认值仍为 null 的特殊情况）。
     */
    protected boolean acceptNull() {
        return defaultValue() != null;
    }

    /**
     * 从字符串转换。默认抛 {@link ValueConversionException}（表示本适配器不接受 string 输入）；
     * 接受 string 的子类覆盖此方法。
     */
    protected T fromString(String s) {
        throw new ValueConversionException(getTargetClass(), "string", s,
            "adapter does not accept string input");
    }

    /**
     * 从宿主对象转换。子类做 instanceof 链：
     * <pre>{@code
     * if (host instanceof Foo f) return f;
     * if (host instanceof Bar b) return convert(b);
     * return null;   // 不识别
     * }</pre>
     * 约定：返回 {@code null} 表示"不识别此宿主对象"（勿用作合法结果）。
     * 识别但数据非法时抛 {@link ValueConversionException}。
     */
    protected abstract T fromHostObject(Object host);

    /**
     * 是否接受其它形状（JS 对象 {@code hasMembers} / 数组 {@code hasArrayElements} 等）。
     * 默认 {@code false}；需要对象/数组输入的子类覆盖。
     */
    protected boolean acceptOther(Value value) {
        return false;
    }

    /**
     * 对 {@link #acceptOther(Value)} 为 true 的输入执行转换。默认抛异常。
     */
    protected T fromOther(Value value) {
        throw new ValueConversionException(getTargetClass(), "value", value,
            "adapter does not accept this input shape");
    }

    // ===================== 模板（final，子类不可覆盖） =====================

    @Override
    public final boolean test(Value value) {
        if (value == null || value.isNull()) return acceptNull();
        if (value.isString()) return true;
        if (value.isHostObject()) {
            try {
                return fromHostObject(value.asHostObject()) != null;
            } catch (ValueConversionException e) {
                // 宿主对象识别失败 -> 不接受
                return false;
            }
        }
        return acceptOther(value);
    }

    @Override
    public final T apply(Value value) {
        if (value == null || value.isNull()) {
            if (!acceptNull()) {
                throw new ValueConversionException(getTargetClass(), "non-null value", value,
                    "null input is not accepted by this adapter");
            }
            return defaultValue();
        }
        if (value.isString()) {
            return fromString(value.asString());
        }
        if (value.isHostObject()) {
            T r = fromHostObject(value.asHostObject());
            if (r == null) {
                throw new ValueConversionException(getTargetClass(), "recognized host object", value,
                    "unrecognized host object type for this adapter");
            }
            return r;
        }
        if (acceptOther(value)) {
            return fromOther(value);
        }
        throw new ValueConversionException(getTargetClass(), "string | recognized host object | supported shape", value,
            "unsupported input shape");
    }
}
