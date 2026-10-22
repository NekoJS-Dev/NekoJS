# Dynamic Registry：COMMIT 后失败的独立验收工作

## 基线与职责

生产源码基线 `1f877b0fa7435be6fea61a44ff2e9266ce2c3c2a`；随后提交仅更新证据与交接。现有 `d91ca974` 的两节点真实 TCP 八阶段证据保持原范围，本工作补齐其中尚未验收的 COMMIT 后客户端激活失败，不把 PREPARE 的 NACK 或超时当成该场景。

主代理独占夹具、必要修复、构建、运行与提交；Luna xhigh 只读调查实际激活/网络代码和现有 TCP helper 的故障注入点，禁止代理编辑、构建或运行。两个现代 NeoForge 节点先生成当前源码对应制品，随后记录 class/JAR/CodeSource 绑定。

## 必须保留的边界

- 独立目录、全新世界、独立端口和账号；仅测试目录开启动态注册 gate，不修改日常游戏配置。
- 通过真实 Socket、正式 PREPARE/ACK/COMMIT/activation-report handler 和 owner thread；不直接调用 ACK/网络 handler，不反射私有 participant，不新增生产测试接口。
- 故障须发生在客户端已经通过 PREPARE、服务器已决定 COMMIT 之后。隔离 helper 可使用其自己的受限注入实现，但不能更改生产检查、伪造成功状态或放宽来源断言。
- 断言来源是公开事务状态、结果/phase log、真实物理注册表和进程退出；保留失败尝试、超时与精确清理记录，不用启动成功代替行为通过。

## 本项已完成的工作

1. 核对实际 COMMIT 失败路径和 degraded 状态的归属、保留范围及重试/重连规则，选出可重复且不污染其他运行的 helper 注入点。
2. 实现最小真实双客户端夹具：先证明正常提交，再触发单客户端 COMMIT 激活失败；分别核对服务器、故障客户端、正常客户端的实际状态与物理条目。
3. 按真实协议实现应有的后续拒绝、重连或重启恢复断言；具体预期从代码和既有契约确定，不能先写一个方便通过的结果。
4. 先跑 26.1.2，锁定夹具与失败边界；再适配 26.2.0。成功会话全部正常停止，并冻结来源、结果和工具；若发现生产缺陷，由主代理单独修复并走回归/复审。

## 实测发现与后续修复

26.1.2 的第三次真实 TCP 尝试已执行前四阶段并发现断线崩溃，详见[失败材料](../architecture-refactor/evidence/2026-10-23-dynamic-commit-red/README.md)。客户端 COMMIT 和在线 STATE_SYNC 都实际注册失败，服务器仍 COMMITTED、B 正常、A 保留旧条目；随后 kick 时，失败构造留下的两个 intrusive holder 使 NeoForge 重冻注册表崩溃。场景保持 NOT GREEN，三项恢复阶段及 26.2.0 尚待执行。

共享修复点为 `RegistrySurgery.withUnfrozenAndHolders`：保留已有 pending identity，失败时只删除本次新增的未绑定 identity，不重新加入已注册消费的旧 holder。先补真实 `MappedRegistry` 回归，再复验原七阶段，不改变故障注入点回避问题。v1/v2 的夹具启动和观察器失败也保留；没有据计划或四阶段通过关闭票。重载性能、完整严格 TSC、最终客户端/分离回退和维护者结论仍保持各自门禁。

共享修复现已提交 `b47734c6`，[四节点 16 项真实注册表回归与先红后绿记录](../architecture-refactor/evidence/2026-10-23-intrusive-holder-fix/README.md)已封存，guard 零警告，两轴只读审查无剩余项。首次 Fabric 测试的重复 tag 绑定前提已纠正并留证。

随后[四现代节点完整 build](../architecture-refactor/evidence/2026-10-23-holder-modern-builds/README.md)通过，2212 tests / 236 skipped / 零失败错误。同一批 NeoForge JAR 的[两节点七阶段真实 TCP 复验](../architecture-refactor/evidence/2026-10-23-dynamic-commit-recovery/README.md)全部通过：故障 A 断线不再崩溃、持续故障时配置同步拒绝、移除故障后同进程恢复，再次双客户端提交成功；六进程正常退出 0，无强制清理。1099 源码、每节点 1640 编译类、各进程五个加载类与确切 JAR 的来源均已独立核对并封存。

这里的实际契约是服务器保持 COMMITTED，A 在线同步失败后仍连接，直到显式 kick；没有全局 DEGRADED 或一律拒绝未来提交的承诺。26.2.0 客户端/服务端 loader 分别为现有缓存 26.2.0.57 / 26.2.0.75。此项补齐旧八阶段材料的 COMMIT 后失败与恢复范围，不代替正式安装器、完整 IDE、性能、最终统一候选客户端/回退或维护者结论。
