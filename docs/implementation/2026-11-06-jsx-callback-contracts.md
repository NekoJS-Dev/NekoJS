# JSX 回调声明契约

## 问题与实现

`jsx-runtime.ts` 将函数组件和多种回调声明为 `Function`，NodeModuleTypeDocs 只提取并传播这些声明，无法提供参数、返回值或上下文推导。这是声明源的缺口，不需要让 Probe 猜测任意函数体。

本轮收紧 JSX runtime 的公开函数契约：

- `NekoUiCallback` 为 `() => void`：排队动作无参数，返回值被忽略。
- `NekoUiRender` 为 `() => NekoUiRenderable`：root render 明确返回 VNode、文本、数字、空值、布尔或这些值组成的数组；不接收 Promise 或任意对象。
- `NekoUiUpdater<T>` 为 `(current: T) => T`：signal 更新保留原值类型，store 更新绑定对应 key 的类型。
- `NekoUiComponent<Props>` 明确 props 和可渲染返回值；factory 保留具体 props 推导，JSX.ElementType 允许运行时已支持的文本、数组和空组件结果。异构 VNode 槽使用擦除的 props 类型，避免退回 Function 或 any。
- workspace 的 render 原先为 `() => unknown`，同样收紧为 runtime 的 render 契约；VNode 和共享 children 也使用可渲染值类型。
- `createStore` 从初始对象推导 State，get/set/update/snapshot 关联键和值。需要动态新增键时可显式用 `createStore<Record<string, unknown>>({})`，或者将初始状态声明为动态 Record。
- `batch` 返回回调结果，保留泛型 T；事件、验证器及高级控件已有具体签名，保持不变。

仅修改类型声明与函数的类型注解，不改变执行体、布局、状态调度、输入和 host 协议。普通合法调用保持；依赖过宽 Function、错误 updater 返回值、无 return 的 render 或对已推导 store 动态加键的 TypeScript 代码会被更早拒绝。`void` 按 TypeScript 常规规则表示忽略返回值，不表示必须返回 undefined。

范围为 JSX runtime 的公开回调。Node util 中 promisify/callbackify 等通用函数适配也有 Function 声明，但涉及 Node 兼容签名，未在本轮改动；不能机械地统一成一种无参回调。

## 验证计划及生成声明

- 新增真实声明消费者 `common/src/test/probe-ts/jsx-callback-contracts.tsx`：参数推导、返回值、组件 props、Promise／对象拒绝、store 键值、动态 Record 和 batch 结果。
- 修改 canonical runtime 类型，再用现有受控流程仅重生成 JSX golden；不手写派生 `.d.ts`。
- 生成命令：`./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' '-Dnekojs.golden.regenerate=true' :common:test --tests '*JsxRuntimeProbeDeclarationGoldenTest'`。只运行此套件的两项，保留 common Java 21 测试工具链，仅重生成 JSX runtime／workspace 两个 golden，不刷新其他 golden。
- 已审查 golden 差异：Function 别名、render/updater/store/组件及 JSX.ElementType 的预期变化，没有其他声明漂移。
- `tsc -p common/src/test/probe-ts/tsconfig.json` 通过，包含 18 个应被拒绝的消费用例；若退回宽泛 Function，未使用的 ts-expect-error 会使门禁失败。

## 最终验证

```powershell
./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' :common:test --tests '*JsxRuntimeProbeDeclarationGoldenTest' --tests '*NodeModuleTypeDocsTest' --tests '*NekoTypeScriptJsxRuntimeTest' --tests '*JsxControlsRuntimeTest' --tests '*JsxAdvancedControlsRuntimeTest' --tests '*JsxWorkspaceRuntimeTest' --tests '*BuiltinUiRuntimeTest' --tests '*TypeScriptJsxExamplesSmokeTest' --console=plain
./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python tools/nekojs-ci-gates.py all --out common/build/jsx-callback-ci-gates.json
python common/build/jsx-callback-audit.py
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
git diff --check
```

全部通过。聚焦检查 95 项，零失败／错误／跳过，见 `common/build/jsx-callback-focused-results.json` 和 `common/build/jsx-callback-runtime-focused-retry.log`。首次聚焦失败是已有声明断言仍引用旧的 NekoUiCallback，已同步为明确的 render 类型；没有修改运行逻辑。完整构建日志 `common/build/jsx-callback-full-build.log`；类型链日志 `common/build/jsx-callback-probe-types.log`。

| 项目 | 总数 | 失败／错误 | 跳过 |
|---|---:|---:|---:|
| common 完整门禁 | 2745 | 0／0 | 4 |
| NeoForge 1.21.1 | 476 | 0／0 | 33 |
| NeoForge 26.1.2 | 843 | 0／0 | 85 |
| NeoForge 26.2.0 | 843 | 0／0 | 85 |
| Fabric 26.1.2 | 441 | 0／0 | 39 |
| Fabric 26.2.0 | 441 | 0／0 | 39 |

最终普通模式的 JSX golden 两项均实际通过，零跳过；只有 JSX runtime 和 workspace 两个 golden 变化。guardLint 扫描 618 个文件、418 个守卫，零警告。五节点 platform gate 和四类 CI gate 通过。Wiki 30 双语主题零问题，文档工具测试 8 项通过。

8 个 JAR 的 36 个 AI payload、manifest 及相关编译类一致；7 个非 CLI JAR 的两个 runtime 模块与规范源逐字节一致。独立 authoring-stdio JAR 按现有打包范围不包含运行时模块，未扩大发布内容。文档 bundleId 为 `a383150cb4904efc85a545586334e884b929ac6f913ce44f9b64496061a017cb`，审计见 `common/build/jsx-callback-artifact-audit.json`。

本版未安装到运行中的游戏，也未做新的维护者实机验收。更新游戏 JAR 后需重新生成 Probe 声明，编辑器才会使用新契约。没有新增依赖、配置项或版本号，已有其他工作树修改保持原样。
