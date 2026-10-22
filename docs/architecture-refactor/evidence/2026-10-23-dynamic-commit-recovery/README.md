# COMMIT 失败、断线与同进程恢复

生产源码 `b47734c6721ee8434df48c0b2a86b887d577f02e`。两个 NeoForge 节点各七阶段通过，六个实际进程正常退出 0，无强制清理。故障仍注入真实物品注册入口；没有移开此前导致断线崩溃的故障点。

每节点依次验证：双客户端空状态、正常双 ACK 提交、COMMIT 后 A 物理激活失败而服务器/B 已提交、在线 STATE_SYNC 失败时 A 保留旧条目、故障仍在时重连于进入 play 前拒绝、移除 helper 故障后同一客户端进程补同步恢复、随后双客户端再次提交。最后三个条目的实际 stack=16、rarity=RARE；原生 owner thread、真实协议结果和物理注册表均有独立观察。

服务器保持 COMMITTED；在线失败的 A 仍连接，直到夹具显式 kick。本材料不承诺全局 DEGRADED 或服务器一律拒绝后续批次。共享修复仅清理失败操作新增的未绑定 intrusive holder，见[先红后绿回归](../2026-10-23-intrusive-holder-fix/README.md)。[旧失败材料](../2026-10-23-dynamic-commit-red/README.md)继续保留三个失败尝试，未改成成功。

运行后独立 binder 核对 1099 个生产/构建源码和每节点 1640 个编译 class；实际安装 JAR 对应[四现代节点完整构建](../2026-10-23-holder-modern-builds/README.md)。每进程另保存五个实际加载类的 PID、CodeSource、resource 和 SHA256，包括修复类 RegistrySurgery。原始七阶段结果、日志、命令记录、精确运行 driver、helper 源码/JAR、来源绑定与独立验证工具在 evidence.zip，manifest 逐项校验。没有导出服务器配置、密码或启动参数文件。

26.1.2 两端 loader 为 26.1.2.71；26.2.0 使用现有缓存的客户端 26.2.0.57、服务端 26.2.0.75。运行使用独立 ModDev helper 启动器与安装的生产 JAR，不称为外部正式安装器验收，也不扩展为所有平台覆盖。

复核：`python docs/architecture-refactor/evidence/2026-10-23-dynamic-commit-recovery/verify.py`。离线 verifier 检查封存字节、真实阶段、退出与加载证明；安装 JAR 本身的逐项字节核对已在封存前执行，其工具与结果同存。它不代签维护者结论。完整严格 TSC、正式重载性能和最终统一候选的客户端/分离回退仍各自开放。
