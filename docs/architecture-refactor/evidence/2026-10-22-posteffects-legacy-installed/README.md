# Legacy PostEffects 独立安装客户端补验

## 通过结果

最新候选 `4405b0b0ba8e4d1665c6e1d92d1b172cc4f18349`，Minecraft 1.21.1 / NeoForge 21.1.227 / Java 21，全新世界。独立 ModDev 2.0.140 启动器只注册验收 helper 为 source mod，NekoJS 从生产 JAR 加载。客户端 JAR SHA256 `21936131f9b234024481514f0a5e02bbf352ac38e4f9d4b038dd637756c2fc13`，专服 JAR `ab66ac8132f11796e7e182c86d85c60425c0a717397f6687a26b5290db2a4d37`；两者全部 1567 个 common/node class 与构建产物匹配，客户端的 ModList、CodeSource、resource URL 和关键 class bytes 另外核验。

16 阶段通过：clear/effect、资源重载不再 set 而保留、clear/effect、失败候选保留、恢复 clear/effect、显式清除后重载、定义退役、未激活目标重载、重新激活目标、原版 creeper 链接管，以及旧定义通过适配器退役后不清理原版链。七次 synthetic native F3+T 调度均清理临时 F3 状态。客户端和服务器正常退出 0，无强制清理。

主代理逐张查看全部 16 张原始 PNG，确认 clear/invert/native creeper 分类。ROI `(230,200)-(380,260)` 排除 HUD；原版链退役前后图像差异为 0。比较与来源复核脚本保留完整实现及日志。截图来自渲染目标读取，不声称物理键盘、显示器 present、官方外部安装器或维护者人工体验验收。

## 未通过的第一轮

最初使用 `d91ca974` 的专服 JAR，虽然它确实包含客户端 reload mixin，但其中嵌入的 ICU 模块与客户端已有 ICU 重复，产生 `ResolutionException: Module com.ibm.icu reads another module named com.ibm.icu`。客户端退出 1，服务器正常退出 0，没有强制清理，该轮 NOT GREEN 原样保留。

修正遵循现有构建配置：客户端用不嵌入 ICU 的普通 JAR，专服用嵌入 ICU 的 server 分类 JAR。第二轮升级为最新源码的确切制品，从新目录/世界执行完整 16 阶段；没有修改生产构建或放宽来源检查。不能仅凭 server 文件名推断客户端类被剔除。

## 离线检查与边界

运行 `python docs/architecture-refactor/evidence/2026-10-22-posteffects-legacy-installed/verify.py` 验证封包摘要、安装 class 绑定、16 张图的原始摘要、原版所有权状态、七次原生调度、退出和首轮失败记录。封包不包含 world、JAR、依赖或 server.properties。

本结果完成 legacy 安装客户端具体路径，不替代完整严格 TSC、最终性能、制品/数据分离回退或整票维护者结论。此前现代节点安装结果绑定中间候选 d91ca974；其后续源码未变更 PostEffects，但仍需按确切候选说明每项验证覆盖范围。
