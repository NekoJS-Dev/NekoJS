# TypeScript 跨包同名父类诊断

测试提交 `6fdf3c734408a65ef4534ed7343b050ace3e8078`，基线 `a2f7f9facf71fb4b82bd29e0a9ad848f6faad35e`。只新增两份真实 Java fixture 和诊断测试；生产代码仍为 `7b724a50f51367ad43f5903b1e7240e8e69bad10`，共享 import allocator **未修复**。不删除父类、继承成员、manual 或泛型，也不扩张 Any 或安全权限。

五个已有精确安装 JAR 的完整默认严格 TS 报告均保留：每个 server/startup 项目有一条 `wrapper.item.PersistentDataJS` 的 TS2506。真实不同包的父/子类在生成模块内均使用 `$PersistentDataJS`，子类声明遮蔽了父类 import。此包冻结各节点完整 item/pdata 模块、原始报告与配置；实际 JAR hash 和 renderer class 字节重新核对，并链接 [原实机验收](../ticket37-ts-constructor-member-repair/README.md)。这里没有重新启动测试服，不将既有报告当成新修复验收。

真实 `TypeReflector` 与 `IndexFileGenerator` 生成两个完整模块：父类 `PersistentData<T>` 和另一包子类 `PersistentData extends ...PersistentData<String>`。IR 的真实泛型父类、不同 FQN 和 import 集合都正确；生产 HostAccess/ClassFilter 下，继承方法、接收父/子实例的父类型参数和 `instanceof Base` 通过，Context 正常关闭。这个运行时结论属于 GraalJS，不是 Python 运行时结论。

严格 caller 使用这些完整生成输出，连续两次退出 2，诊断身份相同：TS2506 自继承、TS2315 泛型身份丢失，以及 TS2741/TS2339 继承能力丢失。最小模型删除子类方法及 caller 后，仅继承声明仍有一条 TS2506；不借此认证完整模块。

在对照前排列三个假设：同名遮蔽、泛型父类处理循环、模块声明解析问题。独立模型保持原输出，逐项验证：

| 对照 | 严格 compiler 结果 | 结论 |
| --- | --- | --- |
| 仅去掉 heritage 的泛型实参 | 退出 2，TS2506 仍在 | 此处泛型实参不是自继承原因 |
| 仅给 import 取别名 | 退出 2，原四种错误仍在 | import 与引用必须一致 |
| import 和两处父类引用同步取别名 | 合法 caller 退出 0 | 原模块形式无需改变；支持同名遮蔽原因 |
| 同一同步别名模型的非法 caller | 退出 2，三条类型错误 | 错误构造参数、错误泛型父类参数、错误继承返回类型仍拒绝 |

同步模型只改 import 名称、heritage 和方法参数的父类引用三处，其余字节、子类公开名、父类模块、caller 与严格配置保持。模型副本是诊断实验，未覆盖或手改真实生成输出；模型通过不等于生产修复。

`./gradlew.bat :common:check :common-api-processor:test guardLint`、三个 NeoForge `nbtSmokeTest` 和全部五节点 `build` 直接退出 0：4,749 测试、271 跳过、零失败，803 XML，109 任务，1m42s。XML ZIP SHA256 `ea09927bbb7fd0bc486f2a5350f5de044e29808f60a36e68372144454cbf4b30`。定向两个事实测试、`npm run test:probe-types` 和构建完成后的 `tools/nekojs-ci-gates.py all` 均退出 0。独立 Standards 0 hard/0 heuristic，Spec 0 findings；审查范围为诊断测试，未认证生产修复。

[manifest.json](manifest.json) 与 [证据 ZIP](same-name-heritage.zip) 含源码 patch、完整模块、原始 RED、全部模型/负例、完成日志和 XML。ZIP 共 86 项、1,095,916 bytes，SHA256 `db71d2e81fffc7c6b38d97c494a69486fe2c17a9e7a35e193f0f492c1739c571`。[独立验证](archive-verification.json) 核对全部 hash/读回、源码 patch、三处精确模型差异、重复 RED、严格配置、运行时事实、五节点原有错误和直接退出记录。递归实际凭据扫描通过，排除 properties/world/JAR/raw JFR、自己的活动打包控制台及无关数据。

下一步在共享模块生成出口保留 FQN 身份分配 import 名称，并让类型槽及所有引用一致消费；还须覆盖同模块公开名、多外包同名、输入别名、编辑与手写声明路径，不能只做全文短名替换。完整 TS/Python IDE 仍 FAIL；同名 field/method、名称确定性、文件系统根因、native crash、fireResistant、剩余领域、多人独立下载窗口、首帧像素/跨节点视觉、维护者/发布/回滚尚未完成。此测试提交没有新生产改动，未重复安装或正式性能；既有精确 7b 性能 PASS 独立保留。维护者 NOT RECORDED、总体 NOT COMPLETE、自动续跑 PAUSED、权限字段问题 UNRESOLVED，无发布授权。
