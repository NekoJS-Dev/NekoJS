# Fabric 客户端目录资源原生验收入口

仅在新的 `build/priority-closeout` 子目录中运行，依赖本机已缓存的 Fabric/MC、JDK 25、Python 和 Gradle 依赖。它是测试 mod，生产构建不加载此入口；不安装 MCP、不改其接管权限，也不修改玩家原有存档、资源包或信任数据。

本入口尚未编译或运行。按“先完成实现，再集中测试”的顺序提交，不能据此宣布客户端验收通过。

## 覆盖范围

- 原生初次资源加载后检查 `parentModel`、`texture` 生成的模型及 26.x item definition，确认 ModelManager 没有返回 MissingItemModel，并读取真实纹理引用。
- ROOT/GLOBAL/WORLD 的同名文件覆盖、完整资源栈及 LanguageManager/I18n 合并结果。
- Fabric Client GameTest API 在同一客户端创建 A、创建 B、重开同一 A 存档；每次进入和退出都等待真实资源变化，不主动强制 reload 来掩盖连接事件漏发。
- 修改 GLOBAL assets 后通过测试输入按下 F3+T，等待语言和文件内容更新。
- 在每个测试世界的真实物品栏保存原生截图。自动模型/资源断言与截图审核分开记录，`screenshotsReviewed=false` 需要实际查看图片后才能补结论。
- 检查最后退出世界、CLIENT_STOPPING 回调及 launcher exit；超时记录强制终止，只清理带本次唯一 JVM property 的游戏和本次 launcher。

该入口不覆盖远端签名/拒绝/断线资源撤回，`remoteSyncAccepted=false` 始终保留；那些场景必须另用真实网络 fixture。资源检查不等于视觉审核、全部平台验收或维护者接受。它使用开发 `runClient` 的真实 loader/生产类，不证明发行 JAR 安装启动。客户端测试开启 Fabric 的实验性 `fabric-client-gametest` 接口；目前已核对 26.2 的接口，另一节点仍需独立核对和执行，不成为生产依赖扩展。

## 分开准备与执行

准备只写隔离 fixture 和 Gradle init，不编译或启动游戏；输出目录必须不存在。先完成源码提交，再准备，避免执行时的 revision 绑定变化。

```powershell
python tools/acceptance/fabric-directory-client/prepare.py `
  --node 26.2.0-fabric `
  --output D:/mcmodDemo/NekoJS-mult/build/priority-closeout/fabric-client-final-v1 `
  --java-home "C:/Program Files/Java/jdk-25.0.2" `
  --gradle-home D:/mcmodDemo/NekoJS/.gradle-perf02
```

集中执行时使用准备好的副本。先由既有节点 `classes` 任务编译生产代码，再冻结实际生产类摘要，随后编译隔离测试 mod，最后运行一次真实客户端。生产源码摘要、测试源码摘要、结果、截图路径和各阶段日志均保留于输出目录；失败时换新的目录，不覆盖旧尝试。

```powershell
python build/priority-closeout/fabric-client-final-v1/run.py `
  D:/mcmodDemo/NekoJS-mult/build/priority-closeout/fabric-client-final-v1
```

另一 Fabric 节点必须用新的输出目录准备并单独执行。准备脚本不复制旧 world 或使用已有账号，游戏需要本机图形环境。不要与正式性能组并行运行，也不要因为组件通过而改写性能成绩。
