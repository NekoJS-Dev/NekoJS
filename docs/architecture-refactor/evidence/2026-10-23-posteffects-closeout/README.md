# 票 28 工程收口

本记录于 2026-10-23 核对票 28 自身条件，不把票 37 的完整 TSC、性能、最终统一候选或发布结论移入本域关闭条件，也不替维护者新增签字。

## 关闭依据

- 事件资源生命周期、candidate inert、失败保留、commit/retirement、运行 binding parity、双后端声明与 Fabric 明确 unavailable 均已有原票对应测试和证据；本批完整矩阵另保留相关 PostEffects 和通用候选释放 XML。
- 安装客户端已补齐：4405b0b0 的三节点统一记录包含 30 张世界目标原图、13 次原生 debug F3+T 调度、六次正常退出。legacy 独立安装另记录完整 16 阶段及原版链所有权；这是同一 legacy 范围的明细，不叠加为 46 张独立验收。两个原归档 verifier 本批重新执行通过。
- HEAD 88e18d84 与安装候选 4405b0b0 的本域 14 个生产文件逐字节相同，含 common candidate、modern/legacy owner/manager/binding 和 legacy 资源重载 mixin。source-audit 保留摘要、受限源码扫描及执行工具；旧静态符号无执行调用者，五处命中均为说明旧写法的注释。
- MIGRATION §2 已列替代路径、数据/行为影响和删除条件；PUBLIC-MIGRATIONS 保存已有 A28 精确追认原文。确认仅覆盖既定旧入口和 teardown 行为，不授权其他删除、性能豁免或发布。

AC9 的事件生命周期、运行 binding parity、迁移表、无旧消费者和精确确认五个条件均满足。原票以“正式候选安装客户端仍缺”为由保留的未勾选说明已过时，现收口该项及工程状态。

## 证据边界

这是本域工程关闭；没有宣称 88e18d84 的整批新 JAR 已再做本域原生客户端测试。组件源码相同不能替代票 37 对最终整批制品的必要验收。完整严格 TSC 与重载性能仍 FAIL，票 37 保持 in-progress。

AC4 的领域测试实际覆盖非法候选、收集错误、preflight 拒绝和同级域拒绝后的旧状态保留；通用 ScriptReloadGenerationTest 验证 CLIENT 候选失败、候选终止后 Context 真关闭/解除登记及 pending listener 清空。没有声称每一种中断/close 触发都重新做了 PostEffects 专属原生演练。结构上 candidate 只持声明，只有 publish 调用平台 apply，提交前没有 live renderer/resource 可泄漏。原生资源重载期间切世界的专属画面用例仍未另跑；既有脚本包 A→B→A 不替代此项，也不把它写成新增支持承诺。

现有客户端证据来自隔离 helper 开发启动器加载生产 JAR、原生 KeyboardHandler 路径和 render target，未认证正式安装器、物理键盘、屏幕 present 或真实维护者整体验收。Fabric unavailable 和 legacy resources-only/任意 inline id 限制保持。

只读 Luna xhigh 核对了票据、受限调用链和原证据；主代理独立复核来源、扫描、归档与测试结果后修改工程状态，代理未修改文件、运行测试或代签。

复核：`python docs/architecture-refactor/evidence/2026-10-23-posteffects-closeout/verify.py`。本包引用两个原归档并校验它们的 manifest 与 archive 摘要，保留原失败尝试，未改写历史结果。
