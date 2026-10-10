# 名称闭包候选：新增 Python 字段冲突仍失败

固定基线 e48ea68cafbde0007ac7271d99615c146199853b，加 12 生产文件补丁 `b306e972b1225a42e9da3185438fd037668ebf73d233433deaf83c8e74dd3d20`。状态 **NOT ACCEPTED**；后续字段冲突修复不得套用本包源码/制品 SHA。

选择器对真实公开方法求有限单调名称闭包，修复嵌套 getter 名称的顺序遗漏；真实 setter 写入与选择元数据有 RED→GREEN。严格 caller 又暴露 getter alias 与真实 getter 方法名重复：真实 9 条 TS 诊断、两项生成测试 RED 保留。两种渲染器让别名避让实际调用名称；Python 控制保留 String 可写属性与三个实际 getter 调用。初始 Python 夹具引用不存在的 helper 的编译失败也保留，不能当产品故障。

| 检查 | 结果 |
|---|---|
| 完整 common/check、隔离、processor、guard 与五节点 build | PASS：800 XML、4719 测试、271 跳过、零失败；106 任务、2m30s、直接退出 0 |
| 三个相关完整测试类 | PASS：45 测试；全部前序 RED 保留 |
| canonical regenerate、npm、平台门禁、严格 TS caller | PASS |
| 严格 Python 正/负 caller | 正向 0 错误、1 stub 来源警告；负向 1 预期错误、相同警告 |
| 五份精确新 JAR 实机注册/生成/放置/重载 | 支持子集 PASS；旧版 8 类反射遗漏仍 PARTIAL |
| 两个 26.x NeoForge 效果前后施加 | PASS |
| 全部 Python AST / TS 解析 | PASS：1600 / 1675 文件、零语法错误 |
| 完整默认 includes 严格 TSC / 项目 Pyright | FAIL，退出分别 2 / 1；includes、安全和错误门禁保留 |
| 最新正式性能、客户端与视觉 | NOT RUN；历史失败保留 |
| 真实维护者结论 | NOT RECORDED |

| 节点 | TSC SERVER / STARTUP | Pyright 错误 | 对 2d0b 新增 Python 身份 |
|---|---:|---:|---:|
| 1.21.1 | 768 / 769 | 23 | 2 |
| 26.1.2 | 942 / 943 | 19 | 1 |
| 26.2.0 | 827 / 828 | 18 | 0 |
| 26.1.2-fabric | 1306 / 1307 | 24 | 1 |
| 26.2.0-fabric | 1046 / 1047 | 23 | 0 |

TS 身份相对 2d0b 没有增减。Python 新错误是 isChickenJockey（旧版、NF26.1.2、Fabric26.1.2）和 isClientSide（旧版）字段与 getter 同名重复，不是基线问题。两语言生成路径没有删除。它们由新 accessor 调用补发错误地使用包含字段的宽冲突集合触发，须另建回归、分别计算别名冲突与访问器调用保留条件；不得删除真实成员、加 Any 或忽略 Pyright 错误。

Standards/Spec 最后源码复审各剩余 0 项，但该结论在新增实机诊断捕获之前，不覆盖上述运行结果，更不是整体/维护者/发布通过。GetterNamedField 的动态读取与成员调用有既有静态表示限制；后续窄修复不能声称解决全部字段/方法双行为。

Fabric 只复制前一已停止专属环境的 `.fabric/server` 原版服务端/启动器缓存并逐项记录 SHA，不复制 processedMods、账户或世界；本次 NekoJS JAR 和五个世界重新准备。PID 22236/30208/32312/29904/28244 均 RCON 正常关闭、直接退出 0；十个端口空闲，无用户进程停止。

inherited-closure-candidate.zip：3955 条目、12957639 字节，SHA-256 `8bdc3f80a8211bba492163a19f1086332323161354b4b7f06f9b576d3bde59c7`。完整 XML ZIP SHA `d5f0c0f26b76d35c7d5ac3edaf0a0d18ec86079161b87d8c58bb6c2ebb00384a`。所有原始失败/成功日志、源码/基线、真实生成树、配置、XML、制品 SHA 和逐诊断身份比较保留；压缩包逐项读回、哈希、实际专属 RCON 密码扫描通过。不含凭据、world、账户、库/JAR 或原始 JFR。

复现完整构建使用前一 selective 包 README 中同一七项目命令；最小严格 caller 使用 inherited-self-comparable-boundaries-tsconfig.json。关闭中断修复应与 Probe 分开提交。整体票据、性能、间歇文件系统失败、native 崩溃/首帧、领域/窗口、跨节点视觉、维护者和发布回滚仍未完成；自动续跑暂停，没有发布授权。
