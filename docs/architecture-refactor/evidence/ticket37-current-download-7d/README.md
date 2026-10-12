# 当前 7d 官方 JAR：部分脚本包传输期间的重载屏障

范围是 NeoForge 26.2 的真实配置阶段脚本包 bundle，不代表原版资源包 HTTP 下载或其他节点。源码 `7d77308bfa400721304b20940ada91092180a1ed`，官方 JAR SHA256 `2a64c34ca2d7abc3e8b2cd6402b17f204b3c10b4d15fcd1c2ebc917b3dd9aff2`；四次全新专属环境各四份 JAR，每份 1592 个 Neko 类与正式编译输出逐字节一致。没有生产源码、签名/路径/信任检查、协议、公开接口或正式性能门槛变更。

第四次窗口通过。真实 C/D 先获得动态第 2 代配置追赶；D 的 `nekojs:pack_bundle` 原帧共 791 bytes，代理仅发送前 64 bytes 后暂缓其余部分。此时 D 没有 cached manifest，也没有执行远端脚本。SERVER 重载第 3 代停在 AWAITING_ACK，只收到 C 的一份真实 ACK，activatedGeneration 仍为 2，该代没有 COMMIT。放行原帧剩余字节后，D 正常完成签名校验、落盘重扫、公钥 pinning、远端 CLIENT 脚本执行；第 3 代两份真实 ACK、两份 activation-report、COMMITTED 均回读到，D 实际完成配置并登录，give `ticket37_live:window_item` 成功。C/D 均有真实 `PACK-DOWNLOAD-PROOF executed=true` 和 Activated 1 remote script pack 日志，缓存内容与服务端完整签名 fixture 对应。

包由临时 Java helper 调用生产 `PackSignaturePayload.build`、`PackHasher`、`PackSignatureVerifier` 生成并验证 Ed25519 签名。私钥只在 helper JVM 内存中，没有持久化。专属 server/C/D 的 mode 为 all；默认 `allowUnsigned=false`、allowReflection/allowAsm/allowThreads 均保留。使用生产 `PackSyncTrustStore.trustServer` 对仅解析到 127.0.0.1 的实际归一化本地主机名建立明确测试信任，连接收到的签名公钥由实际管线首次绑定；没有通配信任、伪造 ACK 或放宽安全检查。

前三次失败均完整保留：

1. 原始签名 fixture 只有一个脚本。服务端启动后 WorkspaceGenerator 在签名内容目录新增 `client_scripts/jsconfig.json`，真实验签拒绝、C 未登录。生产验签器对完整启动后集合拒绝，对只排除新增文件的原始集合通过；诊断没有删除实际文件。orchestrator 退出 1。**生产生成器会改变这种预签名且缺少 IDE 配置的包，问题已复现、尚未修复。** 第二次将同一实际生成的配置原字节纳入完整 fixture 再签名，未手改输出或排除哈希内容。
2. 完整签名通过，但测试信任输入是 IP:port；实际 `normalizeRemoteAddress` 取 hostname、不含端口，客户端按既有规则提示信任 `kubernetes.docker.internal` 并拒绝。orchestrator 退出 1。第三次只修正专属 fixture 信任输入，不修改归一化规则或安全门禁。
3. C 实际验签并执行远端包后，代理把压缩候选当作可选 payload 结构检查，抛 Invalid framed VarInt 并关闭 C。D 收到部分真实 bundle，但验收缺少 C ACK，证明脚本退出 1，正常关闭后代理等待上限也到期。第四次仅让这种可选内部检查忽略无法解释的候选，真正外层帧长度解析仍严格，转发字节、64-byte 前缀、8s 上限及状态判定均不变。

早期代理以通道名子串匹配，会把协商帧中的通道列表也记作 bundle；第二次按 custom payload 真实通道字段识别。原始观察显示这些非 bundle 帧实际是 `neoforge:network` / `minecraft:register` / `minecraft:unregister`，均保持转发。冻结旧 helper 和日志，不把最初分类当实际下载窗口。根据实际配置顺序（STATE_SYNC 先于 bundle），第三次在启动 C/D 前预先声明屏障判定：下载未完需保留已激活代次并等待 D ACK，放行后两端提交。前两次的“C 独自先提交”证明脚本均未执行到，不移用其假设。

十个 JVM（前两次各 server/C，后两次各 server/C/D）直接退出均为 0；客户端自己的 tick 调用 `Minecraft.stop()`，服务端正常 RCON stop，没有强杀/hs_err。第四次三个 wrapper/proxy/证明作业与 orchestrator 全部 0，归档时 25995–25998 均空闲；代理关闭套接字的原始 OSError 保留。没有 HTTP bridge、跨进程 GLFW 激活或键鼠操作，也没有声称使用另一个 Windows 桌面。未重复完整构建矩阵，沿用同源码的 [正式构建证据](../ticket37-node-assert-repair/README.md)。

执行 helper 和证据冻结在 `current-download-evidence.zip`，248 个条目、586874 bytes，SHA256 `f4c49ab5b4f6dcda6fb7f6a1fc7ef8a52e0d5f1b3e4c85c98ecde7798b593f80`。入口为 `prepare-current-download-7d-attempt4.py`、Java `OwnedSignedDownloadPackNormalizedTrust`、server/C runner、C proxy、`orchestrate-current-download-7d-attempt4.py`；后者启动 D 代理/证明/客户端并正常结束本轮进程。保存四轮日志/fixtures/真实代理帧关联/RCON/退出/信任公钥元数据以及签名集合诊断；密码、server.properties、world、私钥、启动参数文件、制品二进制和原始 JFR/hs_err 不入库。四份实际 RCON 密码扫描通过。最初 Python cryptography 探测不可用，未运行该实现或安装依赖；实际签名完全使用 JDK 与现有生产代码。

独立命令 `python docs/architecture-refactor/evidence/ticket37-current-download-7d/verify.py` PASS：ZIP 每条目、三次失败、生产签名集合诊断、原始等待/提交状态、两端 ACK/report、真实脚本执行/公钥/缓存、部分帧转发及正常退出均核对。这个 PASS 只针对上述有限窗口；原版资源包 HTTP 下载、首帧像素、其他客户端节点、剩余领域/磁盘故障、完整 IDE/Pyright、正式重载性能、预签名包的启动写入问题和真实维护者验收仍待完成。总体 NOT_COMPLETE，维护者 NOT_RECORDED，自动续跑 PAUSED，权限字段问题 UNRESOLVED；A22/A28 与发布/回滚边界不变，没有发布授权。
