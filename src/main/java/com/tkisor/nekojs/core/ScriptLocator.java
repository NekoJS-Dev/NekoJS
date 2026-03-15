package com.tkisor.nekojs.core;

import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.ScriptType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 专门负责在文件系统中发现和整理脚本文件
 */
public final class ScriptLocator {

    private ScriptLocator() {}

    public static List<ScriptContainer> discover(ScriptType type) {
        List<ScriptContainer> containers = new ArrayList<>();
        Path dir = type.path;

        if (!Files.exists(dir)) {
            return containers;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> {
                        String fileName = p.toString().toLowerCase();
                        return fileName.endsWith(".js") ||
                                fileName.endsWith(".ts") ||
                                fileName.endsWith(".tsx") ||
                                fileName.endsWith(".jsx");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(p -> containers.add(new ScriptContainer(type.makeId(p), type, p)));
        } catch (Exception e) {
            type.logger().error("扫描脚本目录失败: {}", dir, e);
        }

        return containers;
    }
}