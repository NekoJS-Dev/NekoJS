package com.tkisor.nekojs.script.prop;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author ZZZank
 */
public final class ScriptProperty<T> {

    /// ```
    /// // priority: 123
    /// ```
    public static final ScriptProperty<Integer> PRIORITY = new ScriptProperty<>("priority", 0, Integer::valueOf);
    /// ```
    /// // modloaded: examplemod,example2, graal
    /// ```
    public static final ScriptProperty<List<String>> MODLOADED = new ScriptProperty<>(
        "modloaded",
        Collections.emptyList(),
        (s) -> Arrays.stream(s.split(","))
            .map(String::trim)
            .filter((str) -> !str.isEmpty())
            .toList()
    );
    /// ```
    /// // disable:
    /// or
    /// // disable: anything here, we don't care about the content
    /// ```
    public static final ScriptProperty<Boolean> DISABLE = new ScriptProperty<>("disable", false, s -> true);
    /// ```
    /// runs after <root>/aaa/bbb.js
    /// // after: aaa/bbb.js
    ///
    /// runs after <root>/aaa/bbb.js and <parent folder of current file>/ccc.js
    /// // after: aaa/bbb.js, ./ccc.js
    ///
    /// runs after all files in <script_root>/aaa
    /// // after: aaa/*
    ///
    /// invalid references will be ignored
    /// // after: some/invalid/file
    /// ```
    public static final ScriptProperty<List<String>> AFTER = new ScriptProperty<>(
        "after",
        Collections.emptyList(),
        (s) -> Arrays.stream(s.split(","))
            .map(String::trim)
            .filter((str) -> !str.isEmpty())
            .toList()
    );

    public final String name;
    public final T defaultValue;
    public final Function<String, @Nullable T> reader;

    int ordinal;

    public ScriptProperty(String name, T defaultValue, Function<String, @Nullable T> reader) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.reader = reader;
    }

    public T read(String raw) {
        try {
            return reader.apply(raw);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ScriptProperty(%s)".formatted(name);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScriptProperty<?> that && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}