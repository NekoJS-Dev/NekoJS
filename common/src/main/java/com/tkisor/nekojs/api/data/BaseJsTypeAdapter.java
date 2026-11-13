package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;
import java.util.Objects;

/**
 * Convenient abstract base class for {@link JsTypeAdapter}, dispatching conversion
 * logic by JS value type.
 *
 * <p>Subclasses only need to implement {@link #fromHostObject(Object)} (host object →
 * target type). Other behaviors (null, string, other JS values) are customized via
 * overridable hook methods. Default behavior: {@code null} not accepted, string
 * unsupported, other JS values not accepted; priority is {@link ConversionPrecedence#LOWEST},
 * and no input shapes are declared.
 *
 * <p>Conversion failures should throw {@link ValueConversionException} rather than returning {@code null}.
 *
 * @param <T> Target Java type
 */
public abstract class BaseJsTypeAdapter<T> implements JsTypeAdapter<T> {
    private final Class<T> targetType;

    /** @param targetType The target Java type for conversion, must not be {@code null}. */
    protected BaseJsTypeAdapter(Class<T> targetType) {
        this.targetType = Objects.requireNonNull(targetType, "targetType");
    }

    /** Returns the target Java type. */
    @Override
    public Class<T> targetType() {
        return targetType;
    }

    /** Default priority is {@link ConversionPrecedence#LOWEST}; can be overridden as needed. */
    @Override
    public ConversionPrecedence precedence() {
        return ConversionPrecedence.LOWEST;
    }

    /** Default is no input shape declarations; override to provide flexible TypeScript input aliases. */
    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of();
    }

    /** Whether {@code null} input is accepted (default {@code false}). */
    protected boolean acceptNull() {
        return false;
    }

    /** Default value for {@code null} input (default {@code null}). */
    protected T defaultValue() {
        return null;
    }

    /** Whether string input is supported (default {@code true}). */
    protected boolean supportsString() {
        return true;
    }

    /** Converts from string to target type; throws {@link ValueConversionException} by default. */
    protected T fromString(String s) {
        throw new ValueConversionException(targetType, "string", s, "not supported");
    }

    /** Converts from host object to target type (must be implemented by subclasses). */
    protected abstract T fromHostObject(Object host);

    /** Whether JS values of other types (not null/string/host object) are accepted (default {@code false}). */
    protected boolean acceptOther(JsValueView value) {
        return false;
    }

    /** Converts from other JS value types; throws {@link ValueConversionException} by default. */
    protected T fromOther(JsValueView value) {
        throw new ValueConversionException(targetType, "other", value, "not supported");
    }

    /** Dispatches by value type: null → acceptNull, string → supportsString and probe fromString, host object → type matching, others → acceptOther. */
    @Override
    public boolean supports(JsValueView value, ConversionContext context) {
        if (value.isNull()) return acceptNull();
        if (value.isString()) {
            if (!supportsString()) return false;
            // Probe fromString: the default implementation throws ValueConversionException, meaning the
            // string shape is not accepted. supports and convert then agree instead of supports passing
            // while convert throws.
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
     * Dispatches on the value kind: null is checked against acceptNull and then replaced by
     * defaultValue, a string goes to fromString, a host object goes to fromHostObject, and
     * anything else goes to fromOther.
     *
     * <p>Matches the contract of {@code AbstractJSTypeAdapter#apply}: when null is not
     * accepted it throws {@link ValueConversionException} instead of silently returning the
     * default value. {@code fromHostObject} and {@code fromOther} treat a {@code null} result
     * for an accepted input the same way and throw, because returning null would leak null into
     * Graal or cause a downstream NPE.
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
