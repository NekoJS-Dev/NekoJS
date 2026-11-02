# GLM 混合引号严格消费与文档校正复核

日期：2026-10-25。固定审查 `ff6e0558...c3ee4097`，对应 `232065c8`、`c3ee4097`。两名 GPT-6-luna xhigh 子代理分别只读复核 Standards 与 Spec，主代理检查 diff 并独立运行 TypeScript 验证。未修改产品代码、提交、推送或创建 PR。

## Standards

0 项已确认问题。新增 fixture 提供合法的任意字符串导出 `"a'b"`；生成器真实输出三份包声明，经 `custom` 模块绑定 getter，私有导入仍避让到后缀名。配置启用 strict、关闭 skipLibCheck，包含生成声明、custom 模块与消费文件；npm 完整定向链已接入新检查。

正例验证 custom 类型可用，负例拒绝 number；没有用 any 或其他降级绕过问题。产品 `TypeScriptImportNames.java` 与 `d38c14dd` 无差异。

## Spec

0 项已确认缺失或误实现。上轮要求的严格消费与四处文档校正均已落实：当前票状态反映 d06cd961 性能 PASS，性能文档区分 Gradle 开发运行和安装 JAR，超时字段使用准确措辞，历史波动未被归因为环境唯一原因。原门槛、样本和历史 FAIL 记录保持，`timeoutsMarked` 改名未找到受影响的仓库消费者。

## 独立验证

- PASS：`npm.cmd run test:probe-mixed-quote-import`，退出 0。
- PASS：`npm.cmd run test:probe-types` 完整定向链，含新增尾项，退出 0。
- PASS（预期失败控制）：将生成目录复制到系统临时目录，仅移除 `@ts-expect-error` 后运行 tsc，退出 2，实际出现 TS2322。
- PASS（预期失败控制）：在临时副本将 `$NekoImport$PersistentData1` 改为冲突绑定，tsc 退出 2，实际出现两条 TS2300。原仓库生成文件没有修改，临时副本已自动清理。
- PASS：`git diff --check ff6e0558...c3ee4097`；扫描器与 `d38c14dd` 无差异。
- 保留的 JUnit XML 为 9 项、零失败/错误，包含混合引号测试；本次没有重新运行 Java 测试或 common/check，也没有重建安装产物。

未找到 GLM 临时回退旧扫描器时保存的 red 编译日志，因此该历史操作及其 Gradle UP-TO-DATE 排查仍属于交付陈述；本次临时副本的冲突控制独立证明当前严格消费检查确实能检出对应重复标识符，不冒称重新演练了历史回退过程。

## 下一步

可以继续 §3：保存可复用的隔离安装/声明生成/检查驱动，以当前确切产物重新采集五节点十入口完整严格 TS 基线；随后提交根因分类表，先对 Constable → Optional 通配符/SAM 一族做有真实来源签名的最小正负回归与共享修复。旧 1609 条和其中 140 条同族诊断仍仅代表 v3，不是当前 HEAD 的重采结果。

当前定向检查通过不代表默认完整声明通过，也不关闭票 37。最终候选冻结及客户端/TCP/分离回退/包装与维护者结论保持开放。后续具体范围沿用[阶段任务书](2026-10-25-glm-stage2-review-and-ts-handoff.md)。

审查汇总：Standards 0 项；Spec 0 项；没有阻止继续 §3 的已确认问题。
