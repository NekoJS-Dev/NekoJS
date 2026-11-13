package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.AdapterInputShape.Slot;
import com.tkisor.nekojs.api.JSTypeAdapter;

import graal.graalvm.polyglot.Value;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A generic record adapter. It reads fields from a JS object literal by record component name
 * and builds a record instance. This is the counterpart of KubeJS Rhino's native record
 * mapping, which GraalJS does not provide.
 *
 * <p>Each field value goes through {@code Value.as} with the component type and so recurses
 * through the targetTypeMapping pipeline; nested types such as ItemStack and IDs use their
 * registered adapters. Enum fields also accept a string name, case-insensitively. A missing
 * field falls back to its declared default, supplied at registration, otherwise a
 * {@link ValueConversionException} is thrown. An unknown field in the object literal always
 * fails, so typos surface early. Record instances passed in from the host pass through as is.
 *
 * <p>Probe shape: the adapter reports {@link AdapterInputShape#object(Slot...)} automatically,
 * with defaulted fields as optional slots, so the {@code $Foo_} alias matches the range the
 * runtime accepts.
 *
 * <p>Registration goes through {@code TypeAdapterDsl.registerRecord} on the platform side.
 * Graal's targetTypeMapping matches on the exact class, so "generic" describes the conversion
 * logic only; registration is still one line per record.
 */
public final class RecordJSTypeAdapter<T extends Record> implements JSTypeAdapter<T> {
    private final Class<T> recordClass;
    private final Map<String, Object> defaults;
    private final RecordComponent[] components;
    private final Set<String> componentNames;
    private final Constructor<T> canonical;

    public RecordJSTypeAdapter(Class<T> recordClass, Map<String, Object> defaults) {
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException(recordClass.getName() + " is not a record");
        }
        this.recordClass = recordClass;
        this.defaults = Map.copyOf(defaults);
        this.components = recordClass.getRecordComponents();
        this.componentNames = Arrays.stream(components).map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());
        for (String key : this.defaults.keySet()) {
            if (!componentNames.contains(key)) {
                throw new IllegalArgumentException("default for unknown component '" + key + "' of "
                        + recordClass.getName());
            }
        }
        try {
            this.canonical = recordClass.getDeclaredConstructor(
                    Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
            this.canonical.setAccessible(true);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalArgumentException("no canonical constructor on " + recordClass.getName(), e);
        }
    }

    @Override
    public Class<T> getTargetClass() {
        return recordClass;
    }

    @Override
    public ConversionPrecedence getPrecedence() {
        return ConversionPrecedence.LOW;
    }

    @Override
    public boolean test(Value value) {
        if (value == null) return false;
        if (value.isHostObject()) {
            Object host = value.asHostObject();
            return recordClass.isInstance(host) || host instanceof Map<?, ?>;
        }
        return value.hasMembers();
    }

    @Override
    public T apply(Value value) {
        if (value == null) {
            throw new ValueConversionException(recordClass, objectShapeDescription(), null,
                    "null is not accepted for records");
        }
        Object host = value.isHostObject() ? value.asHostObject() : null;
        if (recordClass.isInstance(host)) {
            return recordClass.cast(host);
        }
        if (host != null && !(host instanceof Map<?, ?>)) {
            throw new ValueConversionException(recordClass, objectShapeDescription(), value,
                    "expected an object literal, got host object " + host.getClass().getName());
        }
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            String name = component.getName();
            if (value.hasMember(name)) {
                args[i] = convertMember(component, value.getMember(name));
            } else if (defaults.containsKey(name)) {
                args[i] = defaults.get(name);
            } else {
                throw new ValueConversionException(recordClass, objectShapeDescription(), value,
                        "missing field '" + name + "'");
            }
        }
        for (String key : value.getMemberKeys()) {
            if (!componentNames.contains(key)) {
                throw new ValueConversionException(recordClass, objectShapeDescription(), value,
                        "unknown field '" + key + "' (expected: " + String.join(", ", componentNames) + ")");
            }
        }
        try {
            return canonical.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new ValueConversionException(recordClass, objectShapeDescription(), value,
                    "failed to construct " + recordClass.getSimpleName(), e);
        }
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        List<Slot> slots = new ArrayList<>();
        for (RecordComponent component : components) {
            boolean optional = defaults.containsKey(component.getName());
            AdapterInputShape shape = shapeOf(component.getType());
            slots.add(optional ? Slot.opt(component.getName(), shape) : Slot.req(component.getName(), shape));
        }
        return List.of(AdapterInputShape.object(slots));
    }

    @Override
    public Optional<String> syntaxDoc() {
        return Optional.of("object literal: " + objectShapeDescription());
    }

    // ===================== Internals =====================

    private Object convertMember(RecordComponent component, Value memberValue) {
        Class<?> type = component.getType();
        try {
            if (type.isEnum() && memberValue.isString()) {
                return enumValue(type, memberValue.asString());
            }
            Object converted = memberValue.as(type);
            if (converted == null) {
                throw new ValueConversionException(recordClass, fieldShape(component), memberValue,
                        "field '" + component.getName() + "' converted to null");
            }
            return converted;
        } catch (IllegalArgumentException e) {
            throw new ValueConversionException(recordClass, fieldShape(component), memberValue,
                    "field '" + component.getName() + "': " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object enumValue(Class<?> enumClass, String name) {
        Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) enumClass.asSubclass(Enum.class);
        for (Enum<?> constant : enumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(name)) return constant;
        }
        throw new IllegalArgumentException("unknown " + enumType.getSimpleName() + " '" + name
                + "' (expected: " + Arrays.stream(enumType.getEnumConstants())
                .map(Enum::name).collect(Collectors.joining(", ")) + ")");
    }

    /** One classification of field types. The probe shape and the error text share this mapping, so
     * they cannot drift apart. */
    private enum FieldShapeKind {
        STRING, BOOLEAN, NUMBER, ENUM, HOST
    }

    private static FieldShapeKind classify(Class<?> type) {
        if (type == String.class) return FieldShapeKind.STRING;
        if (type == boolean.class || type == Boolean.class) return FieldShapeKind.BOOLEAN;
        if (type.isPrimitive() || Number.class.isAssignableFrom(type)) return FieldShapeKind.NUMBER;
        if (type.isEnum()) return FieldShapeKind.ENUM;
        return FieldShapeKind.HOST;
    }

    private String objectShapeDescription() {
        StringBuilder sb = new StringBuilder("{ ");
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            if (i > 0) sb.append(", ");
            sb.append(component.getName()).append(defaults.containsKey(component.getName()) ? "?" : "")
                    .append(": ").append(simpleShape(component.getType()));
        }
        return sb.append(" }").toString();
    }

    private String fieldShape(RecordComponent component) {
        return "object field '" + component.getName() + "': " + simpleShape(component.getType());
    }

    private static String simpleShape(Class<?> type) {
        return switch (classify(type)) {
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case NUMBER -> "number";
            case ENUM -> "string (" + type.getSimpleName() + ")";
            case HOST -> type.getSimpleName();
        };
    }

    private static AdapterInputShape shapeOf(Class<?> type) {
        return switch (classify(type)) {
            case STRING, ENUM -> AdapterInputShape.string();
            case BOOLEAN -> AdapterInputShape.bool();
            case NUMBER -> AdapterInputShape.number();
            case HOST -> AdapterInputShape.host(type);
        };
    }
}
