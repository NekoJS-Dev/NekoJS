package dev.latvian.mods.kubejs.script;

/** Test-only phase identity; there is no legacy script manager behind this enum. */
public enum ScriptType implements ScriptTypeHolder {
    STARTUP, SERVER, CLIENT;

    @Override
    public ScriptType kjs$getScriptType() {
        return this;
    }
}
