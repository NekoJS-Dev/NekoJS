package com.tkisor.nekojs.api.data;

import java.math.BigDecimal;
import java.util.Objects;

public sealed interface TextArgument permits
        TextArgument.StringValue,
        TextArgument.NumberValue,
        TextArgument.BooleanValue,
        TextArgument.NestedText {

    String displayString();

    record StringValue(String value) implements TextArgument {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String displayString() {
            return value;
        }
    }

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

    record BooleanValue(boolean value) implements TextArgument {
        @Override
        public String displayString() {
            return Boolean.toString(value);
        }
    }

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
