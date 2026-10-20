# Fabric 客户端准备与观察器失败记录

`4c2fd226` 的 26.2 v2 尝试中，生产 classes、四份隔离 Java 源码和签名 fixture 均编译/准备成功。独立测试服真实启动并写入 ready 记录；客户端在 Loom `downloadAssets` 阶段失败，未启动游戏，没有资源或客户端通过结论。finally 正常 RCON stop，服务端停止回调存在、launcher exit 0，无强制终止。

Loom 离线缓存没有对应的 `26.2-32` 资源索引与完整对象。本机 NeoForge 缓存可复用大部分对象；后续只按已锁定版本清单补齐缺少的内容，检查长度与 SHA-1，不修改脚本或资源含义，不关闭下载校验。原失败保留，不在同一目录重跑。

归档收录原始准备/运行脚本、编译输出、生产类摘要、公开签名元信息、观察器 JAR、启动失败及正常停服日志。私钥、server.properties、trust-store、环境和存档均未收录。独立只读复核：`python -X utf8 docs/architecture-refactor/evidence/2026-10-20-fabric-client-preparation/verify.py`。

v3 已补齐并校验资源缓存，真实客户端 PID 25452 启动。initial、world-a、f3-t-world-a 三阶段通过；退出 A 后文件赢家已为 GLOBAL，但语言优先级断言失败，整轮保持 `accepted=false`、remote 未执行。日志显示断言紧接在新的原版 ResourceManager 开始重载及字体准备之后，旧观察器仅等待 marker 加五个 tick，未等待异步 listener apply/loading overlay 结束。后续观察器将同时等文件、语言赢家及 overlay 消失，原覆盖、撤回与模型断言保留；若语言最终仍不更新，等待仍失败，不强制额外 reload。另将客户端 fixture simulationDistance 从非法的 2 改为该版本最低合法值 5，不改变服务端或正式性能设置。

v3 的源码、结果、资源记录、完整日志和原生物品栏截图在独立的 `v3-evidence.zip`，没有覆盖 v2。主代理实际查看截图：自定义物品在物品栏和手中呈钻石贴图，未见缺失模型棋盘纹理；这只覆盖 A 的一张截图，不补签 B/返回 A 或维护者接受。客户端 GameTest 失败退出，外部服务端仍正常停服，无强制终止。

v4（`90225df3`）的全部八个本地阶段已通过：初次加载、A、F3+T、退出 A、B、退出 B、返回同一 A、再次退出，均为同一客户端 PID 13272。主代理查看三张真实物品栏截图，均正常显示自定义物品的钻石贴图；视觉记录与原始 `screenshotsReviewed=false` 分开保存，没有代签人类接受。远端首次连接正确显示未信任拒绝，但观察器预期字面 `127.0.0.1`，实际生产身份为 `kubernetes.docker.internal`，整轮失败且 remote 未通过。外部服务端正常退出，无强制终止。

2026-10-21 后续修正仅在观察器通过公开 configuration INIT 的 PacketContext 获取实际远端 socket，先核对 loopback 与 owned 端口，再复用生产 `normalizeRemoteAddress` 取身份并匹配未信任提示、授权此身份。没有从任意拒绝文本提取待信任域名，也没有改生产地址、信任规则或 wire。v4 的结果、八阶段记录、三截图及失败原因保存在 `v4-evidence.zip`，保持失败状态。
