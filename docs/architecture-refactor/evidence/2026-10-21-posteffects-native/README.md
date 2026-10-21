# PostEffects 三节点原生渲染补验

日期：2026-10-21。legacy 修复源码 `8798257f`，现代节点生产源码 `dd15186c`。这是本地工程证据，不是整票或发布签收。

三个客户端分别连接独立已安装服务器，30 张原始世界 render target 图片均由主代理逐张查看。测试 helper 仅在隔离开发启动中加载；生产 class 与记录中的候选 JAR 逐项匹配。没有把开发客户端启动称为正式客户端安装 JAR 启动。测试服务器仍为前一阶段的 `dd15186c`；legacy 修复仅客户端所有权/资源重载路径。26.2 客户端和服务器的 NeoForge 小版本不同，实际连接成功，不宣称版本相同。

| 节点 | 客户端 / 服务器 loader | 图片 / 原生 T 处理 | 结果 |
|---|---|---|---|
| 1.21.1 | 21.1.227 / 21.1.227 | 16 / 7 | PASS；invert、clear、拒绝保留、退役、关闭状态及原版链接管 |
| 26.1.2 | 26.1.2.71 / 26.1.2.71 | 7 / 3 | PASS；blur、clear、重载、拒绝保留及恢复 |
| 26.2.0 | 26.2.0.57 / 26.2.0.75 | 7 / 3 | PASS；同上 |

实际流程是 owner tick 执行 set/clear、arm 后由首个可用的 `RenderFrameEvent.Post` 读取主 render target；截图时都有世界、无加载遮罩、owner thread 一致。retained 阶段是稳定后观察，不能称为故障发生瞬间的首帧。已封存对应 vendor 字节码，Post 在世界 render 后、present 前；没有验证显示器 present。

隔离 helper 提供短暂 F3 状态、刷新原生缓存键位并调用 Minecraft 的 T 键处理器，执行真实 debug 资源重载。finally 释放状态，日志逐次记录 begin/end。此项证明原生 KeyboardHandler 分发和资源链，不证明物理按键或 GLFW 回调输入。测试未增加生产权限、Script API 或默认配置。

## 实际故障与修复

`posteffects-121-v2` 最初机械检查为 PASS，但图片复核发现候选拒绝后的画面已变清晰：墙区域与 clear 的平均差 0.1323、与 invert 的差 74.9128。其机械原记录保持不变，独立 `retention-review.json` 明确 FAIL。原因是原生 shader reload 关闭 renderer 持有的旧 PostChain；保留声明 generation 不能复用已经关闭的 GPU 资源。

legacy 现在在 reload 前捕获实际 NekoJS 选择，在新资源准备后通过 owner executor 重建。弱引用只识别实际链，不延长其生命周期；选择 revision、同一世界、仍无其他链等条件取消过时恢复。返回的 future 包含恢复完成，关闭状态保留。`current()` 仅在实际链仍属该选择时返回 NekoJS id；`isActive()` 在没有实际链时返回 false。公开成员、wire、持久数据及 resources-only 能力边界不变。

另一个原生红测 `posteffects-121-native-retire-red` 证明 stale 账本在退役时关闭原版接管的 creeper 链；最终修复只清理仍实际归属 NekoJS 的链。最终 16 张覆盖显式 clear 取消恢复、成功 generation 退役、disabled 状态保留、原版接管及接管后的实际 Adapter 退役。最后一项刻意直接调用生产 Adapter，不冒充完整脚本事务；definition 已消失，renderer 仍 active，前后绿色区域平均差 0.0。

两个独立 Luna xhigh 源码审查分别发现 Standards 2 项、Spec 1 项，均由主代理修复，复审各 0 剩余。源码审查与上述原生执行结论分开记录；未由子代理写代码或运行验收。

## 构建与证据绑定

最终 legacy 命令：`gradlew.bat -g D:/mcmodDemo/NekoJS/.gradle-perf02 -Dorg.gradle.java.home=<JDK25> --offline --no-parallel :1.21.1:build guardLint --console=plain`。结果 PASS，1m08s / 37 tasks，普通 444 tests / 31 skipped、platform gate 3 / 0，零失败/错误；runtime 与 legacy server artifact 门禁通过，guard 380 blocks / 561 files / 0 warnings。common/check 与其他四节点的前一阶段集中证据仍绑定 `dd15186c`，没有冒称重新执行。

最终 legacy JAR SHA256：`4645b499be2ebc5941041920393141f59820dbe7dcfc942142ca572bbc9355e9`，1559 生产 class 字节匹配；modern 两节点各 1630 class。helper 不进入生产 JAR。`source-binding.json` 含各候选、vendor、服务器 mod、helper、脚本摘要；261 的原始 manifest 早于 helper 修正，保留旧记录，并由 post-run binding 明确记录实际编译来源。完整最终生产 patch 包含新增 mixin；早期试验的局部 patch 不代表最终源码。

中英文 global-bindings 已同步并仅 stamp 本主题，仍为 draft/pending；结构检查 26 topics / 0 problems。整库严格文档检查仍失败 10 项（其他五主题的 stale/unreviewed），没有代签其他文档。

`evidence.zip` 封存 528 项，含所有中间试验、失败记录、原图、脚本/helper、日志、字节码、最终 legacy XML 和源码。不含 world、JAR、依赖、server.properties、用户配置或启动参数文件。`manifest.json` 记录每项摘要及 ZIP 摘要。

只读复算：`python docs/architecture-refactor/evidence/2026-10-21-posteffects-native/verify.py`（Python、Pillow、NumPy）。实际 PASS：摘要、30 张画面指标、原生调用数、正常进程退出、源码/fixture 绑定及 447 测试计数。ROI 和分类参考在原图查看后选定，不冒充预注册像素公式。现代清晰梯度约 338、模糊约 12.9；legacy 使用 clear/invert 参考距离和原版绿色区域。

## 未通过尝试与剩余边界

最初 121 和 262 各有 offline prepare 缺缓存而未启动游戏的记录，补齐既定依赖后重跑；261 有 helper 初始化错误及 server.properties 注释解析错误。legacy 中间 11/15 阶段被最终 16 阶段替代，15 阶段不宣称逐图复核。两个实际 legacy 红测保留，旧 runner 的外层 exit0 不覆盖 JSON/图像失败。

262 第二次游戏未进服，由自有 tick 正常退出，结果 NOT GREEN。根据 vendor 源码与后续结果，推断被 GraalMC 旧 logo 属性的开发警告页阻挡，未取得该页截图。第三次仅使用 NeoForge 自带 `-Dneoforge.warnings.logofile.hide=true` 跳过开发模式的 cosmetic deprecation 页面；vendor production 模式本来不显示该页，连接与七阶段通过。没有绕过运行时能力或安全门禁。

尚未运行：legacy 效果重载期间切换世界；当前正式候选的客户端安装 JAR 启动；多人动态注册表 TCP；最终统一候选性能与制品/数据分离回退。Fabric PostEffects 仍明确 unavailable，目录包资源桥的独立证据不等同于此能力。没有维护者整票签收、远端发布、PR 或 push。
