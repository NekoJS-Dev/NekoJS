# 枚举实例继承修复与实际声明复测

生产实现提交：`489b4a6b`；固定增量 `520401dc...489b4a6b`。主代理完成实现、构建、实机和提交；Luna xhigh 仅只读调查和双轴复核。

## 改动与契约

共享 IR 原先没有记录 Java 枚举的 `Enum<Self>` 父类，导致生成的枚举实例不能满足真实 API 的泛型约束。现在保留该父类，TS 用 class/interface 合并补齐实例继承，继续使用枚举自身的一参数静态 `valueOf`，避免与 Java `Enum.valueOf(Class<T>, string)` 的静态侧冲突。重命名、自引用、canonical 导出与 `$Enum` 导入碰撞分别有严格编译回归。

Python 保留专用枚举呈现，不引入 Java 父类投影或相关额外导入。运行时 API、扫描深度/白名单、数据和 wire 契约未改。没有新增依赖、放宽类型检查或手改生成输出。

五份 legacy golden 通过 `:common:regenerateGoldens --tests '*LegacyProbeTreeTest.legacyTreeMatchesGolden'` 更新。主代理核对全部差异：仅五行 import 扩充与十个枚举的合并接口，未改其他成员。更新后以普通断言运行完整检查；维护者最终发布验收仍开放。

## 验证

```text
.\gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python docs/architecture-refactor/evidence/2026-10-16-followup-acceptance/verify.py
```

最终完整构建 PASS，2m34s，106 tasks（13 executed / 93 up-to-date）。七模块现存完整测试 XML 合计 4909 tests、271 skipped、0 failures/errors；跳过项不算通过。普通 Probe 类型测试和新增三个严格 TSC 项目均 PASS。相同项目对旧声明分别 exitCode=2，新声明分别 exitCode=0，负例检查仍生效。

每个新官方 JAR 都逐类核对 common 和节点编译 class 的字节，清单绑定确切 `489b4a6b` 源码、生产 patch、fixture 与制品摘要。五个全新专属服务器均完成注册、嵌套配置、方块、重载、Python AST 和原生模块断言；NeoForge 另通过流体与交易物理池生命周期，现代 NeoForge 通过动态物理对象保留和 reclaim。五个 runtime verdict PASS，五次正常 RCON stop 后 exitCode=0。

完整默认 Pyright 1.1.414 逐份分析所有实际 stub，保持原 basic 设置。严格 TSC 使用实际默认编辑器入口、`skipLibCheck=false`，未用定向 fixture 替代。

| 节点 | 全部 stub / Pyright | TSC server：前 → 后 | TSC startup：前 → 后 |
|---|---|---:|---:|
| NeoForge 1.21.1 | 320 / 0 error、0 warning | 586 → 572 | 587 → 573 |
| NeoForge 26.1.2 | 378 / 0 error、0 warning | 744 → 725 | 745 → 726 |
| NeoForge 26.2.0 | 346 / 0 error、0 warning | 638 → 619 | 639 → 620 |
| Fabric 26.1.2 | 296 / 0 error、0 warning | 1162 → 1148 | 1163 → 1149 |
| Fabric 26.2.0 | 263 / 0 error、0 warning | 917 → 903 | 918 → 904 |

十个入口合计减少 160 条 TS2344。按文件、错误码、首行消息和出现次数比较，新增诊断为 0；仅忽略源码位置变化，不忽略错误消息。缺失声明、其他泛型和继承问题仍在，完整 TSC gate 继续 FAIL。

## Python 比较边界

五节点全部 Java 类 stub（包括枚举）与前一批实际产物逐字节一致。完整 Python 目录并非逐字节相同：入口和事件模块出现声明顺序变化；Fabric 26.2 的两个同名 `PlayerEntityInteractEventJS` 还交换了本地导入别名的分配。

原始新旧字节、差异、hash 和原始比较结果均保留。verifier 将每个导入别名解析到完整模块/类型身份，再比较 AST、导出名称的重复次数和同名全局变量的最终注解；并断言没有局部遮蔽导入。所有变化模块的这些事实一致。它仍逐字节核对所有 Java 类 stub，不将整个目录标记为字节一致。声明输出顺序的稳定性仍需单独收口。

## 失败记录与证据

初始红例、一次 fixture 模块定位错误、两个预期 golden 断言失败，以及社区精简 fixture 缺少 Java Enum 基类的类型错误均保留；修正 fixture 后再正常复跑。第一轮实机的三个 NeoForge 因验收器漏装交易观察脚本而未通过，两侧 Fabric 通过；五台均正常退出。第二轮只补验收 fixture，保持同一源码/JAR，使用全新目录重跑全部节点并通过；没有改产品代码或降低验收标准。

[归档](enum-evidence.zip)、[摘要](enum-summary.json)和[清单](enum-manifest.json)包含上述记录、完整生成声明、完整诊断、编译器退出码、测试 XML、源码/class/制品绑定及复现工具。归档不含 JAR、世界、口令或用户 profile。首次 Python 比较未解析本地导入别名，原始 false 结果仍在摘要；独立 verifier 根据归档的新旧字节重新计算类型身份，不修改原始记录。

规范轴对生产增量发现 0 项；需求轴最初指出尚缺修复后的完整实际声明结果，本报告与归档补齐该验证步骤。最终只读复核结论见 [REVIEW.md](REVIEW.md)。本轮未复跑性能、其他节点客户端或最终候选回滚，也未代签发布门禁。票 28、37 继续开放。

## 下一处具体调查入口

Luna 对稳定的上一批完整 26.1.2 声明作了两项限定调查，仅记录实际发现，不把推断当验证：

- `MemoryLayout.PathElement`、`UserApiService.UserFlag`、`Provider.Service` 的声明实际未被选入，而非已生成却拼错嵌套名。collector 在配置深度处停止签名边扩展；`MemoryLayout` 的深度边界已有记录。不能靠改 import 拼写解决，也不据此将全部 TS2304 都归为同一原因。
- 十二条 TS2314 中，八条引用裸泛型 host 类型（`Class[]`、四个 `Packet`、三个 `AttachedData`），另四条来自 generic adapter 的输入别名：`Holder_`、`ParticleType_`、`EntityType_`、`BlockEntityType_` 的 self 分支没有泛型实参。`AdapterAliasGenerator.renderAlias`/`render` 与 `TypeScriptClassRenderer.renderSymbol` 是后者的共享修复入口。后续应先用泛型目标与带实参调用的回归证明，保留 typed use-site 实参；裸 Java 签名和擦除语义尚需直接核对，不以统一 Any 或泛型默认值遮盖。

这两项未实施新的生产修改，也未扩大扫描范围。Python 入口/事件输出顺序仍需另行处理；当前报告只证明枚举 Java 类声明保留及此次完整输出的实际结果。
