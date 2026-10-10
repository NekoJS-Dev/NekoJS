# 继承声明与访问器修复：技术检查通过，完整 IDE 仍失败

固定基线 `e48ea68cafbde0007ac7271d99615c146199853b`，12 生产文件补丁 SHA-256 `d4adb0904c6900255c9e45f5e94ae3adf487b8c5eeeaa66c10fd6f842d0f677a`。本包状态 **NOT ACCEPTED**；提交映射另见 DELIVERY.md，不能把后来源码变化套用到本包 JAR。

共享声明选择器保留实际接口/default、同名重载、必要 Object 契约、泛型上下文与上界，并对嵌套 getter 名称求有限闭包。普通祖先方法继续通过继承表达。TS 保留真实 host 泛型、SAM host/回调边界与字段的不同读写类型。Python 用私有 metaclass 表达混合静态/实例调用，分别处理真实成员冲突与别名冲突；已收集、编辑后的祖先字段参与属性投影。最近继承 getter 决定是否已提供相同读写类型，三层协变覆盖不再丢失精确返回类型；祖先静态字段不屏蔽实例属性。

生产 HostAccess、ClassFilter、collector 深度、路径安全、manual 生产器和公共 API 均未放宽或删除。ScriptManager 的实际关闭中断分类修复与 Probe 分开提交：只有关闭已请求且真实 Polyglot 中断时使用 close-preempted；普通脚本异常和 watchdog 分类不变。

| 检查 | 结果 |
|---|---|
| common/check、隔离、processor、guard、三个 NBT smoke、五节点 build | PASS：800 XML、4725 测试、271 跳过、零失败；109 任务、2m、直接退出 0；包含 up-to-date/cache 任务 |
| 五个相关聚焦类 | PASS：80 测试、零失败 |
| canonical golden、npm、平台门禁、严格 TS caller | PASS；本轮仅 Python 追加修复，不手改生成输出 |
| 嵌套 Python 正/负 caller | 正向 0 错误/4 stub 来源警告；负向 1 预期错误/1 警告 |
| 不同读写类型与三层协变 Python caller | 实际错误读取类型已修正，错误写入拒绝；完整严格夹具仍 FAIL：属性覆盖父类变量不符合 Python 继承规则 |
| 精确新 JAR 五节点注册/生成/放置/重载 | 支持子集 PASS；旧版反射遗漏仍 PARTIAL |
| 两个 26.x NeoForge MobEffect 重载前后施加 | PASS |
| 全部 Python AST / TS 解析 | PASS：1600 / 1675 文件，零语法错误 |
| 默认 editor includes 严格 TSC / 全部 stub Pyright | FAIL，分别退出 2 / 1；没有排除或忽略真实错误 |
| 当前源码正式性能、客户端与视觉 | NOT RUN；历史失败保留 |
| 维护者结论、发布 | NOT RECORDED / 未授权发布 |

| 节点 | TSC SERVER / STARTUP | Pyright 错误 | 对 b306 删除 / 新增 Python 诊断 |
|---|---:|---:|---:|
| 1.21.1 | 768 / 769 | 21 | 2 / 0 |
| 26.1.2 | 942 / 943 | 18 | 1 / 0 |
| 26.2.0 | 827 / 828 | 18 | 0 / 0 |
| 26.1.2-fabric | 1306 / 1307 | 23 | 1 / 0 |
| 26.2.0-fabric | 1046 / 1047 | 23 | 0 / 0 |

TS 诊断身份对 b306 没有增减；Python 删除的四条全部是前一候选新增的 isChickenJockey/isClientSide 同名字段冲突。对其他失败候选和7e98的逐文件/代码/消息比较也保留，不能用净数量降低遮盖新错误。两语言生成路径没有删除；旧版八个不可用反射类仍明确记录。

真实 RED、失败夹具及纠正记录均保留：合法 getter/setter 被最早断言误计为重复；第一次相对路径 Gradle launcher 没运行测试，所捕获 XML 是旧结果；绝对路径重试才是真正组合重复 RED。静态字段控制最早错误假设子类 class 可访问祖先静态字段，实际结果为子类 undefined、父类 true，纠正后实例属性生成有独立 RED。三层严格 caller 第一次额外导入未使用 Base，引入验证脚本错误；纠正重试清晰显示精确读取 assignment 错误退出，继承规则错误仍在，错误写入仍被拒绝。所有 source-control finally 恢复 SHA 已核验，控制目录的 green 名称不表示完整编译器通过。

第一次预审矩阵4724/271skip/0fail及其五份 JAR 单独冻结，**从未安装**；不认证后续三层修复。当前实机 PID2264/29764/17492/2424/26128 均 RCON 正常关闭、直接退出0，十端口空闲；没有停止用户进程。Fabric 只复制前一已停止专属环境的原版 `.fabric/server` 缓存并记录 SHA；五个世界和本次 NekoJS JAR 重新准备。

`inherited-accessor-candidate.zip`：4081 条目、14409703 字节，SHA-256 `6d9ed9a1c81e183fc15d0f6fda2901c21adaff300bacecdb8754f55fad76992b`。完整 XML ZIP SHA `37e819dbbfa2efc8677d098307e66ad81660c1b71f4019b4024d4f7d5732820e`。首次归档早于比较任务结束，ZIP 漏收最后 comparison-b306；原4079条目包保留在 ../ticket37-inherited-accessor-incomplete-archive，不是完整交付。等待任务退出后重新生成本4081条目包，逐项读回、全部最终比较及完整 console 的字节一致性、哈希和专属真实 RCON 密码扫描通过，不含凭据、world、账户、库/JAR 或原始 JFR。

复现构建：

```powershell
./gradlew.bat :common:check :common-api-processor:test guardLint :1.21.1:nbtSmokeTest :26.1.2:nbtSmokeTest :26.2.0:nbtSmokeTest :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build --console=plain
npm run test:probe-types
```

严格 caller、实际生成检查、默认 includes 完整 IDE、身份比较、正常关闭和归档脚本在 ZIP 的 runner/control 目录。11 个 canonical golden 包保留原类型，补充真实依赖/继承契约并修正52个 SAM 输入别名；逐项 AST 差异见 golden-audit.json，真实维护者结论未记录。

整体15/16/22/28/34/37未完成，38仍可选非阻塞。完整 IDE、当前源码性能、文件系统间歇失败、native 崩溃/首帧、下载窗口、领域/跨节点视觉及维护者/发布回滚仍开放；自动续跑暂停。下一步诊断剩余 Python 同名跨包导入和继承属性写类型依赖，以及完整 TS manual/泛型/缺失依赖；不能删除真实类型、manual 或添加 Any 绕过。
