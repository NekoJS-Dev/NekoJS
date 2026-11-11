package dev.latvian.mods.kubejs.script;

/** Legacy phase identities mapped onto Neko's root-owned event definitions. */
public enum ScriptType implements ScriptTypeHolder {
    STARTUP, SERVER, CLIENT;

    public static final ScriptType[] VALUES = values();

    public boolean isStartup() { return this == STARTUP; }
    public boolean isServer() { return this == SERVER; }
    public boolean isClient() { return this == CLIENT; }

    @Override
    public ScriptType kjs$getScriptType() {
        return this;
    }

    public com.tkisor.nekojs.api.ScriptType nativeType() {
        return switch (this) {
            case STARTUP -> com.tkisor.nekojs.api.ScriptType.STARTUP;
            case SERVER -> com.tkisor.nekojs.api.ScriptType.SERVER;
            case CLIENT -> com.tkisor.nekojs.api.ScriptType.CLIENT;
        };
    }
}
