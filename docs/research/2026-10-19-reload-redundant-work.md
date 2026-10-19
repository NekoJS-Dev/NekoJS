# 重载剩余重复工作的实现

2026-10-19。继 `4dcfe604` 正式重载 285.8 / 286.6 ms 均未达到 285.3 ms 门槛后，继续排查实际重复工作。遵循用户先完成实现、再集中验证的要求，本阶段由主代理修改源码与回归；Luna xhigh 仅限文件范围只读调查。

## 已落实的改动

`GlobalBindingMemberValidator.checkMemberAccess` 仍检查所有可能对象类型是否存在该成员。只有所有类型都不含成员、需要生成诊断时，才收集它们的方法、属性与字段名字用于拼写建议；收集顺序不变。删除了原先每次合法访问也执行的候选集合构造，以及从属性返回类型生成但从未读取的 `next` 列表。后续链式类型推导仍由原来的 `resolveType` 完成，没有删掉两遍局部类型传播、当前 schema 检查、诊断或解析。

新增联合类型回归：两个候选类各自提供的方法和 getter 属性都合法；分别拼错两个类的方法仍应得到两条诊断及各自建议。它验证联合类型行为，没有断言内部集合或调用次数。已有链式、别名、当前 schema 与组合预检回归继续适用。

共享 26.x 与 1.21.1 的 `RecipeManagerMixin` 在本轮最终配方解码循环中复用一个局部 `RegistryOps<JsonElement>`。它在修改 hook 之后、第一条配方的既有 `try` 内创建；空批次不创建，创建失败仍由该条配方的异常处理接住，下一条再次尝试。每条配方仍执行完整 `Recipe.CODEC.parse`，条件筛选、JSON 深复制、ingredient actions、前后事件、批量提交和客户端后续保持原流程。

该对象不进入字段、root、generation 或跨轮缓存。1.21.1 条件筛选阶段早先创建的 `registryOps` 继续独立，不把筛选阶段的查询结果带过修改 hook。实际减少的是重复创建上下文与其查询缓存；没有复用已解码的 Recipe，也没有跳过当前 registry/codec 校验。

本机 26.1.2/26.2 的原版字节码显示，默认 Provider 每次调用会新建 RegistryOps、HolderLookupAdapter 及 ConcurrentHashMap；adapter 会缓存 registry key 的存在与缺失查询。这个实现依赖同一最终解码批次使用稳定的 registry provider，与原版批量解码的用法一致。动态改变 provider 查询结果、或观察 DynamicOps 对象身份的外部自定义 codec 未经实测；不能把本地检查扩大为所有第三方 codec 的等价证明。

## 没有引入的改动

- 两个预检入口的 `schemaForPath` 只是返回 View 已有的不可变 map，没有重复构建 schema；不为两个简单访问器增加共享协议或缓存。
- FileStamp 的两个文件属性查询可以合并，但五个顶层正式脚本、30 对交替组件观测只节省约 0.0126 ms / 五文件。不将这种微小结果当作剩余正式差距的解释，本阶段没有改文件指纹代码。
- 没有改变信任批准、路径复验、独立 Context、候选失败保留、正式 sampler、首值、门槛、系统策略或五个既有社区事项的外部状态。

## 验证边界

本次实现及新回归尚未运行测试、构建、游戏或正式采样；仅人工复查受影响调用与双节点实现、执行 `git diff --check`。此前五节点检查与正式失败结果绑定旧源码，不能代替新源码的验证。上述两个改动的实际总耗时收益尚未测量，性能目标仍未完成。

随后固定在 `46505ad2` 集中检查：定向 89 项全部执行并通过，完整 common isolation/check、处理器、五节点 build/平台门禁和四项 CI 一致性 gate 全部通过；全量 5,021 次执行、0 failures/errors、271 skipped。原始证据与独立复核见[集中检查](../architecture-refactor/evidence/2026-10-19-redundant-work-checks/README.md)。这仍不是实际 codec 或正式性能通过。

其后按原 sampler 完成[正式复验](../architecture-refactor/evidence/2026-10-19-redundant-work-performance/README.md)：重载 335.6 / 318.2 ms，均 FAIL；启动 36738.6 ms，PASS。17 行有效、九会话正常停服。未单独量化两项源码修改的收益，不能把不同会话分数变化归为单一源码因果；没有为择优成绩重复正式组。

后续集中验证先覆盖成员与组合预检、配方 JSON 隔离/生命周期，完成 common isolation/check 和受影响五节点检查，再将最终源码绑定到原两组重载及启动复验。实际 Mixin/codec 及 Fabric 客户端资源验收仍需游戏证据；没有合适的普通 JUnit 接缝来证明注入后的完整配方循环，本阶段未增加只匹配新实现文字的测试。
