# 装箱字符 TS 投影修复与实际验证

本批修复 `Character` 在 TS 的返回值、数组、泛型内容和回调位置仍渲染为宿主类、而直接输入已经是 `string` 的不一致。基线 `09fcf574`，十个默认严格 TSC 入口从 **7071 减为 6867**，按文件、代码、首行消息的诊断多重集比较移除 **204** 条、**没有新增**。完整 IDE 门禁仍失败，票 37 继续开放。

## 改动与契约

- 仅在 TS 的共享符号渲染处将 `java.lang.Character` 投影为 `string`，与实际 Graal 字符值读取对应；已有输入适配器仍先处理。不是把所有泛型参数都按输入别名转换。
- 保留共享 IR 的 `Character` 符号与源类型，Python 渲染不变；显式槽编辑仍生效。没有修改运行时转换、Java 方法、公开成员、继承、扫描配置、数据或 wire contract，也没有新增依赖。
- 新增三个回归，覆盖真实反射与正式生成、严格 TS 正负例、生产 HostAccess 的具体参数/返回值/数组/泛型读取与回调。UUID、Path、File 的现有主机身份回归通过。
- canonical legacy-tree 经 `:common:regenerateGoldens` 更新，仅 `java/lang`、`java/util` 两个文件：Character 的 Comparable/Class/Optional 实参及 Locale 字符集合改用 `string`，移除不再需要的 Character import。保留 Character 类本身与所有成员；不是手工修补 golden。

## 字符值与擦除泛型的限制

`string` 表达字符值，不表达“所有字符串都能转换为 Java Character”。单个 UTF-16 代码单元约束仍在运行时执行。擦除后的 `Object` 参数/字段也不保证把字符串装箱为 Character；泛型容器写入后由 Java 强转读取、泛型宿主 lambda 调用的拒绝行为，已由生产 HostAccess 回归明确记录。带具体 Character 参数或完整泛型信息的回调需按实际入口验证。本批没有扩展这些运行时能力。

首次实机夹具假定 Guava `CharMatcher.test('a')` 在五节点都可用，1.21.1 因旧库擦除入口的 String → Character 强转失败，导致该轮 SERVER generation 未提交。该轮按失败保留，未当成五节点通过。使用 `09fcf574` 旧 JAR、相同原始夹具和新世界对照，也复现相同失败并正常停止；旧制品 SHA256、归档源文件与提交的对应关系已核验。

最终夹具保留该能力边界：五节点均验证 `Character.valueOf`、`CharMatcher.matches` / `apply`、fastutil 字符 Map 的具体与泛型返回值；四个 26.x 节点验证 `test` 接受，1.21.1 则精确验证原有 ClassCastException 拒绝。所有节点在初次加载和重载后均有值检查及各自能力标记。该负例只确认已有拒绝行为，不宣称 1.21.1 的此入口已修复。

## 验证结果

主代理完成全部修改、构建、验证和提交；Luna xhigh 仅只读调查。候选由源文件 SHA256、生产补丁、实际 JAR SHA256 与全部 common/node classBindings 定位；preparation manifest 中的基线 HEAD 不冒充提交后的候选。

通过：

- `./gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`；补充擦除泛型回归后再次运行 `:common:check`。最终 XML 共 **4952 tests，271 skipped，0 failures / errors**。
- `npm run test:probe-types`，含新字符严格正负例；没有启用 skipLibCheck。
- 新官方 JAR 的五节点实际注册、领域断言、Probe、重载与字符能力检查；全部通过并正常停止。旧 JAR 的 1.21.1 对照也正常停止，结果为预期旧限制复现。
- 五节点全部默认 Python stub 的 Pyright：0 errors / warnings。Python Java 类 stub 逐字节一致；入口和事件文件每节点仍有 4–5 个顺序/本地导入别名差异，完整前后文件与哈希已封存，不能据此宣称全部输出字节确定性。
- `python tools/nekojs-ci-gates.py all`：4 checks / 0 failures。wiki `check --structure-only`：0 problems；本批 `git diff --check` 通过。
- `python docs/architecture-refactor/evidence/2026-10-17-tsc-closeout/verify-character.py`：证据完整性、XML、五节点运行、字符正负能力、旧制品对照和诊断结论校验通过。

| 节点 | 默认 TSC server | 默认 TSC startup | 本批移除合计 |
|---|---:|---:|---:|
| 1.21.1 | 625 | 626 | 36 |
| 26.1.2 | 687 | 688 | 42 |
| 26.2.0 | 676 | 677 | 42 |
| 26.1.2-fabric | 734 | 735 | 42 |
| 26.2.0-fabric | 709 | 710 | 42 |

仍失败或未运行：

- 完整默认 TSC 仍有 6867 条；没有把定向通过或收集诊断的工具退出成功当成完整 TSC 通过。剩余包括 Bean/成员冲突、静态与实例继承、泛型作用域、手写声明及依赖不可用等类别。
- wiki `check --strict` 仍有 plugin-development、type-adapters、event-extensions 的 6 项既有摘要/翻译审阅问题；本批只更新已修改主题的摘要，没有代签人审。
- 未重做正式性能、客户端矩阵、最终候选 release/data rollback 或正式发布；旧性能失败和票 28/37 的其他门禁继续保留。

本批 commit 到 stonecutter，不创建 PR、GitHub issue，不 push 或发布。

## 证据

[调查与工作记录](CHARACTER-WORKPLAN.md)、[机器摘要](character-summary.json)、[清单与 SHA256](character-manifest.json)、[原始证据包](character-evidence.zip)。包内保留最初失败、旧制品对照、最终实机、严格诊断、Python 前后文件、单元 XML、制品绑定、夹具和复核工具；复核不会把完整 IDE 失败改判为通过。
