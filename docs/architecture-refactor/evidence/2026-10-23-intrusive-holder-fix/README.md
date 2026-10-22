# 失败注册的 intrusive holder 清理

生产提交 **`b47734c6`** 修复[真实 TCP 断线崩溃](../2026-10-23-dynamic-commit-red/README.md)的共享原因。`RegistrySurgery.withUnfrozenAndHolders` 为已有表保存 identity checkpoint；action 失败时仅移除本次新增且仍未绑定的 holder，保留原 pending，不重新加入已成功注册消费的 holder。null 表仍恢复 null，成功路径不清表，清理异常附加为 suppressed 并保留原失败。没有新增生产测试接口、改变 gate、wire、公开签名或持久数据。

真实 `MappedRegistry` 四项回归覆盖两次失败、相等但不同 identity、部分成功后失败及临时 null 表后的正常注册。旧实现初版 4 中 2 fail；补上真实 unfreeze→freeze 后 4 中 3 fail。首次跨节点命令让前几个任务跑完整 suite：两个 NeoForge 的四新例均通过；Fabric 的两项失败源于重复绑定 tags 的测试前提，末节点未运行。修正为仅建立既有 frozen guard、最后执行首次真实 freeze，并使 null-map 负例在 unfrozen 状态下检查。

最终每个任务显式附 `--tests com.tkisor.nekojs.dynamic.DynamicIntrusiveHolderRollbackTest`，四个现代节点各 4 例全部 PASS，共 **16 / 0 failure/error/skip**；同轮 guardLint **562 files / 381 blocks / 0 warnings**，30s PASS。全部历史失败与最终 XML/日志封存，没有删失败或缩减断言。

两轴 Luna xhigh 只读复审：Standards 0、Spec 0；测试修正经额外规格复核无发现。主代理独占所有实现、运行和提交；此项不是维护者验收。

此归档时四个现代节点完整 build 正在运行，**新制品 TCP 七阶段恢复尚未执行**，不能凭组件测试关闭票。完整 IDE 最近测量为前一 `1f877b0f` 的 5857 条诊断、继续失败；正式性能仍失败。后续新候选、实际重连/回退、维护者结论保持独立门禁。

复核：`python docs/architecture-refactor/evidence/2026-10-23-intrusive-holder-fix/verify.py`。
