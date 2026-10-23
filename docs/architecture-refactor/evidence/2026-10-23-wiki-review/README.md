# 五个双语主题的源码与翻译复核

这次补齐 quick-start、script-packs、registering-new-content、type-adapters、event-extensions 的实际文档审查，再用既有 `stamp` 更新摘要。没有仅为消除告警重写digest，所有 humanReview 和 translationStatus 保持原值；这不是维护者人工翻译审核或发布验收。

主代理完整核对快速开始及脚本包中英文；两位只读Luna xhigh分别完整审查注册内容两页、适配器和事件扩展四页，修订后再次只读复核零剩余问题。主代理负责所有修改及验证。对照生产源码 `6e7452b7`：

- 更正 clientSync 的真实默认值和分发开关作用；补齐脚本包英文环境示例、入口限制与模块缓存说明。
- 明确注册ID也接受平台Identifier（1.21.1 ResourceLocation）宿主对象，给流体及附魔代码标出支持平台。
- 更正旧SPI包路径、HostAccess共享配置、非目标Java host object的拒绝行为；补充List import，明确底部测试针对旧SPI基类示例。
- DataComponentsAdapter是26.x静态工具，四个现代节点的本批实际JAR/class绑定均包含该类，legacy没有；不据类存在外推每种组件值的运行时解析结果。
- ScriptEvents负责声明组，原生桥接单独说明；Minecraft标识符片段标明26.x/1.21.1名称差异。原来把普通POJO交给bind的错误示例改为bindTransformed真实登录事件。

`GreetExample.java`封装与指南同型的POJO、EventGroup和bindTransformed调用，使用本批三个NeoForge安装目录的实际依赖/JAR编译，全部javac exit0，各生成3类；这里只称编译验证，不称游戏事件派发已实测。

检查：`npm run check:wiki` PASS，26主题零问题，旧10条digest告警清零；`npm run test:wiki` PASS，8tests/0失败。第一次stamp被type-adapters的Windows CRLF语言链接/导航检查拒绝；规范为LF后原检查通过，未放宽生成器或门禁。全页sync触及另外两份平台页的数字锚点，主代理恢复了自己刚产生的范围外改动，保留原外部锚点；这两页正文与HEAD逐字节一致。其余派生导航仍由既有生成器产生。

归档30条目、110104字节，包含实际文档、前后清单、源码、编译片段与日志及检查日志；不含classpath参数文件、JAR、world或凭据。复核：`python docs/architecture-refactor/evidence/2026-10-23-wiki-review/verify.py`。

完整严格TSC仍为5565条失败，正式重载仍340.0/292.8ms超标；本批文档门禁通过不替代它们或最终统一候选的客户端/TCP/分离回退和维护者结论。没有PR、push或发布。
