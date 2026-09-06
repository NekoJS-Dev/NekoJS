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
 * 通用 record 适配器：JS 对象字面量按 record component 名逐字段取值构造 record 实例
 * （KubeJS Rhino 原生 record 映射的对标——GraalJS 没有这层）。
 *
 * <p>字段值经 {@code Value.as(字段类型)} 递归走 targetTypeMapping 管线，嵌套的
 * ItemStack/ID 等按已注册的适配器转换；枚举字段显式支持字符串名（大小写不敏感）。
 * 字段缺失：有声明默认值（注册时提供）用默认值，否则抛 {@link ValueConversionException}；
 * 对象字面量里的未知字段一律报错（typo 早暴露）。host 传入的 record 实例原样通过。
 *
 * <p>probe 面：自动产出 {@link AdapterInputShape#object(Slot...)} 形状（有默认值的字段为可选槽位），
 * 使 {@code $Foo_} 别名与运行时接受范围一致。
 *
 * <p>注册经 platform 侧 {@code TypeAdapterDsl.registerRecord}——Graal targetTypeMapping 按
 * 精确类匹配，"通用"指转换逻辑通用，注册仍是每 record 一行。
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

    // ===================== 内部 =====================

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

    /** 字段类型的统一分类：probe 形状与报错文本共用一份映射，不会漂移。 */
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
