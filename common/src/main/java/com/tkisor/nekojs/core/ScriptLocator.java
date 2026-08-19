package com.tkisor.nekojs.core;


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
 * 专门负责在文件系统中发现和整理脚本文件
 *
 * <p>发现顺序：脚本包（GLOBAL 字母序 → WORLD 字母序，见 {@link ScriptPackRegistry#enabledPacks()}）
 * 的 {@code <pack>/<type>_scripts/} 目录在前，平铺的 {@code nekojs/<type>_scripts/} 在后；
 * 同一目录内按路径排序。包内脚本的 ScriptId path 携带 {@code packs/<id>/} 或
 * {@code worldpacks/<id>/} 前缀（见 {@link ScriptPack#idPathPrefix()}），据此可在世界卸载时
 * 按前缀反注册该包注册的监听器。
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
     * 参数化版本：包列表由调用方提供（测试注入用），平铺目录仍取 {@code type.path}。
     */
    public static List<ScriptContainer> discover(
        ScriptType type,
        ScriptPropertyRegistry propertyRegistry,
        ScriptFilePolicy filePolicy,
        ScriptPackRegistry packs
    ) {
        List<ScriptContainer> containers = new ArrayList<>();
        for (ScriptPack pack : packs.enabledPacks()) {
            for (Path path : discoverScriptFiles(pack.scriptsDirFor(type), filePolicy, type.logger())) {
                containers.add(packContainer(type, pack, path, propertyRegistry));
            }
        }
        for (Path path : discoverScriptFiles(type.path, filePolicy, type.logger())) {
            containers.add(new ScriptContainer(type.makeId(path), type, path, propertyRegistry));
        }
        return containers;
    }

    private static ScriptContainer packContainer(
        ScriptType type, ScriptPack pack, Path file, ScriptPropertyRegistry propertyRegistry
    ) {
        String relative = pack.scriptsDirFor(type).relativize(file).toString().replace('\\', '/');
        ScriptId id = ScriptId.of("nekojs", type.name + "/" + pack.idPathPrefix() + relative);
        return new ScriptContainer(id, type, file, propertyRegistry, pack.id(), pack.scope());
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
            String relative = type.path.relativize(path).toString().replace('\\', '/');
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
        return discoverScriptFiles(type.path, filePolicy, type.logger());
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
            logger.error("扫描脚本目录失败: {}", dir, e);
        }

        return files;
    }
}
