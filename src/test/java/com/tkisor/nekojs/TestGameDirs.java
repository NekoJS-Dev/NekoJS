package com.tkisor.nekojs;

import java.nio.file.Path;

/**
 * 测试 Platform 桩共用的 gameDir 解析：固定 base 名 + 当前进程 PID。
 * 多节点 test 任务会被 Gradle 并行执行（org.gradle.parallel=true），不同 worktree 的
 * 构建也可能同机并行——固定名 tmp 目录会让并行 JVM 的脚本目录清扫互删对方的 fixture
 * （票 17 实测：双 fabric 节点并行时 NetworkGenerationRoutingTest 偶发候选脚本零执行）。
 * PID 后缀使跨 JVM 目录相撞在结构上不可能；同 JVM 内先到先得的 Platform 初始化
 * 寄生在别人的唯一目录上是无害的（测试类顺序执行）。
 */
public final class TestGameDirs {

    private TestGameDirs() {}

    public static Path unique(String base) {
        return Path.of(System.getProperty("java.io.tmpdir"), base + '-' + ProcessHandle.current().pid());
    }
}
