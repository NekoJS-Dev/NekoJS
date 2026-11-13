package com.tkisor.nekojs.core;


import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.pack.ScriptPack;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Finds script files on disk and puts them in order.
 *
 * <p>Discovery order: the {@code <pack>/<type>_scripts/} directories of script packs come first
 * (GLOBAL packs by name, then WORLD packs by name, see {@link ScriptPackRegistry#enabledPacks()}),
 * followed by the flat {@code nekojs/<type>_scripts/} directory; files in one directory are sorted
 * by path. A script inside a pack carries a {@code packs/<id>/} or {@code worldpacks/<id>/} prefix
 * in its ScriptId path (see {@link ScriptPack#idPathPrefix()}), so unloading a world can
 * unregister that pack's listeners by prefix.
 */
public final class ScriptLocator {

    private ScriptLocator() {}

    public static List<ScriptContainer> discover(ScriptType type, ScriptPropertyRegistry propertyRegistry) {
        return discover(type, propertyRegistry, ScriptFilePolicy.legacyRuntime());
    }

    public static List<ScriptContainer> discover(ScriptType type, ScriptPropertyRegistry propertyRegistry, ScriptFilePolicy filePolicy) {
        return discover(type, propertyRegistry, filePolicy, ScriptPackRegistry.get());
    }

    /**
     * Same discovery with the pack list supplied by the caller, so tests can inject it. The flat
     * directory still comes from {@code ScriptTypeEnv.scriptsDir(type)}.
     */
    public static List<ScriptContainer> discover(
        ScriptType type,
        ScriptPropertyRegistry propertyRegistry,
        ScriptFilePolicy filePolicy,
        ScriptPackRegistry packs
    ) {
        List<ScriptContainer> containers = new ArrayList<>();
        var dependencyPlan = packs.dependencyPlan();
        for (ScriptPack pack : dependencyPlan.ordered()) {
            List<Path> files = pack.manifest().descriptor() == null
                    ? discoverScriptFiles(pack.scriptsDirFor(type), filePolicy, com.tkisor.nekojs.script.ScriptTypeEnv.logger(type))
                    : pack.manifest().descriptor().entries().getOrDefault(type, List.of()).stream()
                        .map(entry -> pack.scriptsDirFor(type).resolve(entry)).toList();
            for (Path path : files) {
                ScriptContainer container = packContainer(type, pack, path, propertyRegistry);
                container.packDependencies = dependencyPlan.dependencies().getOrDefault(pack.selector(), List.of());
                containers.add(container);
            }
        }
        for (Path path : discoverScriptFiles(ScriptTypeEnv.scriptsDir(type), filePolicy, com.tkisor.nekojs.script.ScriptTypeEnv.logger(type))) {
            containers.add(new ScriptContainer(com.tkisor.nekojs.script.ScriptTypeEnv.makeId(type, path), type, path, propertyRegistry));
        }
        return containers;
    }

    private static ScriptContainer packContainer(
        ScriptType type, ScriptPack pack, Path file, ScriptPropertyRegistry propertyRegistry
    ) {
        String relative = pack.scriptsDirFor(type).relativize(file).toString().replace('\\', '/');
        ScriptId id = ScriptId.of("nekojs", type.name + "/" + pack.idPathPrefix() + relative);
        ScriptContainer container = new ScriptContainer(id, type, file, propertyRegistry, pack.id(), pack.scope(), pack.version());
        container.pack = pack;
        return container;
    }

    public static List<String> suggestScriptFiles(ScriptType type, String input) {
        return suggestScriptFiles(type, input, ScriptFilePolicy.legacyRuntime());
    }

    public static List<String> suggestScriptFiles(ScriptType type, String input, ScriptFilePolicy filePolicy) {
        String normalizedInput = input == null ? "" : input.replace('\\', '/');
        int slash = normalizedInput.lastIndexOf('/');
        String directoryPrefix = slash < 0 ? "" : normalizedInput.substring(0, slash + 1);
        Set<String> suggestions = new LinkedHashSet<>();
        for (Path path : discoverScriptFiles(type, filePolicy)) {
            String relative = ScriptTypeEnv.scriptsDir(type).relativize(path).toString().replace('\\', '/');
            if (!relative.startsWith(directoryPrefix)) {
                continue;
            }
            String remainder = relative.substring(directoryPrefix.length());
            int nextSlash = remainder.indexOf('/');
            if (nextSlash >= 0) {
                suggestions.add(directoryPrefix + remainder.substring(0, nextSlash + 1));
            } else {
                suggestions.add(relative);
            }
        }
        return List.copyOf(suggestions);
    }

    private static List<Path> discoverScriptFiles(ScriptType type, ScriptFilePolicy filePolicy) {
        return discoverScriptFiles(ScriptTypeEnv.scriptsDir(type), filePolicy, com.tkisor.nekojs.script.ScriptTypeEnv.logger(type));
    }

    private static List<Path> discoverScriptFiles(Path dir, ScriptFilePolicy filePolicy, org.slf4j.Logger logger) {
        List<Path> files = new ArrayList<>();

        if (dir == null || !Files.exists(dir)) {
            return files;
        }

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("node_modules"))
                    .filter(filePolicy::isSupportedScriptFile)
                    .sorted()
                    .forEach(files::add);
        } catch (Exception e) {
            logger.error("[NEKO-1001] 扫描脚本目录失败: {} — failed to scan script directory", dir, e);
        }

        return files;
    }
}
