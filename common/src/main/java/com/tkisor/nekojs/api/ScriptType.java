package com.tkisor.nekojs.api;

/**
 * Script types. This contract enum carries only the names and the predicate semantics; the
 * engine-side environment (script directory, logger, log file, script id) lives in
 * {@code ScriptTypeEnv}.
 */
public enum ScriptType implements ScriptTypePredicate {
    STARTUP("startup", "NekoJS Startup"),
    SERVER("server", "NekoJS Server"),
    CLIENT("client", "NekoJS Client"),
    TEST("test", "NekoJS Test");

    public final String name;
    public final String cname;

    ScriptType(String name, String cname) {
        this.name = name;
        this.cname = cname;
    }

    public String defaultMainScript() {
        return """
                // %s example script
                console.info('Hello, World! (Loaded %s example script)');
                """.formatted(name, name);
    }

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }

    public boolean isStartup() {
        return this == STARTUP;
    }

    public boolean isTest() {
        return this == TEST;
    }

    public boolean acceptsServerApis() {
        return this == STARTUP || this == SERVER || this == TEST;
    }

    public boolean acceptsClientApis() {
        return this == STARTUP || this == CLIENT;
    }

    @Override
    public boolean test(ScriptType type) {
        if (this == SERVER) return type.acceptsServerApis();
        if (this == CLIENT) return type.acceptsClientApis();
        return this == type;
    }

    private static final java.util.List<ScriptType> EXECUTABLE_TYPES = java.util.List.of(STARTUP, SERVER, CLIENT, TEST);
    private static final java.util.List<ScriptType> AUTO_LOAD_TYPES = java.util.List.of(STARTUP, SERVER, CLIENT);

    /**
     * Returns every script type that must be loaded and executed at runtime.
     */
    public static java.util.List<ScriptType> all() {
        return EXECUTABLE_TYPES;
    }

    public static boolean isExecutableTypeName(String name) {
        return EXECUTABLE_TYPES.stream().anyMatch(type -> type.name.equals(name));
    }

    /** The stable path segment used by script roots and generated module identities. */
    public String scriptsDirectoryName() {
        return name + "_scripts";
    }

    /** Resolve a script type from a path segment without consulting platform-owned paths. */
    public static ScriptType fromScriptsDirectoryName(String segment) {
        if (segment == null) return null;
        java.nio.file.FileSystem fileSystem = java.nio.file.FileSystems.getDefault();
        java.nio.file.Path candidate = fileSystem.getPath(segment);
        for (ScriptType type : EXECUTABLE_TYPES) {
            if (candidate.equals(fileSystem.getPath(type.scriptsDirectoryName()))) {
                return type;
            }
        }
        return null;
    }

    public static java.util.List<ScriptType> autoLoadTypes() {
        return AUTO_LOAD_TYPES;
    }
}
