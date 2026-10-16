# Java raw 输入的 TypeScript 实参修复

最终生产源码 `04550ae2`，包含前置 `53461402`，固定增量 `dc67d1e0...04550ae2`。主代理负责全部实现和验证，Luna xhigh 只读调查与双轴复核。

## 改动与契约

在 TS 的反射输入路径中，为实际裸泛型 Class 使用点补充 Java raw 的 any 实参，包括 Class 数组、普通容器内嵌 raw、通配上界，以及函数接口 host/callback 槽。显式 ParameterizedType 保留实参，普通 host 容器内容保留不变类型身份；只有实际 raw 部分擦除，旁边的 typed 部分保持。模块本地导入引用仍由原注册表分配。

该规则只构造 TS 后端的输入引用，不修改共享 IR、Python、类声明的泛型参数/上界或默认值；编辑后的输入槽仍走原 IR 路径。显式适配器和已有函数接口别名继续优先，不重开扫描配置。

## 回归与完整构建

```text
.\gradlew.bat --offline :common:check :common-api-processor:test guardLint :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
python tools/nekojs-ci-gates.py all --out build/domain-raw-input-ci-gates.json
python build/domain-closeout-tooling/check-raw-input-strict.py
python build/domain-closeout-tooling/accept-raw-input.py
python docs/architecture-refactor/evidence/2026-10-16-followup-acceptance/verify.py
```

两个新增 JUnit 回归覆盖反射输入、二维数组、模块引用、顶层 alias 和嵌套 invariant host 的区别，以及通过真实类渲染和真实函数接口 alias 生成的严格 TSC 项目。负例检查 typed/edited 参数、泛型 bound、容器 typed 兄弟槽、函数接口返回值和显式 callback 实参；所有负例都要求编译器真实拒绝错误输入。

修复前两个 JUnit 断言失败，原生成声明严格 TSC 有两处 TS2314。初步局部修复后，普通容器内嵌 raw 回归另有一处 TS2314；规范复核又发现 SAM 的并行路径遗漏，使用 `53461402` 的真实实现和最终 callback fixture 重现两处 TS2314。三份失败生成项目均完整保留；最终生成项目严格 TSC exitCode=0，已接入常规 `test:probe-types`。没有通过忽略声明或移除 bound 使负例通过。

最终完整矩阵 PASS，3m46s，106 tasks（34 executed / 1 from cache / 71 up-to-date）。七模块完整测试 XML 合计 4923 tests、271 skipped、0 failures/errors。复核后的额外断言及 SAM 修复均在最终完整矩阵中执行；此前两个矩阵只作前置记录。

## 五节点实际检查

每个新官方 JAR 都按 class 字节绑定最终源码；五个新独立服务器 runtime verdict 全部 PASS，正常 RCON stop 后 exitCode=0。常规 Probe 类型回归、新增严格 TSC 项目及四项 CI gate PASS。完整默认 Pyright 逐份检查全部实际 stub，五节点均为 0 error / 0 warning。

完整严格 TSC 保留原 server/startup 入口和 `skipLibCheck=false`：

| 节点 | server：前 → 后 | startup：前 → 后 | 每入口新增 / 移除 |
|---|---:|---:|---:|
| NeoForge 1.21.1 | 690 → 685 | 691 → 686 | 0 / 5 |
| NeoForge 26.1.2 | 864 → 859 | 865 → 860 | 0 / 5 |
| NeoForge 26.2.0 | 636 → 631 | 637 → 632 | 0 / 5 |
| Fabric 26.1.2 | 1271 → 1240 | 1272 → 1241 | 0 / 31 |
| Fabric 26.2.0 | 892 → 858 | 893 → 859 | 0 / 34 |

十个入口共减少 160 条 TS2314，逐条诊断多重集无新增；完整 TSC 仍 FAIL。五节点的所有 Java 类 Python stub 与上批逐字节一致；入口/事件模块的顺序与本地导入别名差异按完整类型身份核对相等，不声称整个目录字节一致。

## 证据与范围

[归档](raw-input-evidence.zip)、[清单](raw-input-manifest.json)、[摘要](raw-input-summary.json)保存源码/class/制品绑定、完整测试 XML、三份 red/一份 green 严格项目、完整实际运行和 IDE 日志与工具。独立校验重算诊断多重集、fixture 摘要和 Python 类型身份，不把完整 TSC 失败改写为通过。

双轴最终 Standards 0 / Spec 0，均为静态复核；过程中发现的嵌套 alias 覆盖与 SAM 路径问题均已补齐。未在本批重跑正式性能、客户端首帧或最终候选数据回退。最近正式重载仍 FAIL，不能用本批 Probe 类型检查代替。

[Entity Bean 精确迁移提案](ENTITY-BEAN-DECISION.md)是下一项待维护者决定的公开声明变化，尚未实施；本批没有删除或改名任何 Bean alias。票 28、37 仍开放，没有新增 PR、执行远程发布或代签维护者验收。
