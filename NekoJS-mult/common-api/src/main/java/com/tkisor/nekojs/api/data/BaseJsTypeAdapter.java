package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;
import java.util.Objects;

/**
 * {@link JsTypeAdapter} 的便捷抽象基类，按 JS 值类型分派转换逻辑。
 *
 * <p>子类只需实现 {@link #fromHostObject(Object)}（host object → 目标类型），
 * 其余行为（null、字符串、其他 JS 值）通过可覆盖的钩子方法定制。默认行为：
 * 不接受 {@code null}、不支持字符串、不接受其他 JS 值，优先级为
 * {@link ConversionPrecedence#LOWEST}，无输入形状声明。
 *
 * <p>转换失败应抛 {@link ValueConversionException} 而非返回 {@code null}。
 *
 * @param <T> 目标 Java 类型
 */
public abstract class BaseJsTypeAdapter<T> implements JsTypeAdapter<T> {
    private final Class<T> targetType;

    /** @param targetType 适配器转换的目标 Java 类型，不能为 {@code null}。 */
    protected BaseJsTypeAdapter(Class<T> targetType) {
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    /** 返回目标 Java 类型。 */
    @Override
    public Class<T> targetType() {
        return targetType;
    }

    /** 默认优先级为 {@link ConversionPrecedence#LOWEST}，可按需覆盖。 */
    @Override
    public ConversionPrecedence precedence() {
        return ConversionPrecedence.LOWEST;
    }

    /** 默认无输入形状声明；按需覆盖以提供宽松的 TypeScript 输入别名。 */
    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of();
    }

    /** 是否接受 {@code null} 输入（默认 {@code false}）。 */
    protected boolean acceptNull() {
        return false;
    }

    /** {@code null} 输入对应的默认值（默认 {@code null}）。 */
    protected T defaultValue() {
        return null;
    }

    /** 是否支持字符串输入（默认 {@code true}）。 */
    protected boolean supportsString() {
        return true;
    }

    /** 从字符串转换为目标类型；默认抛 {@link ValueConversionException}。 */
    protected T fromString(String s) {
        throw new ValueConversionException(targetType, "string", s, "not supported");
    }

    /** 从 host object 转换为目标类型（子类必须实现）。 */
    protected abstract T fromHostObject(Object host);

    /** 是否接受其他类型（非 null/字符串/host object）的 JS 值（默认 {@code false}）。 */
    protected boolean acceptOther(JsValueView value) {
        return false;
    }

    /** 从其他类型 JS 值转换；默认抛 {@link ValueConversionException}。 */
    protected T fromOther(JsValueView value) {
        throw new ValueConversionException(targetType, "other", value, "not supported");
    }

    /** 按值类型分派：null→acceptNull，字符串→supportsString 且探测 fromString，host object→类型匹配，其余→acceptOther。 */
    @Override
    public boolean supports(JsValueView value, ConversionContext context) {
        if (value.isNull()) return acceptNull();
        if (value.isString()) {
            if (!supportsString()) return false;
            // 探测 fromString：默认实现抛 ValueConversionException（= 实际不接受 string），
            // 避免出现「supports 通过但 convert 抛异常」的不一致
            try {
                fromString(value.asString());
                return true;
            } catch (ValueConversionException e) {
                return false;
            }
        }
        if (value.isHostObject()) {
            Object host = value.asHostObject(Object.class);
            return host != null && targetType.isAssignableFrom(host.getClass());
        }
        return acceptOther(value);
    }

    /**
     * 按值类型分派：null→acceptNull 校验后 defaultValue，字符串→fromString，
     * host object→fromHostObject，其余→fromOther。
     *
     * <p>与旧基类 {@code AbstractJSTypeAdapter#apply} 的契约对齐：
     * 不接受 null 时抛 {@link ValueConversionException} 而非静默返回默认值；
     * {@code fromHostObject} / {@code fromOther} 对已接受的输入返回 {@code null}
     * 同样视为失败抛异常（返回 null 会把 null 泄漏给 Graal / 下游 NPE），而非合法结果。
     */
    @Override
    public T convert(JsValueView value, ConversionContext context) {
        if (value.isNull()) {
            if (!acceptNull()) {
                throw new ValueConversionException(targetType, "non-null value", value,
                    "null input is not accepted by this adapter");
            }
            return defaultValue();
        }
        if (value.isString()) {
            return fromString(value.asString());
        }
        if (value.isHostObject()) {
            T result = fromHostObject(value.asHostObject(Object.class));
            if (result == null) {
                throw new ValueConversionException(targetType, "recognized host object", value,
                    getClass().getSimpleName() + ".fromHostObject returned null for an accepted host input");
            }
            return result;
        }
        T result = fromOther(value);
        if (result == null) {
            throw new ValueConversionException(targetType, "supported shape", value,
                getClass().getSimpleName() + ".fromOther returned null for an accepted input shape");
        }
        return result;
    }
}
