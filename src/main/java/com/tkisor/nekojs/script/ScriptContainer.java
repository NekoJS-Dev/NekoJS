package com.tkisor.nekojs.script;

import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public final class ScriptContainer {
    public final Identifier id;
    public final ScriptType type;
    public final Path path;

    public boolean disabled = false;
    public Throwable lastError;

    public ScriptContainer(Identifier id, ScriptType type, Path path) {
        this.id = id;
        this.type = type;
        this.path = path;
    }

    public boolean isType(ScriptType type) {
        return this.type == type;
    }
}
