# 预检共享源码解析

2026-10-19。用户要求先完成实现，再集中测试。本阶段不启动游戏、构建、单测或性能采样；Luna xhigh 只读核对接缝，代码与回归由主代理编写。

## 问题与实现

此前完整游戏 JFR 在当前 schema 预检中观察到样本，但不足以将所有耗时归给解析器。当前调用链有可直接确认的重复工作：`NekoModulePipeline.prepareStages` 在选中的语言分支内，将同一源码分别交给绑定检查和回调检查；两个 validator 各调用一次 `ValParser.parse`。

新增内部编译层入口 `ScriptSourcePreflight.validate`，在一次调用内创建惰性的 `ParsedSource`。两个 validator 首次需要 AST 时才解析，同一次调用后续读取同一 AST；解析失败也保留本次失败结果，让两者继续各自记录原有的专属警告。没有增加跨代缓存、配置、依赖或新的 runtime owner。

- 每个 validator 保留空输入、空 schema 门控及各自完整遍历；绑定检查仍先于回调检查。
- 每次仍读取当前 `View`，构建局部类型、去重集合和诊断，使用当前路径和源码计算行列，并通过当前代 reporter 上报。
- JS 族仍预检原始 JS；转译语言仍预检编译产物，编译器 revision 和前后信任门保持原位置。
- 两个公开的单独 `validate` 入口继续可用，每次单独调用都拥有自己的解析会话。入口脚本额外的绑定预检保持，不将它误当成本次可删除的冗余。
- AST 中的列表和 scope map 可变，两个 validator 只读借用；解析会话不进入模块缓存、不在线程间共享，结束后即可释放，不持有 schema、guest 或跨代资源。

这在源码上减少了每次成对预检的一次解析，没有跳过任一校验。实际毫秒收益、分配收益和正式总耗时尚未测量，不能据此宣布原 285.3 ms 重载门槛已通过。

## 回归与检查边界

新增 `ScriptSourcePreflightTest`，比较独立两次调用和合并入口的完整诊断（类型、回调标识、含路径/行列的消息），覆盖绑定及回调同时出错、CRLF/Unicode、别名、函数回调和嵌套调用；另外检查相同源码更换当前 schema、同路径改源码、不同文件的诊断归属，以及空输入/空 schema。

实现阶段只编写回归并人工复查 diff，未立即执行；随后在 `4dcfe604` 集中验证，定向 common 61 项、两个 Fabric 节点各 35 项全部通过。完整 common isolation/check、处理器测试、guardLint、五节点 build 和四项 CI 一致性 gate 均通过；[冻结记录](../architecture-refactor/evidence/2026-10-19-implementation-checks/README.md)保留全部日志与 XML，全量 5,020 次执行、0 failures/errors、271 skipped。这轮功能检查不单独量化解析收益，不据测试通过推断提速或原门槛通过。

随后按原采样器及原门槛完成[正式复验](../architecture-refactor/evidence/2026-10-19-preflight-performance/README.md)：重载 285.8 / 286.6 ms，均 FAIL；启动 24250.4 ms，PASS。全部 17 行有效、九会话正常停服；没有同会话旧/新源码控制，不能从跨会话差值单独量化本改动的毫秒收益。没有改变任何完成标准，整体性能目标仍未完成。
