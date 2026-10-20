# Fabric 客户端目录资源原生验收入口

仅在新的 `build/priority-closeout` 子目录中运行，依赖本机已缓存的 Fabric/MC、JDK 25、Python 和 Gradle 依赖。它是测试 mod，生产构建不加载此入口；不安装 MCP、不改其接管权限，也不修改玩家原有存档、资源包或信任数据。

本目录是独立验收入口，工具存在或编译成功不等于客户端通过；实际通过、失败与未执行范围由各轮证据单独记录。

2026-10-21：[两 Fabric 节点完整原生验收](../../../docs/architecture-refactor/evidence/2026-10-21-fabric-directory-client/README.md)均通过，各 15 阶段，实际生产类与最终构建一致，客户端/服务端正常退出，无强制终止。六张截图已经主代理实际查看；视觉记录与原始结果、维护者整体接受分开保留。此前 v2–v5 失败仍单独保存，不覆盖。

## 覆盖范围

- 原生初次资源加载后检查 `parentModel`、`texture` 生成的模型及 26.x item definition，确认 ModelManager 没有返回 MissingItemModel，并读取真实纹理引用。
- ROOT/GLOBAL/WORLD 的同名文件覆盖、完整资源栈及 LanguageManager/I18n 合并结果。
- Fabric Client GameTest API 在同一客户端创建 A、创建 B、重开同一 A 存档；每次进入和退出都等待真实资源变化，不主动强制 reload 来掩盖连接事件漏发。
- 修改 GLOBAL assets 后通过测试输入按下 F3+T，等待语言和文件内容更新。
- 等待正常请求触发的原生异步重载：文件赢家、语言赢家及 loading overlay 完成同时满足后，再核对完整资源栈、模型与旧语言撤回；不通过额外 reload 推动被测流程。
- 在每个测试世界的真实物品栏保存原生截图。自动模型/资源断言与截图审核分开记录，`screenshotsReviewed=false` 需要实际查看图片后才能补结论。
- 检查最后退出世界、CLIENT_STOPPING 回调及 launcher exit；超时记录强制终止，只清理带本次唯一 JVM property 的游戏和本次 launcher。
- `--remote` 另起隔离的服务端 JVM，保持同一客户端使用真实 TCP：未信任拒绝 → 原签名接受 → 已挂载 assets 篡改后重配置拒绝 → 原签名恢复 → 同 keyId 换钥拒绝 → 再次恢复 → 正常断线。每次拒绝匹配原生断线页的具体原因，检查 SERVER_CACHE、资源栈、远端独有文件和语言均撤回。
- 每次接受连接都要求原生 level、player 和 TCP PLAY listener 就绪，再等待资源应用并发送后续重配置命令；配置期短暂出现资源不计作已进入远端世界。

远端场景使用公开 `ServerPlayNetworking.reconfigure` 重新运行真实配置任务，不直接调用客户端同步 handler。测试密钥在准备进程用既有 Ed25519 签名 API 生成，私钥只写入隔离服务端的 `acceptance-private`，不进入分发包或客户端。客户端从公开 configuration INIT 的 PacketContext 获取实际 TCP 地址，先验证它是 owned loopback/端口，再复用生产地址规范化获取信任身份；本机别名不假定为字面 `127.0.0.1`。先证明该身份未信任并拒绝，再通过既有 API 仅信任此身份，验证首次接受后的公钥 pinning；`allowUnsigned=false` 保持。RCON 密码只留在隔离的 server.properties，不能连同该文件、私钥或信任存储打包公开证据。

不启用 `--remote` 时仅跑本地场景，`remoteSyncAccepted=false` 保留。所有模式的资源检查均不等于视觉审核、全部平台验收或维护者接受。它使用开发 `runClient` / `runServer` 的真实 loader/生产类，不证明发行 JAR 安装启动。客户端测试开启 Fabric 的实验性 `fabric-client-gametest` 接口；已只读核对两节点锁定 API，准备时选择对应 `ClientScreens` 源文件来适配原生屏幕访问差异。两个节点仍需分别编译和执行，不成为生产依赖扩展。

## 分开准备与执行

准备只写隔离 fixture 和 Gradle init，不编译或启动游戏；输出目录必须不存在。先完成源码提交，再准备，避免执行时的 revision 绑定变化。

```powershell
python tools/acceptance/fabric-directory-client/prepare.py `
  --node 26.2.0-fabric `
  --output D:/mcmodDemo/NekoJS-mult/build/priority-closeout/fabric-client-final-v1 `
  --java-home "C:/Program Files/Java/jdk-25.0.2" `
  --gradle-home D:/mcmodDemo/NekoJS/.gradle-perf02 `
  --remote
```

集中执行时使用准备好的副本。先由既有节点 `classes` 任务编译生产代码，再冻结实际生产类摘要，随后编译隔离测试 mod，最后运行一次真实客户端。生产源码摘要、测试源码摘要、结果、截图路径和各阶段日志均保留于输出目录；失败时换新的目录，不覆盖旧尝试。

```powershell
python build/priority-closeout/fabric-client-final-v1/run.py `
  D:/mcmodDemo/NekoJS-mult/build/priority-closeout/fabric-client-final-v1
```

另一 Fabric 节点用新的输出目录单独执行，已核对的接口仍需实际编译与运行确认。准备脚本不复制旧 world 或使用已有账号，游戏需要本机图形环境。远端 fixture 绑定本机 25893/25894，仅供这对进程使用；执行时监控服务器就绪和客户端阶段请求，结束后 RCON 正常停服，失败与强制终止均单列记录。不要与正式性能组并行运行，也不要因为组件通过而改写性能成绩。
