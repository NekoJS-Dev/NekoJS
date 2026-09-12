# W0 失败证据：去 pin 后在 Zulu 8 环境下 Gradle 无法启动（2026-09-12）

## 复现条件

| 项 | 值 |
|---|---|
| 检出位置 | 隔离 worktree `D:/mcmodDemo/NekoJS/NekoJS-w0-baseline/NekoJS-mult`（detached HEAD `9f7021954fa61af0e312941c9c365b3a2f300620`，不含用户 WIP） |
| 配置改动 | 临时 `sed -i '/^org.gradle.java.home=/d' gradle.properties`（删除 pin 行，复现后已 `git checkout --` 恢复） |
| `JAVA_HOME` | 空（`set JAVA_HOME=`） |
| PATH 上的 `java` | Zulu 8（1.8.0_502 / Zulu 8.96.0.19-CA-win64） |
| 命令 | `cmd /c "set JAVA_HOME=& gradlew.bat help --console=plain"` |
| 退出码 | 1 |

## 真实输出（stdout + stderr 原样，无删改）

```text
Starting a Gradle Daemon, 1 incompatible Daemon could not be reused, use --status for details

FAILURE: Build failed with an exception.

* What went wrong:
Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 8.

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights from a Build Scan (powered by Develocity).
> Get more help at https://help.gradle.org.
```

原始日志文件：[logs/w0-pin-removed-help-failure.log](logs/w0-pin-removed-help-failure.log)

## 补充事实

1. `gradlew.bat --version`（同一环境）**不**触发该失败——它只打印 launcher 信息并正常退出（exit=0，日志 [logs/w0-pin-removed-failure.log](logs/w0-pin-removed-failure.log)），输出中 `Launcher JVM: 1.8.0_502`、`Daemon JVM: ... (no Daemon JVM specified, using current Java home)`。JVM 17+ 校验发生在真正执行任务（启动 daemon）时，因此失败证据以 `gradlew.bat help` 为准。
2. 这正是 `gradle.properties` 旧注释所述的失败形态（"Gradle requires JVM 17 or later" 挡住直接 `./gradlew`），实测文案为 `Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 8.`（Gradle 9.6.0）。
3. 结论：被跟踪的 `org.gradle.java.home` 本机绝对路径 pin 移除后，本机必须改用**用户级** `~/.gradle/gradle.properties` 或 `JAVA_HOME` 提供 17+（推荐 25）JVM；CI 由 setup-java 提供。此项已在 W0 commit 中落地并验证（见基线报告）。
