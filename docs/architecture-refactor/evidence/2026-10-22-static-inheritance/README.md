# 静态继承声明与安装客户端证据

本包区分已测制品 `d91ca974` 与后续源码修复 `ad4d3750`，不判票 28/37 完成，也不代表发布授权。

| 范围 | 实际结果 |
| --- | --- |
| `d91ca974` common/check、处理器、guard、五节点 build | 8m37s；5066 tests / 271 skipped，零失败/错误，含 isolation 和 platform gates |
| 同一候选五节点安装服务器 | 全部 PASS；真实初始/重载、注册/放置、Python、适用节点交易及动态空代物理效果检查；五进程正常 exit0 |
| 默认全部 Pyright | 1609 个 stub，五节点零错误/警告 |
| 十入口默认严格 TSC | **FAIL**；515/516、578/579、569/570、646/647、623/624，共 5867 条；未跳过 library 检查 |
| 与 `99677b0f` 诊断多重集比较 | 移除 594 / 新增 8；新增为现代节点 SectionPos 的 TS2430，原先同一 x/y/z Bean 与方法冲突已产生 TS2416/TS2426；不称为无新增 |
| `ad4d3750` 源码后续检查 | common/check 通过（3m23s，含 isolation）；408 项 Probe / 2 skipped、七组定向严格消费及完整 `npm run test:probe-types` 通过；golden 无变化 |
| 26.1.2 安装 NekoJS JAR 的独立客户端 | 七张世界目标图逐张查看通过；三次原生 debug 资源重载，失败候选后未再次 set 仍保留模糊，随后恢复；两进程正常 exit0 |

源码修复的原因与契约见[实施记录](../../../research/2026-10-21-ticket28-37-closeout.md#静态投影复审修复与候选实测)。静态字段隐藏拆成 class 的值侧与合并 interface 的实例侧；仅在完成真实声明闭包后投影。后续修复使未编辑继承重载服从声明祖先的编辑后 IR，保留子类显式编辑，参数选择器不匹配时保持无操作。内部签名键使用明确的 Java/synthetic 类型。运行时、Python 后端、主扫描、wire 和持久格式未改。

两轴复审曾发现父重载副本绕过父编辑、内部混合 Object 键、重复 setup，以及无匹配参数被误标记。原始失败与两轮修复分开记录。最终固定范围 `dae6ef6d..ad4d3750` 的 Standards 为 0 hard breach / 0 smell，Spec 无剩余源码 finding；这是只读审查，不能替代执行。`ad4d3750` 尚未重新打包为五节点制品、生成完整生产图或跑最终性能/回退，`d91ca974` 的二进制证据不覆盖它。

客户端使用独立 ModDev 2.0.140 开发启动器，source mod 仅为验收 helper；NekoJS 的 ModList 路径、实际 CodeSource、class resource URL 和字节均指向安装 JAR，完整 1635 个 common/node 打包 class 与冻结清单一致。其 NekoJS JAR SHA256 为 `8b7c6179ef00f442b041d95b5c379820144d101cd4479615d5f1cf19d5194e57`。这证明安装生产 JAR 加载，未宣称外部正式安装器、物理键盘/GLFW、显示器 present 或维护者签字。其他两个节点先前的开发客户端证据见[三节点原生记录](../2026-10-21-posteffects-native/README.md)。

七张图的同一墙面 ROI `(230,200)-(380,260)` 避开 HUD；清晰梯度平方均值约 338.4–338.7，模糊约 12.8–12.9。机械结果与主代理实际看图结论分开保存。首次安装客户端尝试在第五帧后因历史 `.js.rejected` 文件名碰撞而中止，判 NOT GREEN；第二次使用新目录、新世界和同一冻结 JAR，以独立文件名完成。另保留最初集合夹具把可构造的惰性 Set 代理误当可调用的失败，以及同 JAR 修正夹具后的成功。不覆盖原始失败。

归档保留两批完整构建 XML、安装运行日志/脚本/class 和源码绑定、完整新生成声明、十入口诊断、Pyright、最小红绿测试、后续 common/check XML、helper 源码与原始 PNG。排除 world、JAR/依赖、server.properties 和启动参数。Python Java host stub 的一致性不外推成完整入口/事件字节一致性；后者仍有内容/顺序差异。

只读校验：`python docs/architecture-refactor/evidence/2026-10-22-static-inheritance/verify.py`。校验器验证归档摘要及逐项摘要，保留完整 TSC 的失败结论；不提取文件、运行游戏或代签验收。
