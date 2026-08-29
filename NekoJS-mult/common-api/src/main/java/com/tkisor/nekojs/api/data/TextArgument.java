package com.tkisor.nekojs.api.data;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 富文本的插入参数（可变类型），用于 {@code Text.translatable(...)} 等占位符。
 *
 * <p>包含字符串 / 数字 / 布尔 / 嵌套文本四类。{@link #displayString()} 返回与语言无关的
 * 展示文本（嵌套文本除外，会抛异常）。
 */
public sealed interface TextArgument permits
        TextArgument.StringValue,
        TextArgument.NumberValue,
        TextArgument.BooleanValue,
        TextArgument.NestedText {

    /** 返回与语言无关的展示字符串。 */
    String displayString();

    /** 字符串参数。 */
    record StringValue(String value) implements TextArgument {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String displayString() {
            return value;
        }
    }

    /** 数字参数（保留 {@link JsNumber} 的原始文本表示）。 */
    record NumberValue(Number value) implements TextArgument {
        public NumberValue {
            Objects.requireNonNull(value, "value");
            double number = value.doubleValue();
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("number must be finite");
            }
        }

        @Override
        public String displayString() {
            if (value instanceof JsNumber number) {
                return number.canonicalText();
            }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                return value.toString();
            }
            return javascriptNumber(value.doubleValue());
        }

        /** 返回数值对应的原生 Number（{@link JsNumber} 解析为 Long/Double，其余返回原值）。 */
        public Number nativeNumber() {
            return value instanceof JsNumber number ? number.nativeNumber() : value;
        }

        private static String javascriptNumber(double value) {
            if (value == 0.0d) return "0";
            double absolute = Math.abs(value);
            BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
            if (absolute >= 1.0e-6 && absolute < 1.0e21) {
                return decimal.toPlainString();
            }
            String digits = decimal.unscaledValue().abs().toString();
            int exponent = digits.length() - decimal.scale() - 1;
            StringBuilder result = new StringBuilder();
            if (value < 0) result.append('-');
            result.append(digits.charAt(0));
            if (digits.length() > 1) result.append('.').append(digits.substring(1));
            result.append('e');
            if (exponent >= 0) result.append('+');
            return result.append(exponent).toString();
        }
    }

    /** 布尔参数。 */
    record BooleanValue(boolean value) implements TextArgument {
        @Override
        public String displayString() {
            return Boolean.toString(value);
        }
    }

    /** 嵌套富文本参数（无语言无关展示字符串）。 */
    record NestedText(TextValue value) implements TextArgument {
        public NestedText {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String displayString() {
            throw new UnsupportedOperationException("nested text has no locale-independent display string");
        }
    }
}
