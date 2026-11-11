package dev.latvian.mods.kubejs.plugin.builtin.wrapper;

public interface StringUtilsWrapper {
    static double parseDouble(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException failure) { return fallback; }
    }
}
