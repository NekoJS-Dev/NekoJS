# 注册表适配器与目录一致性

生产源码 `88e18d84bed2f501416b1ef44e7b87d32fa8ef38`。NekoScriptCatalog 一次获取同一 Plugin Runtime 已发布的适配器，临时/最终快照共用同一目录。平台保留旧目录入口及其子类覆盖提供的显式 metadata，追加实际 SimpleRegistryBasedAdapter 缺少的别名；快照读取当前条目和标签，无额外反射扫描、Point 或全局缓存。TypeScript 与 Python 共用元数据。

同名同一 Registry 对象去重；已知物理来源同名不同 Registry、冲突 metadata、生成 Tag/Namespace 符号冲突明确以 NEKO-4052 失败。旧显式 metadata 不携带物理来源身份，其提供者仍负责与适配器匹配；不假装验证未知来源。BlockState 使用 Block 目录，并保留字符串属性语法和原解析行为。运行时权限、wire、数据格式、公开成员与主扫描范围均未改动。

## 回归与复审

真实旧路径：common 三项全失败；严格 TS 缺少 FixtureEntry/Tag 共两条 TS2694，非法值负例再出现 TS2578；BlockState 原形状一项失败。复审另发现新入口绕过旧 provider 子类覆盖；独立回归复现绕过后意外初始化 BuiltInRegistries，修复后进入旧覆盖哨兵。初次抽象 fixture 方法缺失、Python 未传共享 IR 和 Fabric 守卫未注释的夹具错误单独保留，不混称功能红测。

最终定向共 48 项（common 3、五节点各 9），零失败错误/跳过。实际 MappedRegistry 覆盖后续注册、旧快照不变、空集合、相同来源去重、不同来源拒绝、确定顺序、显式 metadata 保留与符号冲突。两个正式 TypeScript backend 生成模式 full/server 的严格正负例、共享元数据的 Python Literal 通过；完整 `npm run test:probe-types` 通过。

Standards 初审指出重复平台十项映射，已集中；Spec 初审指出旧覆盖兼容问题，已修复。两轴最终只读复审分别 0 hard/0 smell、0 findings；审查不替代实际运行。

## 完整检查

`gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.2" :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --offline --no-parallel --console=plain`

PASS：5140 tests / 271 skipped / 0 failures / 0 errors，包含 common isolation、五节点 platform/artifact 门禁。复用 Gradle 对未变输入的有效结果，不宣称所有任务都重新执行。guard 为 383 blocks / 567 files / 0 warnings。定向报告在完整测试覆盖前独立冻结。

Wiki 结构检查 26 topics / 0 problems；完整严格文档检查仍因其他五主题的十项旧 stamp/translation 状态失败。仅更新 error-reference 与 probe-type-generation 两主题，保持 draft/pending，不代签其他主题。

## 验收边界

冻结此源码检查归档时，新安装完整 IDE 尚未执行，前一安装源码仍有 5857 条完整严格 TSC 失败；新安装结果随后另记，不用定向消费通过替代全图。正式性能没有在此批复测，前次 340.0 / 292.8 ms 超过原 285.3 ms，继续 FAIL。没有维护者整体验收、发布授权或新增成员删除。

归档也保留显式 this 的同名成员 throwaway 假设：它仍允许提取值赋为无参函数及 .call(receiver)，严格编译的两条 TS2578 证实假阳性；未采用该方案，没有修改生产成员。

离线复核：`python docs/architecture-refactor/evidence/2026-10-23-registry-catalog/verify.py`。归档 PASS 只证明本批证据一致，不代表完整 IDE、性能或发布已通过。
