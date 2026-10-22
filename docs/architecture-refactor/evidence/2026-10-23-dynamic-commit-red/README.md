# 真实 TCP：COMMIT 失败后的断线缺陷

**结果 NOT GREEN；保留缺陷，不关闭票。** 生产源码 `1f877b0f`、26.1.2 安装 JAR SHA-256 `c8285be43bafb59d179edae5d670c0b933951c196eaf1d0558acda434c467d9b`。主代理独占实现与运行；Luna xhigh 仅只读调查/审查。

第三次夹具运行完成前四阶段：两名独立 TCP 客户端先正常提交；A 已 ACK 的新批次在真实 `MappedRegistry.register` 入口失败，服务器和 B 完成注册；服务器记录 `activation-failed-post-commit` 后追赶，A 再次真实注册失败，服务器记录 `state-sync-rejected`。A 仍保留旧 `tcp_seed`，缺失新条目，服务器保持 `COMMITTED`。helper Mixin 仅匹配 A 的 owner、ITEM 注册表及精确测试键，没有生产测试接口、直接 ACK/handler 调用或私有 participant 反射。

随后真实 kick 触发 A 断线，`RegistryManager.revertToFrozen` → `applySnapshot` → `MappedRegistry.freeze` 因两个未注册 intrusive holder 崩溃。当前 `RegistrySurgery.withUnfrozenAndHolders` 对已有非 null 表仅恢复 frozen 标记，未清除失败构造/注册新增的 pending holder。这是实际回滚缺陷；不能移动故障注入点以掩盖。A 退出 4294967295，B 和服务器正常退出 0，无强制清理。重连拒绝/移除故障后恢复/后续双 ACK 三阶段尚未完成。

历史尝试分别保留：v1 Mixin 包覆盖 helper 主类导致服务器启动 exit1；v2 Windows 短读占用使观察器原子替换文件失败、B 崩溃，A/服务器正常退出。v3 已改专用 Mixin 子包，并在后续 tick 有限重试发布，不阻塞 owner；三次失败都没有改判通过。v1 首次驱动在冻结前被覆盖，未声称保存其原字节，helper 源码/日志/结果仍在。

来源材料包括三方 ModList 文件路径、四个已加载类的 CodeSource/resource/摘要、PID 和实际 JAR 哈希；另存的 provenance 对 1099 个跟踪生产源码/构建文件核对指定提交字节，并逐项匹配 1640 个编译类与 JAR，且 JAR 哈希等于此前普通构建封存记录。日志按隔离 RCON 密码字节脱敏；不导出 properties、完整启动参数或原始 JFR。

主代理将于共享 holder 生命周期边界修复，先补真实 `MappedRegistry` 失败回归，再按原七阶段复验 26.1.2 / 26.2.0。两轴审查指出的 STATE_SYNC 后物理状态、来源追溯缺口已补检查；这是技术审查，不是维护者人工验收。性能、完整严格 TSC、最终统一候选/回退仍开放。

复核：`python docs/architecture-refactor/evidence/2026-10-23-dynamic-commit-red/verify.py`；复核通过只表示失败材料完整且断言相符。
