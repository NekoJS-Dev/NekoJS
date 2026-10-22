# Holder 修复：四现代节点普通构建

生产源码 `b47734c6`。`:26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build` 在 JDK 25 Gradle JVM、现有离线缓存、串行节点配置下 **5m23s PASS，80 tasks**；未使用 regenerate。

四节点普通 test 和各自 platform gate 均通过：两个 NeoForge 各 690 tests / 81 skipped，两个 Fabric 各 416 / 37，共 **2212 / 236 skipped / 0 failure/error**。各节点运行时制品门禁通过。归档包含全部实际 XML、日志、JAR 摘要及编译 class → JAR 的逐项绑定；生产源码与提交一致。common 与 legacy 的生产源码未因本 holder 修复改变，不声称本命令重新执行它们的完整检查。

这证明普通构建及组件行为；真实客户端 COMMIT 失败、断线与重连由独立 TCP 材料记录。不能据此关闭性能、完整严格 TSC、最终候选/回退或维护者验收门禁。

复核：`python docs/architecture-refactor/evidence/2026-10-23-holder-modern-builds/verify.py`。
