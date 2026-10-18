# 2026-10-18 脚本包资源自动化验收

真实 Graal Engine / Context、磁盘包发现、Sandbox、Node timer 和 root 联合激活路径的自动化检查通过。**仅这一部分 PASS；脚本包总验收仍未完成。** 本轮只复用既有测试 fixture、添加资源验收测试，没有修改生产实现、公开 API、持久化或网络协议。

## 覆盖范围

`PackResourceStabilityTest` 五项全部执行，无跳过：

- 1 / 4 / 8 个独立 SERVER 包，各有私有全局对象、相同大小的 payload、事件监听器、immediate 和长间隔 timer。初始化拒绝已存在的私有 owner 标记，识别 Context 意外复用。
- 完整替换包含 legacy 分区：活动 Context 为 `N+1`，候选期间为 `2*(N+1)`。提交后旧 Context 拒绝求值，manager 反查和四张元数据表不再包含旧代；timer scheduler 终止、任务和 ready 队列清空。事件每个 owner 只回调一次，新代 immediate 各运行一次，旧代和失败候选不得额外执行。
- 四包连续五十次完整重载，始终为 5 个活动 / 10 个峰值 Context。每十次观察 Java 堆和旧 Context 弱引用；定向与最终完整运行均观察到 **250 / 250** 个旧 Context 回收。
- 五次在 DOMAIN_PLAN 拒绝整批候选：每批 5 个候选全部关闭，健康 Context、激活身份及监听器保留；随后恢复成功。root 关闭后注册表回到测试前数量，最后一代 Context 和 scheduler 均释放。

最终完整 common 运行的堆观察（bytes）：

| 包数 | 活动 / 峰值 Context | legacy-only 堆 | 包活动后堆 | 旧代与候选共存时堆 |
|---|---|---:|---:|---:|
| 1 | 2 / 4 | 44983736 | 45328592 | 45683408 |
| 4 | 5 / 10 | 45012400 | 46051168 | 46958112 |
| 8 | 9 / 18 | 45083136 | 47020912 | 48668008 |

四包第 10 / 20 / 30 / 40 / 50 次重载的堆为 `46123312 / 46134264 / 46142096 / 46146080 / 46149528` bytes，首末差 **26216 bytes**，低于既有单 manager 检查的 32 MiB 一次性 Graal/JIT 增长容差，未放宽门槛。

GC 请求后的堆窗口包含包元数据、缓存、payload、JIT 暖机和观察对象，不是 Context 的精确独占成本。定向与完整运行的绝对值不同，均保留原值。关闭资源、解除注册表引用、弱引用观察、堆窗口是不同证据；GC 请求不保证所有对象立即回收。

## 验证及失败留痕

```powershell
./gradlew.bat :common:test --tests com.tkisor.nekojs.core.pack.PackResourceStabilityTest --offline --console=plain --no-configuration-cache
./gradlew.bat :common:check --offline --console=plain --no-configuration-cache
python docs/architecture-refactor/evidence/2026-10-18-pack-resource-acceptance/verify.py
```

固定 JDK 25.0.2、现有 Gradle 缓存。定向：5 tests、0 failures/errors/skipped。首次完整检查：2375 tests、1 failure、4 skipped，`LangGeneratorJSTest.mergesWithExistingEntriesKeepingOldKeys` 原子替换文件时抛 `AccessDeniedException`。其生产源码与测试均未改动；单独运行三项通过，随后不改源码复验完整 common：**2375 tests、0 failures/errors、4 skipped**，`checkCommonIsolation` 和 addon fixture 依赖检查也通过。AccessDenied 根因未知，不能称已修复或已证实为外部问题。

归档保留首次失败、语言文件控制、定向及最终完整成功的全部 XML、日志、调用与源摘要，以及测试源码和运行器。独立验证器重算统计，核对失败内容、Context 数量、堆窗口和恢复记录；完整性 PASS 不扩大验证范围。

生产树仍与 `6e5bb8f0` 一致。本轮没有新增五节点构建或游戏会话；此前相同生产实现的节点证据可引用，这五项 common 检查不能代替 Minecraft 客户端验收。

## 尚缺证据

- 原生内存、进程 RSS / private bytes、较大真实包的成本，不能用 Java 堆替代。
- 客户端同一会话 A→B→A 双世界切换，以及签名同步实际网络验证。
- 真实维护者结论，本轮不代签。
- 重载正式性能仍 FAIL，资源检查通过不能放行发布。
