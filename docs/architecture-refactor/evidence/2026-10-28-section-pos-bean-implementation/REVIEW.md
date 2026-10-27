# SectionPos精确迁移复核

本轮主代理实现并自行复核，未把历史子代理意见当成本轮审查，也不代表维护者接受。

| 维度 | 核对与结果 |
|---|---|
| Standards | common无MC/loader引用；真实类型登记位于版本树，沿用TypeDocs与NodeTypeDocs的产品收集、密封、不可变快照和runtime归属。新规则为公开catalog条目，旧snapshot构造签名及IPluginRuntime默认实现保留。新异常英文且有NEKO-4053双语说明，无新依赖、配置、线程或运行时owner。 |
| Spec | 仅登记SectionPos x/getX、y/getY、z/getZ，目标是省略五节点15处TS零参方法；保留数字Bean、getter和静态重载。规则依赖已编辑可见祖先链，不在渲染期间反射补回隐藏来源，不扩张至其他类或Shelf.level。共享IR及Python回归保持。 |
| 发现及修复 | 显式合成getter/字段最初未阻断祖先匹配，两个新反例均真实失败；补保护后通过。旧节点贡献测试只接受Entity规则，完整矩阵因此失败；更新接收方后仍精确断言唯一Entity规则及三条坐标规则，未放宽“不能多贡献”的门禁。 |
| 验证 | 最终common/check、处理器、guard与五节点build通过，929 suites /5226 tests /271 skipped /零失败错误；完整npm严格消费链、四项CI gate及五节点source-roots检查通过。无golden变化。真实五节点Graal回归保留Bean读取、普通直接调用、getter及选择器实际失败行为。安装及诊断复验以本目录最终README和冻结记录为准。 |

当前改动增加精确TypeDocs贡献能力，并有意迁移获批TS调用写法；不改变Java方法、Python、JS运行时、权限、网络或持久数据。源码及编译类与安装JAR的关联单独记录，不替代最终候选性能、客户端/TCP、回退、包装、内存预算与真实维护者结论。
