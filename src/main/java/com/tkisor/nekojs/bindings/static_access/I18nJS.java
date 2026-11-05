package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.facade.I18nFacade;
import graal.graalvm.polyglot.Value;

/** Minecraft translation adapter shared by the supported client nodes. */
public final class I18nJS implements I18nFacade {
    @Override
    public String translate(String key, Object... arguments) {
        requireText(key);
        return net.minecraft.client.resources.language.I18n.get(key, formatArguments(arguments));
    }

    @Override
    public boolean has(String key) {
        requireText(key);
        return net.minecraft.locale.Language.getInstance().has(key);
    }

    @Override
    public String translateWithFallback(String key, String fallback, Object... arguments) {
        requireText(key);
        requireText(fallback);
        Object[] values = formatArguments(arguments);
        if (has(key)) return net.minecraft.client.resources.language.I18n.get(key, values);
        try {
            return String.format(java.util.Locale.ROOT, fallback, values);
        } catch (java.util.IllegalFormatException invalidFormat) {
            // Retain Minecraft's visible malformed-format result without resolving fallback as a key.
            return "Format error: " + fallback;
        }
    }

    @Override
    public String getLanguageCode() {
        return net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
    }

    @Override
    public boolean isRightToLeft() {
        return net.minecraft.locale.Language.getInstance().isDefaultRightToLeft();
    }

    private static void requireText(String text) {
        if (text == null) throw invalidArgument();
    }

    private static Object[] formatArguments(Object[] arguments) {
        // Java varargs maps a lone guest null to a null array; it remains one format argument.
        if (arguments == null) return new Object[] { null };
        Object[] values = new Object[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            Object value = arguments[index];
            if (value instanceof Number) {
                Value number = Value.asValue(value);
                if (number.fitsInLong()) value = number.asLong();
                else value = number.asDouble();
            } else if (value != null && !(value instanceof String) && !(value instanceof Boolean)) {
                throw invalidArgument();
            }
            values[index] = value;
        }
        return values;
    }

    private static IllegalArgumentException invalidArgument() {
        return new IllegalArgumentException("[NEKO-4030] I18n requires non-null string keys/fallbacks and string, number, boolean or null format arguments");
    }
}
