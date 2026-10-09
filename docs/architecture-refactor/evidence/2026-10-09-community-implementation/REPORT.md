# 五项社区提案的 stonecutter 实现

基线：`3783fadb4b1701971c3d443c0c13c43ee1c46aa5`。按当前架构移植提案能力，未直接合并目标为 master 的 PR。

## 修改与行为

- [#61](https://github.com/NekoJS-Dev/NekoJS/pull/61)：扩充有效 TS 语料，移除 KNOWN_GAPS 跳过。修复函数／对象类型擦除边界、泛型 class 字段、type re-export、方法重载、this 参数和嵌套 namespace；补实际行为、模块解析与行数回归。校正非法的 for-of 类型标注、混合枚举及索引签名输入。运行时箭头函数和断言后的索引访问不会被类型擦除吞掉。
- [#60](https://github.com/NekoJS-Dev/NekoJS/issues/60)／[#65](https://github.com/NekoJS-Dev/NekoJS/pull/65)：复用共享 SAM 解析，处理 Object 方法、协变返回和继承泛型。Probe 输入别名接受函数与原 Java 接口实例；回调参数使用宿主类型，返回值使用输入契约。保留调用者泛型推导，SAM 自身方法变量才降级为 any；支持有限嵌套，递归泛型增长有界回退。泛型父类／接口实参同时进入共享收集与 IR。
- [#63](https://github.com/NekoJS-Dev/NekoJS/issues/63)／[#66](https://github.com/NekoJS-Dev/NekoJS/pull/66)：新增 `ClassDeclarationCatalogEntry` 和 `TypeDocsRegister.registerClassDeclaration`，沿 type_docs／node_type_docs Point 收集、合并并冻结。替换全局生效；最高优先级获选，冲突同优先级拒绝，相同定义幂等。显式 Java 依赖进入收集；被排除、隐藏或未能生成的必需依赖使生成失败。隐藏 target 优先，其他反射成员编辑由声明体覆盖。保留旧 snapshot 构造器与 runtime 默认空列表。

注册声明必须保留标准 `$JavaName` 导出（嵌套类为 `$Outer$Inner`），不要重复定义生成器拥有的输入别名。枚举替换的辅助别名使用原运行时名称，不受被替换 IR 的重命名影响。新增 NEKO-4031／4032／4033 及中英文插件文档。

实际 Probe 输出由 `CommunityProbeTypeScriptFixtureTest` 写入 build 目录，然后通过严格 tsc 检查：宿主参数、适配器返回值、继承／raw／嵌套回调、调用者泛型推导，以及 RPC 的 schema → returns → fn 正确推导与错误类型拒绝。Graal 测试另验证函数、Java 实例、嵌套回调和返回 Java Iterator 的回调。

`Iterable` 是具体例外：Graal 对它使用迭代器协议，箭头函数调用实测被拒绝。因此它保留既有数组别名，不生成 callable 输入。JS 数组转 Iterable 实测可用；新 SAM 返回 Iterator 时保留宿主 Iterator 类型，避免旧输入映射把它错误投射为元素值。

## Golden 审查

通过 `:common:regenerateGoldens` 再生成，未手工修改派生产物。14 个 legacy package 声明文件有内容变化：

- function／util／stream：通用 callable＋host 别名、方法参数接入这些别名，以及泛型继承实参；保留调用者 T／R／V 等变量。Iterable、集合和枚举的既有别名继续存在。
- lang／constant／invoke／module／net／nio／charset／io：泛型 heritage、相关 SAM 别名与跨包导入。
- reflect／security／测试包等：移除已映射为 TS 标量的 String／数字类型和不再被发射的 Enum heritage 的无用导入。

未增加 legacy tree 文件，也未改 managed API manifest。`.d.ts` 明确使用 LF，避免生成资源仅因换行转换出现假修改。

## 验证

- `./gradlew.bat :common:regenerateGoldens --console=plain`：通过，10 秒；[再生成日志](regenerate-goldens.log)。
- `./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:compileJava :26.1.2:compileJava :26.2.0:compileJava :26.1.2-fabric:compileJava :26.2.0-fabric:compileJava --console=plain`：通过，1 分 35 秒，44 项任务；包含 common 隔离检查、addon fixture surface 和 guardLint（384 blocks、548 files、0 warnings）。common 共 2149 项测试，0 失败、4 项既有 assumption 跳过；processor 共 13 项，0 失败。[最终检查日志](final-check.log)。
- 五节点 compileJava 均通过。最终合并检查的 Fabric 编译任务为 UP-TO-DATE；此前独立五节点编译通过，33 项任务中 5 项执行。[节点编译日志](node-compile.log)。
- `npm.cmd run test:probe-types`：通过，原 managed fixture 与实际 Probe 输出的新增 fixture 均由严格 tsc 检查，未启用 skipLibCheck。[TypeScript 日志](probe-types.log)。
- `git diff --cached --check`：通过；最终 diff 已按范围、语言、公共契约和派生产物审查。

4 项跳过来自 `LocalErrorSourceTest` 和 `TypeScriptNoopIrGoldenTest`，各 2 项；新增回归与语料没有跳过。

首次真实 RED 为 131 项中 26 项失败（[日志](regressions-red.log)）；中途完整检查捕获 buffer.ts 断言括号边界导致的启动连带失败，修复共享原因后复测通过。新增语料没有以跳过代替修复。

## 剩余范围

未运行五节点完整 build、GameTest 或真实游戏客户端；未集成外部 RPC 插件。RPC 验证使用真实生成器和匹配的 Java fixture，声明替换不会新增 Java RPC 运行时行为。维护者尚未作出人工验收结论，不能把自动化通过视为维护者接受。
