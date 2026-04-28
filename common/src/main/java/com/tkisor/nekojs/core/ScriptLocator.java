package com.tkisor.nekojs.core;

import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 专门负责在文件系统中发现和整理脚本文件
 */
public final class ScriptLocator {

    private static final Set<String> VALID_SUFFIXES = Set.of("js", "ts", "jsx", "tsx");

    private ScriptLocator() {}

    public static List<ScriptContainer> discover(ScriptType type, ScriptPropertyRegistry propertyRegistry) {
        List<ScriptContainer> containers = new ArrayList<>();
        Path dir = type.path;

        if (!Files.exists(dir)) {
            return containers;
        }

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("node_modules"))
                    .filter(p -> {
                        var fileName = p.getFileName().toString();
                        var lastDot = fileName.lastIndexOf('.');
                        return lastDot >= 0 && VALID_SUFFIXES.contains(fileName.substring(lastDot + 1));
                    })
                    .sorted()
                    .map(path -> new ScriptContainer(type.makeId(path), type, path, propertyRegistry))
                    .forEach(containers::add);
        } catch (Exception e) {
            type.logger().error("扫描脚本目录失败: {}", dir, e);
        }

        return containers;
    }
}