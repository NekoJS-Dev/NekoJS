# JSX UI 增强与内置文档交付记录

## 范围与契约

本轮按现有 retained JSX UI 扩展输入与常用控件，不引入 React、DOM、任意 CSS 或新的平台适配层。原生宿主仍仅在 NeoForge 26.x 启用。保留无关工作区修改，不提交、不推送、不升版本。

- 输入：复制/剪切/粘贴、Unicode 边界和单行过滤、长文本光标跟随及选区/点击坐标一致性。
- 控件：原生 `slider`，以及 `nekojs/jsx-runtime` 导出的受控 `Toggle`、`Slider`、`Select`。组件状态由调用者保存，不能在每次 render 中重建 signal。
- 外观：按钮、输入框和滑块共用有限 `appearance` 颜色字段；状态叠加顺序为 base → hover → focused → pressed → disabled。不是 CSS。
- 兼容性：原始 `NekoUiEvent.value` 仍为字符串；`Slider` 组件适配成数字回调。现有输入框调用者不必迁移。
- Select：调用者控制 value 与 expanded；展开内容是有界滚动的内联列表，不是原生浮动菜单。选项 value 唯一，显示 label 可重复。
- 文档：英文 canonical handbook、完整 CLIENT TSX 示例、安装/触发/结果/清理说明与中英文 wiki 同步；现有 Skill/AGENTS 通过 handbook 路由，不复制整本资料。

## 实现与回归范围

三路修改分别负责原生宿主、共享运行时/类型与可复用组件、内置资料/wiki；Lead 负责真实模块加载示例、旧 catalog 回归、声明生成与最终集成验证。派生声明只走既有生成工作流。

旧 catalog 测试有意从 11 项改为 12 项，新增 slider 的受控值事件断言，并按集合核对完整 primitive 列表，避免把枚举顺序误当作排序契约。原有 counter 示例保持不变。

## 审查与回归修正

- Select 的自然高度现在计入子控件的显式高度，防止嵌套布局把收起按钮裁到 9px、展开选项裁到 0px。覆盖六种 profile、响应式间距、横向布局、逻辑坐标与显式 height/min/max；不改动整个共享布局算法。
- 原生 slider 在指针比例的两端直接取 min/max，避免 min=-10、max=0.1、step=1 的浮点抵消把右端点再次吸附到 0。新增精确端点、越界拖动/释放及原生宿主到真实 JSX 回调的字符串事件回归。
- 原生事件仍使用现有 Java Map 传递。真实 Graal + 内置 TSX 示例证明输入中文/emoji 与滑块字符串值可以往返；Java 测试回调应读取 hash entry，而非把 Map entry 当作 Value.getMember。没有为测试误读修改生产传输。
- 声明契约有意将输入事件回调从宽泛 NekoUiCallback 改成 NekoUiEventCallback，保留原始 value?: string；同步原有 builtin 提取测试并额外核对三个组件导出。未删除断言或放宽类型门禁。
- JSX 声明只通过既有 filtered golden 测试重新生成，审查差异为 70 行新增、13 行删除，限于 slider、组件、有限 appearance 与具体事件回调；不刷新无关 golden。

## 验证状态

| 检查 | 实际结果 |
| --- | --- |
| 共享运行时、外观、真实 TSX 示例与既有 UI focused 回归 | 49 tests 通过；其中 10 个真实运行时控件用例 |
| Java Map → 真实内置 controls TSX | 1 个真实引擎用例通过，中文/emoji 与字符串值往返，diagnostics 为 0 |
| builtin 模块声明回归 | 24 tests 通过，检查具体事件类型与 Toggle/Slider/Select 导出 |
| Probe TypeScript 检查 | npm run test:probe-types 通过 |
| 全量 common、处理器、隔离、guardLint、五节点 build 与三个 NeoForge NBT smoke | 通过；common 2693 tests、367 suites、0 failures/errors、4 skips；guardLint 390 guards / 589 files / 0 warnings |
| 原生新增控件回归 | NeoForge 26.1.2 与 26.2.0 各 14 tests、0 failures/errors/skips，含真实 JSX 字符串事件、精确小数端点与嵌套 scissor |
| 五节点普通测试报告 | 1.21.1：472 tests / 33 skips；26.1.2、26.2.0：各 729 / 83；两个 Fabric：各 438 / 39；全部 0 failures/errors |
| CI source roots 与 aggregate gates | 五节点实际 Gradle source-roots 探针均重新生成并核对时间；all 为 4 gates、0 failures |
| Wiki | sync/stamp/strict 通过：30 topics、0 issues；工具测试 8 passed；60 页 282 个代码块语法检查通过 |
| Wiki 兼容性 | 52 个既有页面数字锚点稳定、234 个证据目标可解析、生成流程幂等；30 topics 保持人工待审 |
| 文档与生产产物 | common JAR、独立 stdio JAR 及五个生产节点 JAR 均含 34 份与 canonical 源码逐字节一致、哈希匹配的文档资源；五节点 JSX runtime 也与源码一致 |

首次全量 common 发现一项旧声明断言仍预期宽泛回调类型，已按实际新类型增强断言并通过 focused 与最终全量回归。构建保留非阻断的 JDK native-access/Unsafe/deprecation 和测试 fixture close() checked-exception 警告，没有为通过门禁屏蔽或排除测试。

最终 bundleId：`6540155a278556e61a9c7227742cb11b56b4319fdfa172fd589414fbbdd30e9e`。独立 stdio JAR 保持纯查询类隔离、无原生客户端执行器且 class major ≤ 65；生产 JAR 的原生 JSX 宿主仅出现在两个 NeoForge 26.x 节点。

### 实际执行命令

使用 Zulu 25 运行 Gradle daemon，保留 common 测试 Java 21 toolchain；最终普通 build 不启用 golden regeneration：

```bash
export JAVA_HOME="C:/Program Files/Zulu/zulu-25"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew.bat "-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25" \
  :common:check :common-api-processor:test :common:assemble guardLint \
  :1.21.1:build :26.1.2:build :26.2.0:build \
  :26.1.2-fabric:build :26.2.0-fabric:build \
  :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest --console=plain
npm run test:probe-types
for node in 1.21.1 26.1.2 26.2.0 26.1.2-fabric 26.2.0-fabric; do
  python tools/nekojs-ci-gates.py source-roots --node "$node" || exit $?
done
python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json
node scripts/wiki-docs.mjs sync
node scripts/wiki-docs.mjs stamp --all
node scripts/wiki-docs.mjs check --strict
node --test scripts/wiki-docs.test.mjs
git diff --check
```

另以本地构建审计脚本验证 wiki 的 282 个语法块、既有锚点和证据链接，并读取实际 JUnit XML、七个 JAR 的文档 manifest/源码/哈希和实际 source-roots 文件。语法块检查不等于 Minecraft 中运行所有示例。

## 未交付与验收边界

- 本轮没有 Fabric/1.21.1 JSX 原生 UI、服务端权威容器 Slot、物品显示原语、通用拖放、grid/wrap、虚拟列表或完整浏览器样式系统。
- 原生 slider 的内部主键拖动不等于公共 `onDrag`/拖放 API。
- 单元测试与 fake host 只能证明其覆盖的引擎/输入/布局契约，不能证明 Minecraft 实机绘制、系统剪贴板集成、IME、GPU 或多人游戏验收。
- 维护者实机结论待记录；不伪造人工通过。
