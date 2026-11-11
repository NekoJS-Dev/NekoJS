# Probe 原生 JSX 宿主类型与 TSX 补全验证

## 目标与实现

在 `versions/26.1.2/run/nekojs/client_scripts/src/probe-ui-demo.tsx` 提供真正的 TypeScript JSX 示例，验证事件、泛型状态、组件属性与原生宿主的补全。该目录属于本地游戏工作区，不纳入 Git。

修复生成器：增加方法级 `@ScriptOutput` 元数据，经反射 IR 进入 TypeScript 输出；保留继承和 Bean 属性，显式 Probe 编辑优先，Python 继续使用 Java 类型。该注解使用可解析的 TypeScript 表达式，不负责引入额外声明依赖，也不改变运行时值。

原生 JSX 宿主为测量与视口返回值声明真实的字段形状，并通过交叉类型保留 Java Map 方法。普通 Map 不变。便携宿主的 `layout` 契约要求视口和快照参数，与运行时始终提供三或四个实参的行为一致。移除示例旁的临时兼容声明。

## 验证与后续

以下均已通过，Gradle daemon 使用 Zulu 25，common 测试工具链保持 Java 21：

| 命令／检查 | 结果 |
|---|---|
| `:common:check guardLint` | 2749 项，零失败／错误，4 跳过；隔离与源码门禁通过 |
| `:26.1.2:build :26.2.0:build` | 各 844 项，零失败／错误，85 跳过；platform gate 通过 |
| `:1.21.1:build :26.1.2-fabric:build :26.2.0-fabric:build` | 分别 476／441／441 项，零失败／错误，33／39／39 跳过；platform gate 通过 |
| `:common:regenerateGoldens --tests '*JsxRuntimeProbeDeclarationGoldenTest'` | 通过现有流程再生成；唯一 golden 差异为 `layout` 的视口、快照从可选变为必需 |
| `npm.cmd run test:probe-types` | 完整类型链通过，包含新的输出形状消费者；common 消费者检查声明，`skipLibCheck: false` |
| `npm.cmd run test:probe-native-jsx` | 两支持节点真实宿主消费者通过；包含错误渲染结果、错误尺寸类型、只读字段和错误 layout 参数的负例 |
| 最终原生聚焦回归 | 补充视口负例后，两节点各 1 项通过；生产实现未再次变更 |
| 本地 TSX 示例 | 使用实际工作区声明检查通过；NekoJS classic TSX 编译与 Graal JS 语法解析通过 |
| TypeScript language service | 事件 `NekoUiEvent`，Signal／Store 更新参数 `number`，Toggle 参数 `boolean`，Slider 参数 `number`，Store 键仅 `name`／`saves` |
| `python tools/nekojs-ci-gates.py all` | 四类门禁零失败 |
| `npm.cmd run check:wiki`、`git diff --check` | 30 个双语主题零问题；差异检查通过 |
| JAR 一致性审计 | 8 个 JAR 各 36 个 AI payload 与规范源、manifest 哈希一致；7 个非 CLI JAR 的 JSX 模块、仪表盘和新增注解均核对通过 |

完整测试计数保存在 `common/build/probe-ui-generator-test-audit.json`；类型、构建和审计日志使用 `common/build/probe-ui-generator-*` 前缀。原生聚焦检查会替换对应节点的 JUnit 结果目录，完整测试计数记录在其运行之前。

原生消费者使用与安装的编辑器配置相同的 `strict: true`、`skipLibCheck: true`，验证脚本如何消费真实宿主。最初尝试检查最小目录快照闭包的全部 Minecraft 声明时出现继承冲突；本轮不声称已解决或完整检查该声明图。独立 common 输出元数据消费者仍检查声明，并验证普通 Map 不会获得尺寸字段、继承与 Bean 形状有效、Python 及显式编辑优先级保持正确。

本地游戏工作区的 UI 包声明和 `@manual` 已从真实后端输出刷新，未手改生成结果。运行中的旧游戏仍持有旧生成器，重新运行客户端后，后续 Probe 才会使用新实现。`main.js` 与游戏配置未改。

本轮不扩大原生 JSX 的平台支持，不包含游戏画面或维护者人工验收结论；没有版本升级、提交或远程同步。
