package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.script.ScriptContainer;

import java.nio.file.Path;

/** Frozen host-side entry outcome; it retains no guest values or mutable script properties. */
public record PackScriptView(String scriptId, String selector, Path source, String version,
                             Status status, String reason) {
    public enum Status { READY, EXECUTED, SKIPPED, FAILED }

    public static PackScriptView of(ScriptContainer script, boolean executed) {
        Status status;
        String reason = "";
        if (script.lastError != null) {
            status = Status.FAILED;
            reason = script.lastError.getClass().getSimpleName();
        } else if (!script.shouldRun()) {
            status = Status.SKIPPED;
            reason = script.disabled || script.properties.getOrDefault(com.tkisor.nekojs.script.prop.ScriptProperty.DISABLE)
                    ? "disabled" : "mod_unavailable";
        } else {
            status = executed ? Status.EXECUTED : Status.READY;
        }
        return new PackScriptView(script.id.toString(), script.packId == null ? "workspace"
                : script.packScope.name() + ":" + script.packId, script.path,
                script.packVersion, status, reason);
    }
}
