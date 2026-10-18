# PR #61 的 interface 函数类型独立语料

已补上提案 `c7e875713f43e37274d9ffb38c45c13ef62e5720` 的精确 `arrow-fn-type-in-interface` 输入：interface 同时包含方法签名和箭头函数类型属性。保留原 `module=false`、`lineInvariant=true`，通过现有擦除后 GraalJS parse 与行数检查，不执行仅用于 parse 的 interface 输入，也不加入 assumption/skip。

只增加一个已有框架内的用例，没有修改生产编译器、声明生成器或公开合同。修正此前文档计数：修改前实际为 58 个 CorpusCase，新增后为 59 个；该测试类另有行为检查，因此 JUnit 总数为 73。

- `gradlew.bat :common:test --tests com.tkisor.nekojs.core.compiler.TypeScriptErasureParseCorpusTest --console=plain`：PASS，73 tests，0 failures/errors/skipped。
- `gradlew.bat :common:check --console=plain`：PASS，2376 tests，0 failures/errors，4 skipped；包括 common 隔离检查。
- `python docs/architecture-refactor/evidence/2026-10-18-community61-interface/verify.py`：PASS，重算归档哈希、逐字比较提案输入及两个选项、核对全部 XML 和隔离任务。

`evidence.zip` 共 323 entries、331956 bytes，SHA-256 `7203889ff03599cfb7e21c5b72353a6f135f94969095bc86925636d125baacbc`。包含提案原文件、当前文件及 diff、首次定向结果和完整 common 结果；已知 RCON 凭据的文本匹配做了替换。该次 common 检查不是正式性能采样。

这解决 stonecutter 本地核对中的一个独立覆盖缺口。原 PR 仍以 master 为目标；没有刷新或更改 GitHub 状态、发送评论、关闭事项或创建 PR。其余合同差异、master 验证及维护者结论见[社区核对记录](../../../research/2026-10-17-community-closeout.md)。本轮没有重跑严格 TypeScript 声明消费。
