# Fabric 目录资源与签名网络客户端最终验收

测试入口为 [Fabric 客户端验收工具](../../../../tools/acceptance/fabric-directory-client/README.md)，运行源码 `5fec3f89`。生产实现已在 `39d405a6` 收口，之后仅修改隔离测试工具与文档；冻结的每节点 1,569 个生产类摘要与[最终完整构建](../2026-10-20-fabric-final-checks/README.md)逐类相同。运行使用本机 JDK 25.0.2、锁定 Fabric/MC、开发 runClient/runServer，不是发行 JAR 安装验收。

| 节点 | 同一客户端 PID | 自动场景 | 退出与视觉结论 |
| --- | --- | --- | --- |
| 26.2.0-fabric | 23444 | 全部 15 阶段 PASS | CLIENT_STOPPING、客户端与服务端 launcher exit 0；三张截图已由主代理实际查看 |
| 26.1.2-fabric | 324 | 全部 15 阶段 PASS | CLIENT_STOPPING、客户端与服务端 launcher exit 0；三张截图已由主代理实际查看 |

## 原生行为

- 每节点独立创建 A、创建 B、重开同一 A 存档，同一客户端完成八个本地阶段。ROOT/GLOBAL/WORLD 的原生资源栈与文件、语言覆盖/旧语言撤回均检查；修改 GLOBAL assets 后通过真实 F3+T 输入更新。
- `event.item` 的 `parentModel`、`texture` 生成原生模型和 26.x item definition；实际 ModelManager 返回有效模型，纹理引用可读取。A、B、返回 A 三张真实物品栏图片显示自定义物品的钻石贴图，未见缺失模型棋盘纹理。
- 独立服务端 JVM 使用真实 loopback TCP；首次确认生产规范化身份未信任并匹配拒绝原因，再通过已有 API 仅授权 owned 身份。`allowUnsigned=false` 保持，首次接受验证 public key pinning；接受资源的三个阶段均要求原生 TCP PLAY 就绪。
- 已挂载后通过公开 `ServerPlayNetworking.reconfigure` 重新握手：内容篡改匹配 `signature mismatch`，同 keyId 换钥匹配 `key rotation not accepted`，每次拒绝均检查 SERVER_CACHE、远端独有文件及语言撤回。恢复原签名后重新连接；最后正常断线也撤回资源。
- 不直接调用客户端同步 handler，不强制额外 reload 推动生命周期。异步重载等待同时满足文件、语言和 overlay 完成，不把配置期临时资源当作已进入远端世界。

此前 [v2–v5 失败](../2026-10-20-fabric-client-preparation/README.md)完整保留，没有覆盖。v5 的工具遍历原始在线玩家列表，而重配置会同步移除玩家；最终工具使用不可变快照并记录每次命令请求/完成及异常。该修正没有改生产信任、签名、地址规范化、wire 或资源优先级。

## 复核与范围

准备与运行分别使用 `python -X utf8 tools/acceptance/fabric-directory-client/prepare.py --node <node> --output <fresh-output> --java-home <jdk25> --gradle-home <cache> --remote`、`python -X utf8 <fresh-output>/run.py <fresh-output>`。两个节点串行，各自绑定提交、源码、观察器与实际生产类。私钥、server.properties、trust-store、环境和用户存档不进入公开归档；资源索引及按长度/SHA-1 校验的缓存补齐记录单独收录，不归档大型资源对象。

独立只读复核命令：`python -X utf8 docs/architecture-refactor/evidence/2026-10-21-fabric-directory-client/verify.py`。归档保留原始结果、15 阶段资源记录、具体拒绝原因、真实地址、RCON 命令、四份隔离源码/观察器、完整日志和截图；逐文件摘要与最终构建类摘要分别核对。

复核结果 PASS。`evidence.zip` 共 97 项、1,574,061 bytes，摘要见 `verification.json`；两节点均无 server-errors、超时或强制终止。历史六组相关归档的只读 verifier 也全部通过，未重跑已经通过的构建、common 检查或正式性能组。

原始结果的 `screenshotsReviewed=false` 不改写，主代理的真实视觉审核另存 `visual-review.json`，`humanMaintainerAccepted=false` 保持。此处不补签维护者整体接受，不扩展到所有 OS/JDK、其余三个节点的图形矩阵、发行 JAR 安装、编译型 guest 或长期内存预算；本轮没有重新运行正式性能组，也没有外部发帖、PR 或 push。
