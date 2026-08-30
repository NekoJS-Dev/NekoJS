package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * 平台无关的 JSON 值模型（sealed 接口），用于脚本与 Java 之间传递 JSON 数据。
 *
 * <p>包含 null / 布尔 / 数字 / 字符串 / 数组 / 对象六种变体。数字以字符串 lexeme 形式
 * 保存，以无损保留原始精度与表示（见 {@link NumberValue}）。所有容器变体不可变。
 *
 * <p>常量 {@link #MAX_DEPTH}/{@link #MAX_NODES}/{@link #MAX_INPUT_CHARS}/
 * {@link #MAX_STRING_CHARS}/{@link #MAX_OUTPUT_CHARS} 定义解析/序列化时的资源上限。
 */
@ContractReceiver
public sealed interface JsonValue permits
        JsonValue.NullValue,
        JsonValue.BooleanValue,
        JsonValue.NumberValue,
        JsonValue.StringValue,
        JsonValue.ArrayValue,
        JsonValue.ObjectValue {

    /** 最大嵌套深度。 */
    int MAX_DEPTH = 64;
    /** 最大节点总数。 */
    int MAX_NODES = 10_000;
    /** 输入字符串最大字符数。 */
    int MAX_INPUT_CHARS = 1_048_576;
    /** 单个字符串值最大字符数。 */
    int MAX_STRING_CHARS = 1_048_576;
    /** 输出字符串最大字符数。 */
    int MAX_OUTPUT_CHARS = 1_048_576;

    /** JSON 数字 lexeme 语法（含负数、小数、指数）。 */
    Pattern NUMBER_LEXEME = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    /** 返回 null 值单例。 */
    static NullValue nullValue() {
        return NullValue.INSTANCE;
    }

    /** 构造布尔值。 */
    static BooleanValue bool(boolean value) {
        return new BooleanValue(value);
    }

    /** 以数字 lexeme 构造数值（须匹配 {@link #NUMBER_LEXEME}）。 */
    static NumberValue number(String lexeme) {
        return new NumberValue(lexeme);
    }

    /** 构造字符串值（校验代理项配对与长度上限）。 */
    static StringValue string(String value) {
        return new StringValue(value);
    }

    /** 构造数组（元素须非 null，拷贝为不可变列表）。 */
    static ArrayValue array(List<JsonValue> values) {
        return new ArrayValue(values);
    }

    /** 构造对象（key/value 校验，拷贝为不可变映射）。 */
    static ObjectValue object(Map<String, JsonValue> values) {
        return new ObjectValue(values);
    }

    /** null 值（单例枚举）。 */
    enum NullValue implements JsonValue {
        INSTANCE
    }

    /** 布尔值。 */
    record BooleanValue(boolean value) implements JsonValue {
    }

    /** 数值（以 lexeme 形式保存，须匹配 {@link #NUMBER_LEXEME}）。 */
    record NumberValue(String lexeme) implements JsonValue {
        public NumberValue {
            Objects.requireNonNull(lexeme, "lexeme");
            if (!NUMBER_LEXEME.matcher(lexeme).matches()) {
                throw new IllegalArgumentException("invalid JSON number lexeme: " + lexeme);
            }
        }
    }

    /** 字符串值（校验代理项配对与长度上限）。 */
    record StringValue(String value) implements JsonValue {
        public StringValue {
            validateString(value, "string value");
        }
    }

    /** 数组值（不可变，元素须非 null）。 */
    record ArrayValue(List<JsonValue> values) implements JsonValue {
        public ArrayValue {
            values = List.copyOf(values == null ? List.of() : values);
            values.forEach(value -> Objects.requireNonNull(value, "array value"));
        }
    }

    /** 对象值（不可变，key/value 校验）。 */
    record ObjectValue(Map<String, JsonValue> values) implements JsonValue {
        public ObjectValue {
            Objects.requireNonNull(values, "values");
            Map<String, JsonValue> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                validateString(key, "object key");
                copy.put(key, Objects.requireNonNull(value, "object value"));
            });
            values = Collections.unmodifiableMap(copy);
        }
    }

    private static void validateString(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length() > MAX_STRING_CHARS) {
            throw new IllegalArgumentException(label + " exceeds " + MAX_STRING_CHARS + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new IllegalArgumentException(label + " contains an unpaired high surrogate");
                }
                i++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains an unpaired low surrogate");
            }
        }
    }
}
