# Fabric 服务端定时器：原生失败与实现修复

2026-10-20。修复前源码为 `6bce9c234b5e623412a5ca732634fdfbd9f3df6c`；本记录不把准备成功或正常退出当作验收成功。

## 发现与改动

完整 Fabric 26.2 服务端观测在启用第一个独立包后，脚本重载返回 `phase=COMMIT - no errors.`，但后续 `setImmediate` 未执行。原生 end tick 已持续运行，事件回调未报 ownership 错误。检查调用链确认 Fabric 仅接入 CLIENT 定时器冲刷；服务端的 end tick 和 pdata 冲刷都没有调用运行时定时器入口。common 的包资源检查曾显式调用该入口，未覆盖这处平台接线。

修复在现有 `FabricServerEventBindings` 的 `END_SERVER_TICK` 回调中调用 `NekoRuntimeRoot.flushReadyNodeTimers(SERVER)`，由 root 冲刷旧目录 manager 和全部活动包；root 缺失或 SERVER 类型已被关闭时跳过，不创建 Context。沿用服务器线程，在登录和 tickPost 脚本事件之前执行；不新增 owner、线程、API 或配置，不手动冲刷候选 Context。CLIENT、TEST 自带的测试运行 drain、NeoForge、签名与 wire 合同保持。

新增 source wiring 回归约束调用发生在原生 end tick、使用 root、跳过缺失/关闭的 SERVER、保持 TEST 原有 drain；它本身不能替代原生运行证据。遵循本轮“先完成任务，不要着急测试”，修复后尚未编译、执行测试或重新启动游戏。

## 保留的两次失败

- v1：隔离观测 mod 查询 `EnableJVMCI` 时，当前 VM 的 HotSpotDiagnosticMXBean 抛出 `IllegalArgumentException`。未产生有效样本；游戏崩溃后保存退出，launcher exit 0，`accepted=false / ready=false / normalStop=false`。不能由 launcher 成功推断游戏验收通过。
- v2：只把不通用的 VM 选项读取改为可选字段，保留所有资源断言。准备、legacy 基线采样通过；首包 COMMIT 后 `packprobe sample scale1 1` 返回 `Immediate did not run once`，随后 RCON 正常停服，`accepted=false / ready=true / normalStop=true`。没有完成 1/4/8 包成本或五十次重载序列。

v2 的实际引擎标识是 `Interpreted`、JavaScript 为 `GraalVM JavaScript` 25.1.3，加载日志含 GraalMC 25.1.3.7。缺失 VM 选项不等于选项为 false；加载 GraalMC 不证明 JS 已编译。下一版观测将记录依赖自带的公开 `Engine.supportsCompilation()`，不因其结果为 false 而伪造优化引擎结论。这里只保留一个 legacy 内存点，不计算每包成本或回收趋势。

## 证据与后续

[evidence.zip](evidence.zip) 含 38 个条目：两次探针源码、控制器、fixture、启动/编译日志、生产类摘要，以及 v2 的实际命令和唯一基线点；没有 server.properties、RCON 密码、world 或信任数据。SHA-256 为 `daf9316daab3314b67a2df023c8c323370dd35890496c6f8383c14e30890a1de`。[verification.json](verification.json) 记录逐文件摘要，[verify.py](verify.py) 复核失败响应与分类。

后续集中检查应先覆盖两个 Fabric 节点的相关回归和构建，再以修复源码进行同一原生序列，保留旧 Context/注册表/scheduler/回调断言及所有失败。原正式性能 PASS 对应 NeoForge 测量路径；此次 Fabric 专属接线不改该路径，不重复运行正式 cohort 来替代剩余实现。
