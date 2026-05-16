package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
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
 */
public final class ScriptLocator {

    private ScriptLocator() {}

    public static List<ScriptContainer> discover(ScriptType type, ScriptPropertyRegistry propertyRegistry) {
        List<ScriptContainer> containers = new ArrayList<>();
        for (Path path : discoverScriptFiles(type)) {
            containers.add(new ScriptContainer(type.makeId(path), type, path, propertyRegistry));
        }
        return containers;
    }

    public static List<String> suggestScriptFiles(ScriptType type, String input) {
        String normalizedInput = input == null ? "" : input.replace('\\', '/');
        int slash = normalizedInput.lastIndexOf('/');
        String directoryPrefix = slash < 0 ? "" : normalizedInput.substring(0, slash + 1);
        Set<String> suggestions = new LinkedHashSet<>();
        for (Path path : discoverScriptFiles(type)) {
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

    private static List<Path> discoverScriptFiles(ScriptType type) {
        List<Path> files = new ArrayList<>();
        Path dir = type.path;

        if (dir == null || !Files.exists(dir)) {
            return files;
        }

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("node_modules"))
                    .filter(ScriptCompilerRegistry::isSupportedScriptFile)
                    .sorted()
                    .forEach(files::add);
        } catch (Exception e) {
            type.logger().error("扫描脚本目录失败: {}", dir, e);
        }

        return files;
    }
}