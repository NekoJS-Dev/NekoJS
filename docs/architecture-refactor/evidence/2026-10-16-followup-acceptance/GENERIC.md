# 泛型适配器输入别名修复

生产实现提交 `f3f32a69`，固定增量 `10a46700...f3f32a69`。主代理负责所有修改、构建和验证；Luna xhigh 仅只读复核。本报告不代签票 28、37 或最终发布验收。

## 改动与兼容

适配器的 Self 分支原先生成裸 `$Holder` 等泛型 host，输入位置又丢弃具体实参。现在别名声明携带目标的类型参数、bound 和对应 Self 实参，数组和对象内的嵌套 Self 也传递同一组实参。反射和中性 IR 两个输入渲染入口都保留 host 类型身份；参数、bound 优先使用 `ctx.ir()` 中经过声明编辑事件修改的事实。

导出名和导入分配器仍使用裸标识符。模块视图复制泛型 metadata；清空注册表、替换为普通别名时移除旧 metadata。未提供共享 IR 的既有调用仍可只反射泛型参数，不建立第二份完整成员扫描。

只有真实泛型 adapter 的输入别名添加 `= any` 默认值，以保留已有 `$Foo_` 裸写法的 Java raw/擦除语义；显式 `$Foo_<string>` 仍检查具体值和 bound。没有给全部 Java class 添加默认泛型、扩大扫描深度、扩展 `HostValue` API 或添加 suppress。运行时 API、数据与 wire 契约没有变化。

## 定向验证与构建

```text
.\gradlew.bat --offline :common:test --tests '*TypeScriptGenericAdapterTest' --tests '*AdapterAliasGeneratorTest' --console=plain
.\gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python docs/architecture-refactor/evidence/2026-10-16-followup-acceptance/verify.py
```

初始实现前的三个 Java 回归失败；保留完整旧生成声明，三个严格 TSC 项目分别 exitCode=2，并记录 TS2314、TS2315 及错误类型未被拒绝所致的 TS2578。修复后的四个 Java 回归通过，普通、重命名、编辑后的 bound 三个严格 TSC 项目分别 exitCode=0。负例覆盖具体实参、数组/对象中的 Self、上界和递归 bound；编辑场景还经过中性 IR 渲染入口。裸别名仍接受擦除值。

完整构建 PASS，3m19s，106 tasks（50 executed / 56 up-to-date）。七模块现存完整测试 XML 合计 4913 tests、271 skipped、0 failures/errors；跳过项不算通过。常规 Probe 类型检查及所有新增严格 TSC 项目 PASS；未手改或更新 golden。

本轮新增 fixture 未单独构造同名 bound 的导入碰撞；源码使用已有导入分配机制，不能将静态核对描述为这一场景的新直接编译证据。性能、其他节点客户端首帧及最终候选的数据回滚尚未在本批重跑。

## 五节点实际输出：声明门禁仍失败

每个新官方 JAR 均逐类核对 common 与节点编译 class 的字节，清单绑定确切源码、生产 patch、fixture 和制品摘要。五个全新独立服务器均完成注册、嵌套配置、方块、重载、Python AST 与原生模块断言；NeoForge 另通过流体和真实交易池生命周期，现代 NeoForge 通过动态物理对象保留/reclaim。全部 runtime verdict PASS，五次正常 RCON stop 后 exitCode=0。

完整默认 Pyright 1.1.414 逐份分析全部实际 stub，保持 basic 配置，五节点均为 0 error / 0 warning。严格 TSC 使用真实默认 server/startup 编辑器入口、`skipLibCheck=false`。

| 节点 | stub 数 | server：前 → 后 | startup：前 → 后 | 每个入口新增 / 移除 |
|---|---:|---:|---:|---:|
| NeoForge 1.21.1 | 320 | 572 → 693 | 573 → 694 | 125 / 4 |
| NeoForge 26.1.2 | 378 | 725 → 867 | 726 → 868 | 146 / 4 |
| NeoForge 26.2.0 | 346 | 619 → 639 | 620 → 640 | 24 / 4 |
| Fabric 26.1.2 | 296 | 1148 → 1290 | 1149 → 1291 | 146 / 4 |
| Fabric 26.2.0 | 263 | 903 → 923 | 904 → 924 | 24 / 4 |

十个入口合计移除 40 条 TS2314，同时新增 930 条 TS2344，净增加 890 条诊断。因此不能把本批写成完整声明改善或无新增诊断；完整 TSC 与无新增诊断的判断都为 FAIL。比较逐条保留文件、错误码、首行消息和重复次数，只忽略位置变化，包含脚本和全局错误。

已直接确认的一条错误链是 `$Bat` 不满足 `$Entity`：前一批在 `$EntityType.BAT` 已有相同 `attackable` 冲突，父类呈现 boolean Bean 属性，子类呈现 `() => boolean` 方法。本批 `$EntityType_<$Bat>` 的构造参数保留实参后，在更多位置检查出了这一冲突。Java 子类型和 Graal Bean/普通方法投影的关系需要继续处理，不能为了消掉诊断重新丢弃实参、移除 bound 或把取出的标量谎称为可调用。其他新增错误链未逐条归为这一原因。

所有 Java 类 Python stub 与前一批逐字节一致。入口/事件模块仍有顺序和本地导入别名分配差异；保留原始 false 字节比较，将导入别名解析到完整类型身份后，AST、导出重复次数和同名全局变量的最终注解均相等，不声称整个目录字节一致。

## 证据与复核

[归档](generic-evidence.zip)、[清单](generic-manifest.json)与[摘要](generic-summary.json)保存新旧定向 fixture、红/绿严格检查、完整运行日志与声明、所有实际 IDE 诊断、测试 XML、源码/class/制品绑定及复现工具；不含 JAR、世界或口令。四项只读 CI gate 另通过，源码根挂载没有变化。

独立 verifier 通过的是证据完整性检查：它重新计算新旧诊断多重集，并明确断言新增 930 / 移除 40、`noNewDiagnostics=false`、`fullTscPassed=false`；不是把声明 gate 改成通过。既有性能失败记录和阈值仍保留。

固定源码增量的规范轴 0 项、需求轴 0 项 P1/P2 实现问题；均为只读静态复核，不代替完整实际声明检查。后续完整结果暴露的约束冲突已记录，见 [REVIEW.md](REVIEW.md)。票 28、37 继续开放，分支保持草稿交接状态。
