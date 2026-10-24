# java:/node: 共享虚拟模块名在 Windows 被当作磁盘路径的缺陷修复

选择器安装跟进轮（`build/closeout-selector-backend-installed-v2`，2026-10-24 22:47）把验收脚本改为[现有文档](../../../../README.md)写法的 `java:` 类导入后，五个节点全部 NOT GREEN：三个 NeoForge 节点（1.21.1、26.1.2、26.2.0）在 `nekojs reload server` 的 EXECUTION 阶段失败，两个 Fabric 节点在就绪前退出。失败摘录见[installed-v2-failure-excerpt.md](installed-v2-failure-excerpt.md)；该轮记录保留为失败证据，不追改。

## 症状与根因

- 稳定报错：`java.nio.file.InvalidPathException: Illegal char <:> at index 4: java:net/minecraft/world/phys[$Vec3]`，候选代被丢弃、活动代不变（Fabric 节点 server 脚本在启动期加载，崩溃更早，`reload failed` 形态只出现在 NeoForge 首次 reload）。
- 崩溃点：`NekoEsmVirtualModuleRegistry.scriptTypeOf` 在剥离 `#` 后缀后直接把 moduleId 交给文件系统解析（`root.getFileSystem().getPath`）。Windows 上多字母 `xxx:` 前缀不是合法盘符，`getPath` 抛 `InvalidPathException`；Linux 上同一路径恰好可解析并最终返回 null，因此这是 Windows 特有崩溃，此前隔离安装都在本机暴露。
- 触发路径为共享注册入口：`NekoNativeEsmSourceRewriter.syntheticObjectModule → virtualModules.register`（命名 `java:` 导入，如 `import { $Integer } from 'java:java/lang'`）。`scriptTypeOf` 的 javadoc 与 `types` 映射注释早已声明 `node:`/`java:`/裸包名应返回 null，但代码未实现；`displayPathForModuleId` 中同前缀判断原本只作用于显示路径。

## 修复与回归

`scriptTypeOf` 在进入文件系统所有权查找前先识别四种共享前缀（`java:`、`java.`、`java/`、`node:`）并返回 null；判断提取为 `isSharedVirtualModule`，`displayPathForModuleId` 复用同一助手。共享 id 因此不进入 `types` 映射：`clear(ScriptType)` 不再可能误删共享模块，`clear()`/`invalidate()` 仍可删除，与既有注释合同一致。

- `NativeJavaPackageImportTest`（新增）：在 @TempDir 完整流水线中执行 `import { $Integer } from 'java:java/lang'` 并断言 `$Integer.parseInt('42') === 42`，端到端覆盖触发路径。
- `ScriptTypeScopedCacheClearTest.sharedJavaAndNodeModuleNamesNeverBecomeScriptPaths`（新增）：覆盖四种前缀的 register/reserve、`#namespace-capture` 后缀变体，以及类型清理边界（SERVER 清理后共享模块存活、invalidate 可删、重注册得到新代 URI）。

## 验证

- 修复前 red：`:common:test` 2 failed，均为 `InvalidPathException`（[selector-java-import-red.log](selector-java-import-red.log)）。
- 修复后 green：同命令 BUILD SUCCESSFUL（[selector-java-import-green.log](selector-java-import-green.log)）。
- 完整隔离检查：`gradlew.bat :common:check` BUILD SUCCESSFUL in 3m 27s（[selector-java-import-common-check.log](selector-java-import-common-check.log)）。

## 影响与后续

v2 安装所用 JAR（源至 e13cbe82）含此缺陷，其 NOT GREEN 结论维持原样。本修复提交后以新源码重跑五节点隔离安装（v3，新目录/新端口），`java:` 导入验收应转为通过；结果与修正后的十组严格 TS 计数记录于[安装差分跟进](../../../research/2026-10-24-selector-installed-followup.md)。无 PR、推送或发布。
