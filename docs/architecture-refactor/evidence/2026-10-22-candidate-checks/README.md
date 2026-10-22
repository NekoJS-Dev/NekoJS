# 4405b0b0 统一候选构建、安装运行与 IDE 复验

源码候选：`4405b0b0ba8e4d1665c6e1d92d1b172cc4f18349`。本记录保留实际失败，不把补验改写为首轮全绿。

## 构建

`gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.2" --offline --no-parallel :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`

PASS：11m41s，106 tasks / 40 executed；5067 tests / 271 skipped / 0 failures / 0 errors，包含 common 隔离检查及各节点 platform gate。安装制品的 common/node class 全部与构建逐字节绑定。

## 安装运行

首轮五节点：1.21.1、26.2.0、26.1.2 Fabric、26.2.0 Fabric 通过；26.1.2 未通过。五个服务端均正常退出 0。

26.1.2 首轮成功执行实体召唤命令后，原定 20 次查询仍未找到实体，验证停止于效果/重载之前。这是未确诊的运行观察失败，不能凭后续成功认定根因或源码已修复。

独立新目录/新世界使用同一 JAR、依赖及脚本，仅显式设定测试服 `pause-when-empty-seconds=-1`，原查询次数、间隔和实体/效果/重载/交易门禁不变，完整通过并正常退出 0。该控制结果单独保留；它不证明首次失败由暂停导致。失败日志没有服务器暂停提示。

通过项覆盖实际注册、嵌套配置指纹、方块/流体放置、候选失败保留、交易池生命周期、空动态 generation 保留物理效果及脚本真实宿主集合边界，详见各节点 command-proof、runtime-verdict 和日志。不同节点有实际能力差异，不将启动成功当作所有能力通过。

## IDE

五节点 1609 个 Python stub 的默认 Pyright 均为 0 error / 0 warning。
十入口默认严格 TSC（`skipLibCheck=false`）均退出 2，无编译器崩溃，合计 5867 条诊断：

| 节点 | server | startup |
|---|---:|---:|
| 1.21.1 | 515 | 516 |
| 26.1.2 | 578 | 579 |
| 26.2.0 | 569 | 570 |
| 26.1.2 Fabric | 646 | 647 |
| 26.2.0 Fabric | 623 | 624 |

这些声明来自首轮各节点的实际生成命令，包含未完成运行门禁的 26.1.2，不能改称独立控制组再次生成的输出。完整 IDE gate 仍然 FAIL；未放宽默认包含规则或删除公开成员来隐藏诊断。

归档保存构建 XML/日志、源码和制品绑定、首轮及单节点控制的实际结果、全部生成声明/stub、严格 TSC/Pyright 输出与执行工具。排除世界、JAR/依赖、server.properties、启动参数和人工签字。

离线复核：`python docs/architecture-refactor/evidence/2026-10-22-candidate-checks/verify.py`。归档一致性 PASS 不等于完整运行或 IDE 验收全绿。
