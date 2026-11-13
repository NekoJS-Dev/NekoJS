package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;

import java.nio.file.Path;

/**
 * A scanned script pack: manifest metadata, scope and enabled state (the state file wins over the
 * manifest default). Script directories in the pack are {@code <root>/<type.name>_scripts/}, the
 * same naming rule as flat script directories.
 */
public record ScriptPack(
    String id,
    String name,
    String version,
    ScriptPackScope scope,
    Path root,
    boolean enabled,
    ScriptPackManifest manifest
) {

    /** Source-qualified selector for management; not a runtime activation identity. */
    public String selector() {
        if (scope == ScriptPackScope.SERVER_CACHE && manifest.descriptor() != null) {
            String directory = root.getFileName().toString();
            for (String origin : java.util.List.of("packs", "worldpacks")) {
                if (directory.equals(origin + "_" + id)) return scope.name() + ":" + origin + ":" + id;
            }
        }
        return scope.name() + ":" + id;
    }

    /** Script directory of this pack for one script type. It may not exist, so callers check first. */
    public Path scriptsDirFor(ScriptType type) {
        return root.resolve(type.scriptsDirectoryName());
    }

    /**
     * Prefix segment of the ScriptId path for scripts in this pack: {@code packs/<id>/} for GLOBAL or
     * {@code worldpacks/<id>/} for WORLD. {@code ScriptLocator} builds the full ScriptId as
     * {@code nekojs:<type>/<prefix segment><path inside the pack>}, and the same prefix is the key used
     * to unregister listeners by scriptId prefix when a world unloads.
     */
    public String idPathPrefix() {
        if (scope == ScriptPackScope.SERVER_CACHE && manifest.descriptor() != null) {
            return scope.idSegment() + "/" + root.getFileName() + "/";
        }
        return scope.idSegment() + "/" + id + "/";
    }

    /**
     * Full prefix including the ScriptType segment, used to unregister a whole pack:
     * {@code nekojs:<type>/<idPathPrefix>}.
     */
    public String scriptIdPrefix(ScriptType type) {
        return "nekojs:" + type.name + "/" + idPathPrefix();
    }

    @Override
    public String toString() {
        return scope + ":" + id + "@" + root;
    }
}
