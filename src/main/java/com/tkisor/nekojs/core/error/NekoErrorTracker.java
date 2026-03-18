package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.script.ScriptContainer;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NekoErrorTracker {
    private static final Map<Identifier, ScriptError> ERRORS = new ConcurrentHashMap<>();

    public static void record(ScriptContainer script, Throwable error) {
        ERRORS.put(script.id, new ScriptError(script, error));
    }

    public static void clear(Identifier scriptId) {
        ERRORS.remove(scriptId);
    }

    public static void clearAll() {
        ERRORS.clear();
    }

    public static boolean hasErrors() {
        return !ERRORS.isEmpty();
    }

    public static ScriptError getError(Identifier scriptId) {
        return ERRORS.get(scriptId);
    }

    public static Collection<ScriptError> getAllErrors() {
        return ERRORS.values();
    }
}