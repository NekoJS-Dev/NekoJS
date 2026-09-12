# 2026-09-12 fabric 冷构建修复验证（工单 01 follow-up）

修复动机：26.1.2-fabric 的 `deps.fabric_source_node` 指向自身，fabric convention 在 stonecutter 已挂载节点本地源之后再注入同一目录，`processResources` 收到两份 copy root，冷构建确定性失败（基线报告 §7.1，工单 01 发现）。修复（**`14de611f`**，`fix(fabric): skip self-referential source-node injection in fabric-node convention`）只做最小接线改动：源根注入仅在借用其他节点时进行（bridge 节点 26.2.0-fabric → 26.1.2-fabric），自源节点（26.1.2-fabric）依赖 stonecutter 挂载。不改 `duplicatesStrategy`、不放宽任何命令。

## 修复 diff（`buildSrc/src/main/kotlin/nekojs.fabric-node.gradle.kts`，第 92 行区域）

Before（`14de611f^` = `62d5163a`）：

```kotlin
val fabricSourceRoot = rootProject.file("versions/$fabricSourceNode/src/main")
sourceSets.main {
    java.srcDir(fabricSourceRoot.resolve("java"))
    resources.srcDir(fabricSourceRoot.resolve("resources"))
    java.exclude("**/NeoForge*.java")
}
```

After（`14de611f`，节选；convention 另补注释说明根因）：

```kotlin
val fabricSourceRoot = rootProject.file("versions/$fabricSourceNode/src/main")
if (fabricSourceNode != project.name) {
    sourceSets.main {
        java.srcDir(fabricSourceRoot.resolve("java"))
        resources.srcDir(fabricSourceRoot.resolve("resources"))
    }
}
sourceSets.main {
    java.exclude("**/NeoForge*.java")
}
```

## 验证结论摘要（隔离 worktree `NekoJS-fabricfix`，detached @ `14de611f`，2026-09-12）

1. **五节点冷构建全绿**（fresh worktree，本机用户缓存温热、命中部分 FROM-CACHE 属预期并如实记录）：
   - `:common:check` + `:common-api-processor:test` ✓（BUILD SUCCESSFUL in 46s）
   - `guardLint` ✓（守卫块 252 / 332 文件 / 豁免 0 / 警告 0）
   - nbtSmokeTest ×3 ✓（FROM-CACHE）
   - 五节点 `build` ✓（**BUILD SUCCESSFUL in 40s**，81 tasks: 32 executed, 6 from cache, 43 up-to-date；`:26.1.2-fabric:processResources` 在本 worktree 冷执行成功）——修复前该任务 3 次执行全部失败。
2. **eachFile 单次拷贝证据**（`trace-copyspec.init.gradle`，格式与修复前 trace 同构）：`:26.1.2-fabric:processResources` 与 `:26.2.0-fabric:processResources` 均只输出 5 条 COPY——4 个 mixin/accesswidener 文件**各一次**（修复前各两次），`fabric.mod.json` 来自 `build/generated/sources/modMetadata`。
3. **NeoForge 无回归**：三 jar SHA-256 与基线逐字节一致（本次记录完整值）：
   - 1.21.1 = `fbd1bb9eefcd77314961ade17b390951b97553e8093e0c3b949bdbf1530b0f00`
   - 26.1.2 = `7d3ce6d2e3a435c5210123201d88ebc37339413bac25a8a329d9ba420f4a2eb0`
   - 26.2.0 = `ca41eab78ebe0b970f7f0001ef123782f28e9a8d63f9da3a46e04eb8a3c7bb7a`
4. **fabric 产物与测试实测**（详见基线报告 §5/§6 与实测 manifest 更新）：
   - 26.1.2-fabric：`nekojs-fabric-26.1.2-1.1.0-preview3.jar`，17,397,691 B，SHA-256 `3060a2b5b1f6a6923f9ba2926a84ada8b594bfdf27954c40d097590da899962e`，7399 entries；mixin 29+13+1；无 AT/mods.toml/NeoForge mixin/NeoForge 类。
   - 26.2.0-fabric：`nekojs-fabric-26.2-1.1.0-preview3.jar`，17,397,689 B，SHA-256 `9572bf56cdda062c320c0cd529f315cd512bd259c770216ec0cdeaa4fb98c25d`（与修复前基线 jar 前缀 `9572bf56cdda062c` 一致，产物逐字节不变），7399 entries。
   - `verifyFabricRuntimeArtifact` / `check` / `test` 两节点均执行并通过；fabric test 实测 = 8 suites / 37 tests / 6 skipped / 0 failed（两节点相同；跳过 = BindingHelpersTest×2 + RegistryAutoAdapterScannerTest×4，与 NeoForge 26.x 相同的裸 JVM assume）。
   - **remapJar 在本配置下不存在**：Loom 1.17.20（loom-back-compat 的 loomx 变体）未注册 remapJar，`jar` 输出即 build/libs 唯一最终 jar（CI 也直接收集 `versions/*/build/libs/*.jar`）；显式调用 `:26.1.2-fabric:remapJar` 报 `task 'remapJar' not found`（证据 `win-fix-fabric-remapjar-notfound.log`）。基线文档中"remap 前中间 jar"的说法据此更正。
5. **Graal 通道闭环（基线 not-verified #9）**：fabric 最终 jar 内**不含** Graal（`org/graalvm/**` 0 条、`com/oracle/truffle/**` 0 条；convention 的 `embeddedCommonRuntime` 显式过滤 graal-1504336）；dev/runtime classpath 上两节点均解析为 `curse.maven:graal-1504336:8762962 -> 8762963`（convention 重定向**生效**——基线 §0"未生效"是对日志箭头记法的误读，本次更正）。生产环境 Graal 由运行方提供，`fabric.mod.json` depends 未声明 graal（31/32 号票可评估是否补充）。jar 携带：icu4j 提取类 5,762 条（`extractIcuClasses` 设计行为）、night-config 3.8.3 272 条（`bundled` 配置设计行为）。
6. **性质声明**：本修复属最小接线修复，不构成"Fabric 源所有权"迁移的实现约束——31 号票仍可在其基础上重做源根所有权收口（convention/stonecutter 二选一）。

## 回滚方式

`git revert 14de611f`（回滚后 26.1.2-fabric 冷构建将复现 §7.1 失败；增量状态下可能被掩盖）。

## logs/ 索引

| 文件 | 内容 |
|---|---|
| `gradle-version.txt` | worktree 内 `gradlew --version`（Gradle 9.6.0，daemon=jdk-25.0.2 来自用户级 `org.gradle.java.home`） |
| `win-fixbuild-1-common-check.log` | 冷构建第 1 步 `:common:check :common-api-processor:test`（EXIT=0） |
| `win-fixbuild-2-guardlint.log` | 冷构建第 2 步 `guardLint`（EXIT=0） |
| `win-fixbuild-3-nbt-smoke.log` | 冷构建第 3 步三节点 `nbtSmokeTest`（EXIT=0） |
| `win-fixbuild-4-five-node-build.log` | 冷构建第 4 步五节点 `build` 全绿（EXIT=0；含 fabric 任务行与 toolchain/loom 输出） |
| `win-fix-fabric-copyspec-trace.log` | eachFile 跟踪：两 fabric 节点 `cleanProcessResources`+`processResources`，每 entry 单次 COPY |
| `trace-copyspec.init.gradle` | 上面跟踪用的 init 脚本（仅加 eachFile 监听，不改任务配置） |
| `win-fix-fabric-remapjar-notfound.log` | 显式调用 remapJar 失败证据（`task 'remapJar' not found`） |
| `win-fix-fabric-test-results.txt` | 两 fabric 节点 JUnit XML 汇总（8/37/6/0） |
| `win-fix-deps-26.1.2-fabric-runtimeClasspath.log` | runtimeClasspath 解析（graal 8762962 -> 8762963、icu4j 77.1、night-config 3.8.3） |
| `win-fix-deps-26.2.0-fabric-runtimeClasspath.log` | 同上（icu4j 78.3、fabric-api 0.159.0+26.2） |
| `jar-facts.py` / `jar-facts-output.txt` | 五节点 jar 解析脚本与输出（SHA-256/entries/metadata/mixin/禁入条目/Graal 通道） |
