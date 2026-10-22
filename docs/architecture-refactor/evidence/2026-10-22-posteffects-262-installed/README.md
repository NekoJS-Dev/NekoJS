# 26.2 PostEffects 安装生产 JAR 补验

冻结候选 `d91ca974`、NekoJS JAR SHA256 `c2e4448eb0aa7e7a7c91270a9b537fa1b4ef200a52e31f490bb4d3ec58f28c35`：独立 ModDev 2.0.140 客户端启动器的唯一 source mod 为验收 helper；NekoJS 从安装生产 JAR 加载，ModList、CodeSource、resource URL 与 class bytes 均核对。全部 1635 个 common/node packaged class 与冻结摘要相符。客户端 NeoForge 26.2.0.57，服务器 26.2.0.75，Java 25；全新世界。

七阶段均通过：clear/effect、原生 F3+T 后 clear/effect、失败候选后不再次 set 而保留已有效果、恢复后 clear/effect。三次 synthetic native T dispatch 使用有界 F3 状态并清理，未声称物理键盘输入。客户端和服务器均正常退出 0，无强制清理。主代理已逐张查看七张原始 PNG；ROI `(230,200)-(380,260)` 避开 HUD，清晰边缘平方梯度均值 338.36–338.71，blur 为 12.80–12.91。虽然夹具使用 `minecraft:invert` 这个声明 ID，其参数资源实际指定 blur；图像验收按真实模糊效果，不按 ID 名称推断。

26.2 沿用既有 GUI overlay / renderer target API，供应商开发图标警告通过 `neoforge.warnings.logofile.hide=true` 隐藏；未修改生产权限、gate 或源码。截图来自 RenderFrameEvent.Post 的目标读取，不声称显示器 present、人工游戏体验或官方外部安装器验收。

`python docs/architecture-refactor/evidence/2026-10-22-posteffects-262-installed/verify.py` 可离线核对 archive、class 绑定、七图原始摘要、测量值、原生调度次数和退出结果。完整脚本、helper 源码与日志封存；不含 world、JAR、依赖、server.properties 或认证数据。

本结果仅覆盖该中间候选；legacy 的独立安装客户端、后续源码的统一候选、完整 TSC、最终性能和制品/数据分离回退仍需补齐。与 26.1.2 的前次安装补验合看，不替代整票或维护者发布结论。
