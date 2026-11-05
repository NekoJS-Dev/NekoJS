# 统一本地化 Binding 与内置 JSX 模块导入

## 目标与范围

为五个受支持节点提供 CLIENT-only `I18n.translate(key, ...args)`，使用当前客户端语言、保留 Minecraft 翻译键和格式语义；服务端继续使用延迟解析的 `Text.translatable`。普通脚本通过现有 Bindings Point 注册，内置诊断会话复用同一 Binding 实现，不安装用户插件模块或依赖用户 generation。

生产仪表盘改为标准 ESM：显式 `import { UI } from 'nekojs/jsx-runtime'`，导出工厂，JSX 使用现有自动 runtime 编译。内置会话只允许内存中的规范打包资源解析，不设置全局 UI 或 classic JSX 工厂；保留 root-owned 生命周期、复制／源码操作桥、失败回退和零错误显式打开。

## 实施与验证计划

1. 保留当前工作区全部既有修改，检查普通 Binding 注册／声明生成和现有模块资源约定。
2. 增加 common 本地化契约与统一 guest 参数桥、Minecraft 版本树实现，并接入 NeoForge／Fabric 的 CLIENT Binding 注册。
3. 使用 Graal 原生 ESM 与只读资源文件系统导入规范 JSX runtime，调整 TSX 工厂和原生装配，验证全局 UI 不存在、未授权导入失败、会话清理与重载隔离。
4. 更新实际 Binding、Probe 声明与类型消费者测试；通过既有生成流程刷新需要变化的产物，说明有意变化，保持 Java 21 测试工具链。
5. 同步英文内嵌手册、示例和双语 wiki，运行 common 完整检查、五节点构建、适用类型链／CI gate／打包核对。
6. 安装到既有隔离实例、正常保存退出并重启大窗口，由维护者通过选项工具执行实机验收；临时测试资源结束后清理。

## 当前状态

实现、五节点聚焦注册测试、完整门禁与构建、完整 Probe 类型链和打包审计已完成；新 JAR 已安装、游戏已重启为大窗口，普通 CLIENT Binding 的真实翻译和参数替换已从日志确认。临时日志脚本已清理，维护者已确认界面正常，并在最后修订后确认大小窗口图标可见、居中且两笔分离，本次已执行范围验收通过。不新增依赖，不升级版本，不提交、推送或发布。

## 实现与契约

- `I18nFacade` 是不依赖 Minecraft 的 common 契约。`I18nBinding` 为普通脚本和诊断会话提供相同的参数转换与只读代理；版本树 `I18nJS` 调用当前客户端 Minecraft 翻译实现。NeoForge 与 Fabric 均经原有 Bindings Point 在 CLIENT 注册，其余 Script Type 不注册。
- 接口为 `I18n.translate(key, ...args): string`，键为完整翻译键，参数为字符串、数值、布尔值或 null。沿用 Minecraft 缺失键及格式语义，不缓存翻译结果。已存储的字符串不会随语言变化自动更新；SERVER 仍使用接收方解析的 `Text.translatable`。该 Binding 不增加其他节点的 JSX 支持。
- 仪表盘为标准 ESM，显式导入 `UI` 并默认导出工厂；现有编译器以自动 runtime 模式生成 JSX 导入。`BuiltinUiModules` 仅向 Graal 原生 ESM 提供两个固定内存资源，不读取宿主文件或用户模块。规范 Node JSX runtime 的注册在 ESM adapter 的局部对象内捕获，未安装全局 UI、classic JSX 工厂或 Node 注册函数。已有真实文件的非法导入回归验证拒绝读取，失败后会话仍可执行。
- 诊断会话继续由同一个 root／Engine 拥有，HostAccess 配置保持与普通上下文一致；关闭、guest 回调延迟释放、跨线程停机、CLIENT 重载成功／失败隔离和原生失败回退机制保留。操作桥删除专用翻译入口，保留复制、焦点和源码定位。
- 公开 JS 代理消费 rest 参数，Java facade 接收归一化数组；声明通过已有 TypeDocs Point 发布 CLIENT 专用函数形状，再由实际 Probe 后端生成和 TypeScript 消费。新增测试发现 Binding 声明生成器把内联类型表达式当作别名名称，现只为单个合法标识符生成转发别名，既有具名别名保留。没有修改全局 Java varargs 投影，也没有手改生成声明或 golden。
- `NEKO-4030` 表示无效本地化调用或对只读 Binding 的写入，双语错误码表同步。英文内嵌手册及用法片段、双语 JSX wiki 同步；类型链新增 `test:probe-i18n`，覆盖正确调用、键类型、返回类型和对象参数拒绝。

## 已完成自动验证

- `common/build/i18n-builtin-modules-initial-focused.log`：初轮工厂／原生界面及 root 生命周期验证通过。后续聚焦结果 `common/build/i18n-builtin-modules-focused-final.log` 覆盖五节点实际注册与原生翻译、common 参数与原生导入、两个支持节点的真实宿主界面；全部通过。早期失败暴露只读代理写入语义和声明投影不符，均已修正；最终完整门禁涵盖后续声明生成器修正。
- `./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain`：3 分 36 秒通过。日志 `common/build/i18n-builtin-modules-full-build.log`。common 2745 项、4 跳过；1.21.1 为 474 项、33 跳过；两个 NeoForge 26.x 各 840 项、85 跳过；两个 Fabric 各 439 项、39 跳过，全部零失败／错误。保留 Java 21 common 测试工具链，未刷新 golden。
- 四类 CI gate 通过，沿用未变动的五节点 source-root 探针证据；报告 `common/build/i18n-builtin-modules-ci-gates.json`。Wiki 30 双语主题零问题、工具 8 项通过，`git diff --check` 通过。
- `npm.cmd run test:probe-types`：完整类型链通过，包含新 `test:probe-i18n` 的真实生成声明和正／负类型消费者。日志 `common/build/i18n-builtin-modules-probe-types.log`。既有 golden 未变化，新增 Binding 通过现有 TypeDocs／Probe 流程生成声明，没有手工补写生成文件。
- `python common/build/i18n-builtin-modules-audit.py`：8 个 JAR 各 36 项资料与规范源／manifest 一致，7 个非 CLI 制品含最新 TSX；公共会话／导入／本地化／声明生成器及五节点注册与本地适配类和编译输出一致。报告 `common/build/i18n-builtin-modules-artifact-audit.json`。UI SHA-256 为 `135a2cc8ecb01a49f68276cea4c3244841922a71e6e29cf20bdc1d1a6196d1d7`，bundleId 为 `141dc30ce90c198ceb06805e9d8ed5a0a4dffe63a5f3faafa6b94ccb9b9bc807`。

## 实机状态

已按既有授权正常保存退出旧 PID 35656，日志确认所有维度保存完成并正常停止。物理 Esc 中断了上一轮电脑操作，助手停止该轮操作；继续后核对进程确已退出，没有因观察中断重复启动。退出时实际配置为 GUI 缩放 5、语言 zh_cn；安装保留这一当前设置，没有覆盖回此前的缩放 3。

校验原安装哈希、备份后替换为新 JAR，SHA-256 为 `c315b3fa141d04be8f8c31dd7dffa3ac986bb1c77f02720e688d2abb0636805b`。新 PID 17580、MCP 9876；截图确认主菜单最大化。启动日志 `common/build/i18n-builtin-modules-launch.log`，证据目录为隔离实例 `evidence/i18n-builtin-modules-20261105/`，旧仪表盘版本的验收与备份保留。

临时 CLIENT 日志脚本 `acceptance/__i18n_binding_acceptance_20261105.js` 不注册事件、不打开界面、不主动制造错误。实际日志从资源加载前的 `Done | nekojs.gui.dashboard.totals` 变为加载后的 **`完成 | 3 条错误 · 累计 7 次`**，证明普通 CLIENT 脚本的同名 Binding 能读取当前资源翻译并替换两个数值参数。已将原日志证据保存为证据目录的 `public-i18n-live.log`，校验并备份临时脚本后移除，确认自动加载路径已不存在；脚本只打印结果，没有需等待用户操作才能释放的订阅。日志结果不能证明维护者已确认 UI 观感；已通过选项工具交给维护者执行正常重载、中文 UI／空状态／搜索输入及 Esc 关闭重开，结论待回复。

未运行其余四节点实机、完整语言切换 UI 流程、GPU 性能或实机泄漏测量；CPU 跨版本测试、日志结果和既有版本的人工结论不扩大到这些范围。

## 感叹号视觉居中收尾

维护者反馈“界面没问题，但左上角感叹号歪了一点，没居中”。实机截图确认字体笔画略偏左，外框位置正常。图标原先居中的对象是字体布局框，实际笔画受字形留白和缩放取整影响；本轮仅将装饰感叹号改为同色的竖线和点，以 JSX 几何容器统一居中，保留外框尺寸、颜色和布局。没有修改全局文字定位、本地化契约或原生绘制代码。

当前 CPU 绘制夹具记录文字起点，无法锁定实际字体笔画的视觉中心；此可逆装饰调整不增加镜像布局的测试，以真实截图和维护者反馈作为视觉回归信号。既有工厂／原生宿主回归及完整构建验证 TSX 和生命周期路径。新版本构建、安装及图标观感结论待完成；其余界面反馈按上述实际原话记录，不扩大为图标已通过。

首轮几何图标版本完整构建 7 分 17 秒通过、8 个制品和四类 CI gate 核对通过后已安装（JAR `aea13fae10c10b666c8c38d409822b6fb7c34d08ffe00ea86a5e5a9a99bbb473`，UI `11dec9137d538d8ebdf609d6fc09f5bc171b923000d70d7e79fc02591231c52c`，PID 37068）。随后维护者反馈“小窗口下看不到这个感叹号了”，截图确认外框可见、内部两笔消失。此版本未通过最终视觉验收，不作为完成结论。

新增真实规范 TSX／原生宿主绘制回归：在 256×144 逻辑视口统计可见图标笔画，修复前实际为 0 而预期为 2，红例见 `common/build/i18n-icon-small-red.log`。原因是两笔按设计单位缩小后不足一个逻辑像素，被原生一致的最近像素边界投影为零宽；不是字体或缺失翻译。修订使用已有 logical 坐标、百分比尺寸和最小 1 逻辑像素约束，同时约束中间留白，不改变全局绘制／裁剪／命中取整契约。

`NativeBuiltinErrorDashboardTest.iconStrokesStayVisibleSeparatedAndCenteredInSmallViewports` 通过真实填充命令检查 256×144、384×216、854×499、1280×720 的两笔均可见、分离且在整数像素容差内居中；不是仅检查样式属性。两个支持节点的该套回归各 4 项零失败／错误／跳过，日志 `common/build/i18n-icon-small-focused.log`。最终完整构建、安装与实机观感确认仍待完成。

最小像素修订的最终验证：前述完整命令 3 分 8 秒通过，日志 `common/build/i18n-icon-small-full-build.log`。common 2745 项、4 跳过；1.21.1 为 474 项、33 跳过；两个支持节点各 841 项、85 跳过；两个 Fabric 各 439 项、39 跳过，全部零失败／错误。新增回归在两个支持节点均实际执行。四类 CI gate 与 8 个制品核对通过，报告分别为 `common/build/i18n-icon-small-ci-gates.json`、`common/build/i18n-icon-small-artifact-audit.json`。文档 bundle 与公开类型未改，沿用此前完整 Probe 类型链通过结果，本次不重复类型链或 wiki 工具测试；`git diff --check` 通过。

通过 MCP 正常保存世界并退出 PID 37068，校验旧安装哈希、备份后安装新 JAR `18e4a123262d72e29fbd02d4bac0242a3f02115aac83301e262f31e10ad194d7`。UI SHA-256 为 `0cf097bfb47d06067e937a7b8a9fc9fc09a91147ebdd0b4ef0e096757535d9a8`；规范 `import` 和 I18n 契约保留，临时脚本仍不存在。新 PID 9200、MCP 9876，启动日志 `common/build/i18n-icon-small-launch.log`。截图确认新主菜单已最大化，GUI 缩放 5 与 zh_cn 保留。

维护者通过选项反馈：**“大小窗口都可见且居中，竖线和点分开，本次验收通过”**。结合此前“界面没问题”的本地化与操作反馈，本次 NeoForge 26.1.2、GUI 缩放 5、中文环境的已执行范围验收通过。最终核对安装 JAR／规范 TSX 与审计哈希一致、四类 CI gate 全通过、临时脚本原路径不存在；验收 JSON 状态为 `MAINTAINER_ACCEPTANCE_PASSED_EXECUTED_SCOPE`，待办为空。其余四节点实机、完整 UI 语言切换流程、GPU 性能和实机泄漏测量仍未运行。
