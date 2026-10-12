# 当前 d109 客户端画面与退出诊断

2026-10-12，工作区已经有社区修复 `c6455e04` 与物品组件/资源提交 `d10912ebbf24d9d5df75a908dc7a5529da09979a`。本轮保留这些既有改动，用当前官方 NeoForge26.2 JAR SHA256 `fde6c16ba1b6fbca43dfc87aff4bf9709c1925bcb2d72c307f2e73346922f092` 新建专属 server/A/B；三份安装 JAR 一致，1592个可对应当前 common/node 编译目录的类条目逐份一致。这绑定本轮实际制品，不把此前93源码的矩阵/IDE/性能结论转为当前源码通过。本轮没有改生产代码、公开接口或安全配置。

使用 NeoForge26.2.0.75/JDK25.0.2、缓存依赖 SHA1 检查、新专属世界和合成离线身份。A 带原有 Minecraft MCP0.4.2 JAR，B 不带它；其余模组、STARTUP和CLIENT逻辑相同，仅专属信号路径和身份不同。两个客户端依次运行，不是并发窗口测试。实际计划 `LIFECYCLE-PLAN.md` 在 ZIP 中保存；继承的 PLAN.md 是旧 fixture 模板，实际 source/JAR/对照配置以 preparation、runner 与生命周期计划为准。

## 实际画面通过：限 NeoForge26.2

Computer Use 新选实际 Minecraft 窗口，观察清晰/模糊世界，逐步发送 F2/F3+T；画面变化后重新观察。游戏原生 F2 保存八张不同的 PNG，游戏 stdout 的截图文件名/时间与完整原图对应。重载前后状态分别为声明 generation1/2，第2代第一个 HUD 回调实际读到声明与 active 状态。切回 clear 后读到 `current=null/active=false`，实际条纹墙恢复清晰。

| 客户端 | 重载前 | 重载后 |
| --- | --- | --- |
| A，带桥接器 | [清晰](a-clear.png)、[模糊](a-blur.png) | [模糊](a-reload-blur.png)、[清晰](a-reload-clear.png) |
| B，无桥接器 | [清晰](b-clear.png)、[模糊](b-blur.png) | [模糊](b-reload-blur.png)、[清晰](b-reload-clear.png) |

主代理逐张观察实际原图，图像字节不同只是辅助检查，不能独立证明模糊效果。B 重载后是夜间，光照与早期图不同；条纹边缘、地面纹理实际清晰度变化仍可见，未把亮度变化当成 blur 的证据。八张展示图与 ZIP 原图字节一致，API 另生成的三张图片也保留，但验收不依赖桥接器截图缓存/刷新机制。桥接器截图为何曾重复仍未建立因果结论，原93失败证据保留。

本次闭合两个顺序客户端的原生截图/实际资源重载/clear状态及画面。F2 发生在重载后观察阶段，**不认证首次像素帧**；任意 inline ID、候选拒绝恢复的当前源码像素、多节点客户端及多人包下载窗口没有由这轮闭合。此 fixture 未配置所有注册物品/方块模型，缺失模型警告保留；不替代独立 Issue36 的资源功能验收。

## 退出挂起真实复现与最小化

用现有 tick owner 的专属信号调用原生 `Minecraft.stop()`，相同关闭路径通过 ZIP 中的 `probe-client-close-d109.py` 做十秒断言。A 实际记录 stop/loader close 后没有 direct exit，检查 FAIL；jcmd 观察 `DestroyJavaVM` 和八个非 daemon `pool-14-thread-*`。窗口已消失，核对专属 PID10652/executable/argfile 后只清理该 JVM，直接退出4294967295。B 用同样 tick-owner stop 路径约0.906秒正常退出0。服务器正常 RCON stop/direct0。A 的强制退出没有改记为正常退出。

随后最小化到已安装桥接器里的真实 `McpHttpServer` 类：不启动 Minecraft、Graal或NekoJS，handler为null，只启动 HTTP 服务、访问一次 `/api/status`（校验实际 owned PID）并调用该类原有 stop。没有复制 HTTP 实现，也没有伪造响应。这个独立 Java 测试在3秒内能稳定区分是否退出；原始执行源码快照和扩展对照后的源码都保留。

| 实际最小测试 | 结果 |
| --- | --- |
| 状态请求后，只调用原有 bridge.stop | stop已返回，但 executorShutdown=false/terminated=false；3.016秒仍未退出，专属子进程被清理，FAIL |
| 同一请求和stop，仅额外关闭取得的原有 executor | shutdown=true/terminated=true；约0.266秒直接退出0，PASS |
| 无请求，其余start/stop保持 | 没有创建请求 worker；约0.313秒直接退出0，PASS |

在关闭 executor 的对照之前，主会话列出线程池未关闭、HTTP分发线程残留、桥接器其他全局资源三个预测。实际结果支持第一个：只关闭该 executor 即可退出，另外两个没有留下能维持该最小进程存活的资源。安装字节码也显示 start 使用 `Executors.newFixedThreadPool(8)` / `setExecutor`，stop关闭HTTP/SSE却未关闭 executor；最小片段、线程记录、完整stdout/stderr和直接结果保留。这个工具缺陷足以独立造成同类 JVM 退出挂起，也解释A/B对照；没有逐个追踪原93线程对象的创建，所以不把每个旧现场线程都作对象级归因。

**这确认的是桥接器缺陷与测试控制里的修复效果，桥接器产品尚未修复。**安装 JAR 没有改写，没有注册生产 shutdown hook，没有在 NekoJS 内接管第三方线程池，没有把 daemon 化当成资源释放。以后可用无桥接器的原生F2与专属游戏fixture继续NekoJS验收。没有提交第三方Issue、发布修改桥接器、改变协议或工具权限字段。本轮并未把带桥接器客户端退出改为PASS。

## 原生崩溃与其余限制

两轮当前顺序运行没有生成 hs_err，也没有复现历史 `glfw.dll+0x10fa1`。这不证明该崩溃已修复；没有执行旧的多窗口并发激活情形，根因仍 NOT ESTABLISHED，需要独立的稳定重现过程。桥接器HTTP退出问题与GLFW原生崩溃是不同现象，不能把前者结论套到后者。

当前新源码的完整 IDE 和正式启动/重载性能本轮没有重跑；此前93正式reload FAIL保留，但不能作为d109实测分数。新增组件/生成模型和社区修复的独立验证记录保留，本轮不替它们增补未经执行的结论。历史FS/fireResistant、剩余领域、包下载、跨节点/首帧、真实维护者接受、发布/回滚等仍未完成。整体 NOT COMPLETE，自动续跑 PAUSED，工具权限字段问题 UNRESOLVED；A22/A28精确授权边界不变。

## 原始证据与验证

[原始包](lifecycle-evidence.zip)93 entries、2565726 bytes、SHA256 `fd1c68169d83905c3f525e3f5c671bd5f02d5b0263c45768461463ba590c330b`。[清单](manifest.json)、[运行结论](runtime-summary.json)、[独立验证源码](verify.py)、[实际验证结果](archive-verification.json)核对全部hash读回、三JAR/1592编译条目、八张原生截图、原始FAIL/两项最小控制PASS、精确RCON凭据扫描。server/client与最小测试全部结束，五个专属端口可重新绑定。没有操作无关用户实例。

ZIP包含准备/启动/API/原子信号/关闭探针/最小真实桥接器测试/归档源码、fixture、全部stdout/stderr/游戏日志与线程记录、直接退出及失败检查。排除 properties/凭据、world、账户/launcher参数、制品/库/资源和无关桌面图片。固定目录有防覆写保护，重放需改为新专属目录。用 `python docs/architecture-refactor/evidence/ticket37-client-lifecycle-d109/verify.py` 作独立验证；本地制品/精确凭据检查需要本轮排除在ZIP外的本地文件。没有为本次纯证据提交重跑生产矩阵。
