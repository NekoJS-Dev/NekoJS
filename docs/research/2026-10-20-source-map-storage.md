# 源码映射段存储收口

## 依据与范围

`46505ad2` 的[当前命令阶段诊断](../architecture-refactor/evidence/2026-10-20-current-reload-stages/README.md)中，root 切代平均 102.43654 ms；分配采样的 `SourceMapRegistry$MappingEntry` 权重约 12.79 MB / 20 次重载。这是采样权重，不是精确分配量或该类的独占耗时。路径调用链的只读复查没有找到能保留 trust/path 复验的安全高收益删减；成功 AST 复用也已因收益太小而否决。

本次由主代理修改 `SourceMapRegistry` 的私有映射表示：每行保存一个 `int[]`，每个映射段按 generated column、source index、original line、original column、name index 顺序存五个整数。解析期间复用可扩容工作数组，在分号和结束处复制本行有效范围；各完成行没有共享可变工作缓冲区。

不增加缓存、跨代资源或公共类型。仍按每次注册提供的 JSON 解码，路径规范化、来源内容、映射 revision、别名、清理和容量限制沿用原机制。VLQ 解码、非法或不完整字段的容忍规则、累加器的 Java int 行为、空行/尾分号均保持原代码逻辑；查询仍按输入顺序扫描并以首项作为早于首列时的默认值，未改为排序或二分查找。

新增非单调列及重复列回归，要求先于首列仍使用首段、按原顺序扫描到的重复命中取最后一段；补充非零额外字段不改变第五字段名字 delta、超长 VLQ 保持现有 int 回绕与顺序查询的回归。已有密集映射/空行、多源/名字 delta、无效段及 runtime 隔离回归继续适用。Luna 只读核对了表示变更与旧 parser 的状态迁移，源码修改与回归均由主代理完成。

## 验证边界

实现提交时遵循先完成实现的要求，源码和回归写好后仅人工检查完整 diff、调用方与私有类型引用，并执行 `git diff --check`；当时未运行新源码的编译、测试或性能采样。JFR 诊断与源码/工件还原证据绑定旧生产提交 `46505ad2`，不能当作本次实现的验证。

后续集中验证需要覆盖：SourceMapRegistry 及映射错误归属回归、`common:check` 的隔离检查、固定语料的旧/新可观测映射结果对照和分配/耗时测量；有明确收益且行为一致后，再绑定最终源码按原正式门槛复验。旧私有 decoder 对照器通过 `toString()` 比较私有记录；内部表示改变后须改为对照实际映射字段或按同一五字段规范展开两侧，不能用不同容器的 `toString()` 判断行为等价。

实现提交时尚未证明总重载收益，参考的上轮正式两组重载为 335.6 / 318.2 ms，均 FAIL，启动 PASS。脚本包和 Fabric 实机资源验收、社区外部收尾也不由该内部表示变更自动完成。

随后在 `24d35a5d` 完成[集中检查与组件对照](../architecture-refactor/evidence/2026-10-20-source-map-storage-checks/README.md)：定向 113 项、全量 5,024 次执行，零 failures/errors、271 skipped；common isolation/check、处理器、五节点 build、guardLint 和四项 CI gate 均通过。旧/新 10,022 份输入的解码字段及 491,078 个公开位置查询一致。六源码组件分配约下降 49.4%，墙钟仅节省约 0.063 ms，不据此承诺完整重载达到原门槛。

该提交随后完成[原标准正式复验](../architecture-refactor/evidence/2026-10-20-source-map-storage-performance/README.md)：两组重载 **289.4 / 338.0 ms，均 FAIL**，启动 **36661.0 ms，PASS**。17 行全部有效、九会话正常停服；原首值、门槛、采样器和默认系统策略保持。完整重载目标仍未完成，不按分数重跑未改动源码。
