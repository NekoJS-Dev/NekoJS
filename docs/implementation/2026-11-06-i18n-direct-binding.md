# I18n 直接 Binding 与同类代理审计

## 目标及审计

按维护者要求，先由 `gpt-6-luna`、`xhigh` 只读检查同类代理，再由主代理修改。审计没有修改文件，主代理在收到结论后才开始修改生产代码。

唯一明确可合并候选为 `I18nBinding`：它逐项包装一个 `I18nFacade`，没有自身状态、生命周期或组合委托。NeoForge、Fabric 和内置 UI 三处只为同一实现套上此代理。

| 已检查对象 | 结论及理由 |
|---|---|
| I18nBinding | 删除专用代理，直接注册 I18nJS；输入检查归原生实现 |
| DelegatingBinding | 保留：合并 helper 和 Minecraft 静态目标，跨 Context 重绑 |
| ApiFacadeProxy | 保留：受管 API 名录、重载选择、参数／返回值转换与错误规范化 |
| EventGroupJS、ScriptEventGroupJS、ScriptEventBusJS、EventBusJS | 保留：动态命名、订阅与回调 |
| RegistryEventJS、BuilderSurface、DynamicBuilderSurface、配方及命名空间代理 | 保留：按 schema／扩展动态生成入口，统一构建校验和发布 |
| ModificationViewSurface | 保留：属性写入与显式 setter 的规范化；已有普通 Bean 写入丢失的回归证据 |
| PostEffectBuilder、PostEffectEventJS | 保留：收集回调错误，并在 finish 时拒绝发布被脚本 catch 的无效候选 |
| PackCapabilityBridge | 保留：activation 失效、跨 Context 回调及 JSON 边界 |
| BuiltinUiHostBridge | 保留：原生节点句柄映射和 JSX host 协议转换 |

不因类名含 Proxy 或 Wrapper 就删除；本轮实现范围限定 I18n。

## 修改及行为

- 删除 common `core/ui/I18nBinding.java`，普通 CLIENT 和内置会话直接安装 `I18nJS`／`I18nFacade` 对象。
- common 保留 `api/facade/I18nFacade`，继续隔离 Minecraft 依赖。TypeDocs 移回已有 `NekoCommonBuiltinPlugin.registerTypeDocs`；脚本声明形状保持不变。
- `I18nJS` 校验非 null key/fallback 和格式参数类型，保持整数／小数规范化。辅助方法设为 private，无需新增隐藏注解或公开辅助 API。
- 内置会话复用普通脚本已有的 `js.nashorn-compat` Bean 属性规则、strict 和 `js.ecmascript-version=latest` 设置。兼容选项默认会回退到旧语法，必须显式保留现代语法才能编译现有 ESM／JSX。移除仅测试使用的 HostAccess.NONE 默认构造入口；调用方显式传入共享策略。生产工厂原本就传共享策略，不改变全局 HostAccess。
- 三个方法、两个只读属性、实时语言读取和普通有效调用保持。按普通 Java Binding 处理可变参数：末尾数组可展开，嵌套对象仍拒绝，建议类型脚本使用显式 spread。一个 null 格式参数仍按一个值处理。
- 参数数量／Java 参数类型和只读写入错误由既有 Graal 互操作报告；原生值校验继续使用 NEKO-4030。继承的普通 Java 宿主成员也按现有 Binding 规则处理，不再维护 I18n 专用成员白名单。

诊断探针 `common/build/I18nDirectInteropProbe.java` 与日志 `common/build/i18n-direct-interop-probe.log` 已实测：Nashorn 兼容开启时 getter 属性可读、方法与只读属性写入拒绝；数字 key 被 String 参数拒绝；Java varargs 展开末尾数组。

## 验证计划

1. common 直接互操作、真实生成声明、内置会话与 root 生命周期。
2. 五节点真实 I18nJS：注册对象、参数校验、普通 Java varargs、只读与动态属性。
3. 两个 NeoForge 26.x 内置 UI 原生回归、完整 common 门禁和五节点构建。
4. 完整 Probe 类型链、guardLint、CI gates、Wiki 和打包内容一致性。

当前状态：修改、完整自动验证和打包核对完成。本轮未替换运行中游戏的 JAR，未进行新的实机验收，前一版反馈不扩大为此版通过。

聚焦验证已通过：common 16 项、五节点原生 I18n 各 3 项、两个 NeoForge 26.x 内置 UI 各 4 项，共 39 项。覆盖直接对象注册、null 与对象拒绝、末尾数组展开、单个 null 的参数数量、只读属性、实时元数据、原生 Screen、图标及关闭／回退路径。日志 `common/build/i18n-direct-binding-focused-retry.log`，明细 `common/build/i18n-direct-binding-focused-results.json`。

二次只读复核与探针指出手写数字边界判断会丢失负零或 64 位整数精度；最终实现直接复用原有 Graal 数字转换规则，没有保留另一套浮点范围算法。五节点新增 `-0`、Long.MAX_VALUE BigInt 及不可无损转换整数回归均通过，日志 `common/build/i18n-direct-binding-numeric-regression-retry.log`。单独运行还暴露注册测试依赖其他套件先初始化 Platform，已改成自带最小平台 fixture；生产初始化流程未变。

`npm.cmd run test:probe-types` 全链通过，日志 `common/build/i18n-direct-binding-probe-types.log`。Wiki strict 检查 30 双语主题零问题，工具测试 8 项通过。

## 最终完整验证

```powershell
./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python tools/nekojs-ci-gates.py all --out common/build/i18n-direct-binding-ci-gates.json
python common/build/i18n-direct-binding-audit.py
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
git diff --check
```

全部通过。数字修订后的构建日志 `common/build/i18n-direct-binding-final-build.log`，四类 gate 报告 `common/build/i18n-direct-binding-ci-gates.json`。保留 Zulu 25 daemon 和 common Java 21 测试工具链，未刷新 golden。

| 项目 | 总数 | 失败／错误 | 跳过 |
|---|---:|---:|---:|
| common 完整门禁 | 2745 | 0／0 | 4 |
| NeoForge 1.21.1 | 476 | 0／0 | 33 |
| NeoForge 26.1.2 | 843 | 0／0 | 85 |
| NeoForge 26.2.0 | 843 | 0／0 | 85 |
| Fabric 26.1.2 | 441 | 0／0 | 39 |
| Fabric 26.2.0 | 441 | 0／0 | 39 |

五节点 platform gate 和运行产物门禁通过。guardLint 检查 418 个守卫、618 个文件，零警告。8 个 JAR 的 manifest、每个 JAR 内 36 个 AI payload 及相关编译类均与规范源一致，且全部没有 `I18nBinding.class`。原 JSX 界面在 7 个非 CLI JAR 中保持一致，哈希仍为 `0cf097bfb47d06067e937a7b8a9fc9fc09a91147ebdd0b4ef0e096757535d9a8`。

产物核对报告 `common/build/i18n-direct-binding-artifact-audit.json`，文档 bundleId 为 `17be5827dd6a05ce61bb2e4cc210c5396bb9bbc40fa0ee63e5526b0e03f0b273`。没有新增依赖、配置项或版本号，也没有修改其他代理的生产逻辑。

NeoForge 26.1.2 新 JAR：`versions/26.1.2/build/libs/nekojs-neoforge-26.1.2-1.1.0-preview3.jar`，SHA256 `520f9f24b364444ab385cd67f7c01b99b4fc8e2c9d7821705806962975e99d8c`；尚未安装到当前游戏。
