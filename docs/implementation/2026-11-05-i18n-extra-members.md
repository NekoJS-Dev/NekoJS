# I18n 补充能力

## 目标与方案

在五个支持节点的 CLIENT Binding 和内置诊断会话中统一增加 `has(key)`、`translateWithFallback(key, fallback, ...args)`、只读 `languageCode` 和 `rightToLeft`；保留现有 `translate` 行为。

- `has` 查询当前翻译表，包含 Minecraft 的语言回退；翻译结果恰好等于键仍算存在。
- 已存在的键优先；缺失时按 `Locale.ROOT` 格式化备用原文，不将备用原文再次解析成键。格式错误返回可见的 `Format error: ` 文本。
- 元数据每次读取客户端当前状态，不缓存，不自动重建已显示界面。
- Java 实现留在 Minecraft 源树，common 只包含接口与受控代理。按现有 TypeDocs 和 Probe 流程生成声明。

Script API 为增量扩展，原调用保持有效。Java `I18nFacade` 新增抽象方法，直接实现此接口的消费者需要补齐方法；仓内原生实现和测试替身已同步。没有新增依赖、版本号、配置或事件订阅。

## 验证计划

1. common 代理、内置模块生命周期和真实 Probe 声明消费者。
2. 五节点原生 Binding：注册范围、真实翻译表、备用模板和动态语言元数据。
3. common 完整门禁、五节点构建、Probe 类型链和相关文档检查。
4. 隔离客户端安装与实际运行证据；维护者手动验收另行记录。

当前状态：实现、自动验证、安装、大窗口重启、已执行范围的维护者验收及临时脚本清理已完成。

首次聚焦验证发现并修订：26.2 原生 `I18n` 不再提供 `exists`，因此使用五节点共有的 `Language.getInstance().has`；受限 Graal 会话枚举成员需要 `ProxyArray`，不能返回需要宿主数组权限的 Java 数组。原有翻译路径不变，未新增业务版本分支。

## 已完成的聚焦验证

- `:common:test` 筛选 I18nBindingTest、I18nProbeDeclarationTest、BuiltinUiRuntimeTest、NekoRuntimeRootLifecycleTest：17 项通过。
- 五节点 `I18nBindingsTest`：各 2 项，共 10 项通过。覆盖真实原生适配器、键与翻译同名、备用模板不再查键、占位符、非法格式、语言和方向变化。
- 两个 NeoForge 26.x `NativeBuiltinErrorDashboardTest`：各 4 项，共 8 项通过。
- `npm.cmd run test:probe-i18n`：通过；包含真实生成声明的正向消费和只读属性／无效参数反向检查。
- Wiki strict：30 个双语主题零问题；文档工具测试 8 项通过；`git diff --check` 无空白错误。

聚焦重跑日志：`common/build/i18n-extra-members-focused-retry.log`；测试明细：`common/build/i18n-extra-members-focused-results.json`。构建采用 Zulu 25 daemon，保留 common Java 21 测试工具链，未刷新 golden。

## 完整验证与打包

命令：

```powershell
./gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Zulu/zulu-25' guardLint :common:check :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm.cmd run test:probe-types
python tools/nekojs-ci-gates.py all --out common/build/i18n-extra-members-ci-gates.json
python common/build/i18n-extra-members-audit.py
```

全部通过；构建日志 `common/build/i18n-extra-members-full-build.log`，类型链日志 `common/build/i18n-extra-members-probe-types.log`。

| 测试项目 | 总数 | 失败／错误 | 跳过 |
|---|---:|---:|---:|
| common 完整门禁 | 2746 | 0／0 | 4 |
| NeoForge 1.21.1 | 475 | 0／0 | 33 |
| NeoForge 26.1.2 | 842 | 0／0 | 85 |
| NeoForge 26.2.0 | 842 | 0／0 | 85 |
| Fabric 26.1.2 | 440 | 0／0 | 39 |
| Fabric 26.2.0 | 440 | 0／0 | 39 |

五节点 platform gate 及四类 CI gate 通过。guardLint 扫描 618 个文件、418 个守卫块，零警告。8 个 JAR 的 36 个 AI payload、manifest 和编译类与规范源一致；7 个非 CLI JAR 的内置 UI 也一致。文档 bundleId 为 `7e24eed5d1f88e946880b71212d5715b10cfb76de0a0f0683ad4b50dc0f1d888`。界面 TSX 哈希保持 `0cf097bfb47d06067e937a7b8a9fc9fc09a91147ebdd0b4ef0e096757535d9a8`，本轮未调整布局或图标。

## 安装与维护者验收

隔离目录：`common/build/mcp-live-26.1.2-20261103`。旧实例通过正常保存退出后安装新 JAR，SHA256 为 `8c30de7fc66f40ba6e7f0d08c0242eb8c096bc63f33b43f0bdfe49de43c5fe2d`。中文及 GUI 缩放 5 保留，新实例 PID 33132、MCP 端口 9876。旧 JAR 备份和本轮证据位于该目录的 `evidence/i18n-extra-members-20261106/`。

已截图确认 Minecraft 主菜单为大窗口（逻辑尺寸 1707×1019）。临时脚本 `nekojs/client_scripts/acceptance/__i18n_extra_members_acceptance_20261106.js` 曾放入隔离目录，其 SHA256 为 `5ab24b24cb395e310502e574e77afa4fb4324409721f284c7eb5826869679fd8`；核对备份和哈希后已移除，确认自动加载目录内不再存在此文件。该脚本没有注册事件、定时器或原生资源。

2026-11-06，维护者对进世界、CLIENT 重载、中文报错界面的标题／搜索／空状态、输入、Esc 关闭及命令重开反馈：“重载及界面操作都正常”。记录为上述已执行范围通过，原阻塞已解除。

没有在本轮实际客户端日志中取得临时脚本的 `I18n extra acceptance` 输出，因此不将界面反馈扩大为四项新成员的实机脚本验证。新成员的结果以 common 及五节点原生自动测试为依据。证据 JSON 明确记录此项为 `NOT_OBSERVED`；客户端日志副本保存在同目录 `client-at-acceptance.log`。

其他四节点实机、真实 RTL 语言界面观感和 GPU 性能未测；五节点的语言代码／方向变化已有原生 CPU fixture 覆盖。属性不会自动镜像界面，属于本轮明确边界。
