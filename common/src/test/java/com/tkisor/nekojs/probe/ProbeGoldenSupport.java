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
 * Golden regeneration support. When the system property {@code nekojs.golden.regenerate=true} is set
 * (the {@code regenerateGoldens} gradle task sets it), each golden comparison test writes its actual
 * output over the golden resource directory and then skips its assertions with an assumption, so the
 * whole regeneration run passes. Review the resource directory by hand afterwards and commit it.
 */
final class ProbeGoldenSupport {

    private ProbeGoldenSupport() {}

    /** Whether golden regeneration mode is active. */
    static boolean regenerateEnabled() {
        return Boolean.getBoolean("nekojs.golden.regenerate");
    }

    /**
     * Resolves a classpath resource directory to a Path. Only the file: protocol is supported, which
     * matches how the tests load goldens. Returns null when the directory cannot be resolved.
     *
     * <p>In regeneration mode the write goes to the <b>src/test/resources source tree</b> instead.
     * Under Gradle the classpath resource points at the {@code build/resources/test} copy, and
     * processTestResources overwrites that copy from src, so writing the copy would make regeneration
     * a no-op. Assertion mode (no regen) still reads the build copy, which processTestResources keeps
     * in sync with src. A non-Gradle layout (a standalone runner whose classpath points straight at
     * src) has no build/resources/test segment, so the path is returned unchanged.
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
     * Mirrors the whole file tree under {@code actualRoot} onto {@code goldenRoot}, keeping the
     * structure and copying each file byte for byte, then deletes the files that no longer exist in
     * actual and the directories that become empty. Mirroring makes regeneration idempotent: whichever
     * golden test runs first, the resource directory ends up matching the actual output.
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
        // Deletes empty directories depth-first, so children go before their parents. Deleting a directory
        // that is not empty fails and the failure is ignored.
        for (Path dir : emptyCandidateDirs) {
            try (var list = Files.list(dir)) {
                if (list.findAny().isEmpty()) {
                    Files.delete(dir);
                }
            }
        }
    }
}
