package com.tkisor.nekojs.probe;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Golden 重生成支持：系统属性 {@code nekojs.golden.regenerate=true}（gradle 任务 {@code regenerateGoldens}
 * 设置）时，各 golden 对比测试把**实际产物**覆盖写回 golden 资源目录，随后以 assumption 跳过断言，
 * 让重生成运行整体通过；跑完后人工 review 资源目录并提交。
 */
final class ProbeGoldenSupport {

    private ProbeGoldenSupport() {}

    /** 是否处于 golden 重生成模式。 */
    static boolean regenerateEnabled() {
        return Boolean.getBoolean("nekojs.golden.regenerate");
    }

    /**
     * 类路径资源目录解析为 Path（仅支持 file: 协议，与各测试的 golden 加载口径一致）；不可解析返回 null。
     *
     * <p>重生成模式下重写到 <b>src/test/resources 源树</b>：Gradle 下类路径资源指向
     * {@code build/resources/test} 副本，镜像写副本会被 processTestResources 重新覆盖成
     * src 的旧内容——regen 等于空操作。断言（非 regen）模式仍读 build 副本（由 processTestResources
     * 从 src 同步，内容一致）。非 Gradle 布局（独立运行时 classpath 直指 src）找不到
     * build/resources/test 段则原样返回。
     */
    static Path resourceDir(Class<?> owner, String basePath) {
        var url = owner.getResource(basePath);
        if (url == null || !"file".equals(url.getProtocol())) return null;
        final Path resolved;
        try {
            resolved = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            return null;
        }
        if (!regenerateEnabled()) {
            return resolved;
        }
        for (int i = 0; i + 3 < resolved.getNameCount(); i++) {
            if ("build".equals(resolved.getName(i).toString())
                    && "resources".equals(resolved.getName(i + 1).toString())
                    && "test".equals(resolved.getName(i + 2).toString())) {
                Path moduleDir = resolved.subpath(0, i);
                return resolved.getRoot().resolve(moduleDir)
                        .resolve("src/test/resources")
                        .resolve(resolved.subpath(i + 3, resolved.getNameCount()));
            }
        }
        return resolved;
    }

    /**
     * 把 {@code actualRoot} 的整棵文件树镜像覆盖到 {@code goldenRoot}（保留结构，逐文件字节复制），
     * 并删除 golden 中 actual 已不存在的文件及随之清空的目录。镜像语义让重生成幂等：
     * 无论哪个 golden 测试先跑，资源目录最终都与实际产物一致。
     */
    static void mirrorTree(Path actualRoot, Path goldenRoot) throws IOException {
        if (!Files.isDirectory(actualRoot)) {
            throw new IOException("actual tree missing: " + actualRoot);
        }
        Files.createDirectories(goldenRoot);

        Set<String> actualRel = new LinkedHashSet<>();
        Files.walkFileTree(actualRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = actualRoot.relativize(file).toString().replace('\\', '/');
                actualRel.add(rel);
                Path target = goldenRoot.resolve(rel);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        Set<Path> emptyCandidateDirs = new TreeSet<>(Comparator.reverseOrder());
        Files.walkFileTree(goldenRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = goldenRoot.relativize(file).toString().replace('\\', '/');
                if (!actualRel.contains(rel)) {
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                if (!dir.equals(goldenRoot)) {
                    emptyCandidateDirs.add(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        // 深度优先删空目录（子目录先于父目录），非空目录的 delete 会失败并被忽略
        for (Path dir : emptyCandidateDirs) {
            try (var list = Files.list(dir)) {
                if (list.findAny().isEmpty()) {
                    Files.delete(dir);
                }
            }
        }
    }
}
