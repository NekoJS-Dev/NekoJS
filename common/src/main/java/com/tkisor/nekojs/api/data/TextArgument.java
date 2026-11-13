package com.tkisor.nekojs.api.data;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An insert argument for rich text, used as a placeholder in calls such as
 * {@code Text.translatable(...)}.
 *
 * <p>There are four kinds: string, number, boolean and nested text. {@link #displayString()}
 * returns display text that does not depend on the language; nested text is the exception and
 * throws.
 */
public sealed interface TextArgument permits
        TextArgument.StringValue,
        TextArgument.NumberValue,
        TextArgument.BooleanValue,
        TextArgument.NestedText {

    /** Returns the display string, which does not depend on the language. */
    String displayString();

    /** A string argument. */
    record StringValue(String value) implements TextArgument {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String displayString() {
            return value;
        }
    }

    /** A number argument. It keeps the original text form of a {@link JsNumber}. */
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

        /** Returns the native Number behind the value: a {@link JsNumber} is resolved to a Long or a
         * Double, anything else is returned as is. */
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

    /** A boolean argument. */
    record BooleanValue(boolean value) implements TextArgument {
        @Override
        public String displayString() {
            return Boolean.toString(value);
        }
    }

    /** A nested rich text argument. It has no language-independent display string. */
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
