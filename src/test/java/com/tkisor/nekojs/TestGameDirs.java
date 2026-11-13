package com.tkisor.nekojs;

import java.nio.file.Path;

/**
 * Shared gameDir resolution for the test Platform stubs: a fixed base name plus the current
 * process PID. Gradle runs the test tasks of several nodes in parallel
 * ({@code org.gradle.parallel=true}), and builds of different worktrees can also run on the same
 * machine. With a fixed tmp directory name, parallel JVMs that scrub script directories delete
 * each other's fixtures: with two fabric nodes in parallel,
 * {@code NetworkGenerationRoutingTest} occasionally ran zero candidate scripts. The PID suffix
 * makes a cross-JVM collision impossible by construction. Inside one JVM the first Platform
 * initialization wins, and living in another test's unique directory is harmless because test
 * classes run sequentially.
 */
public final class TestGameDirs {

    private TestGameDirs() {}

    public static Path unique(String base) {
        return Path.of(System.getProperty("java.io.tmpdir"), base + '-' + ProcessHandle.current().pid());
    }
}
