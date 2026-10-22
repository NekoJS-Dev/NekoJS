# 最新统一候选的 PostEffects 安装客户端

候选 `4405b0b0ba8e4d1665c6e1d92d1b172cc4f18349` 已完成三个 NeoForge 节点的独立安装客户端渲染验收：本包 modern 两节点各七阶段，连同 [legacy 16 阶段](../2026-10-22-posteffects-legacy-installed/README.md)，共 30 张原图，由主代理逐张复核。六个游戏进程均正常退出 0，无强制清理。每个客户端只把验收 helper 作为 source mod，NekoJS 来自生产 JAR，并核对实际 ModList、CodeSource、class resource 和 JAR class bytes。

| 节点 | 客户端 / 服务器 NeoForge | NekoJS SHA256 |
| --- | --- | --- |
| 26.1.2 | 26.1.2.71 / 26.1.2.71 | e1fdc7d59b8b91232f50f956cfe8022eb02ebc45fa6e5d86e1775e61224fd763 |
| 26.2.0 | 26.2.0.57 / 26.2.0.75 | ada6f548c14a47ea7119e888b60b5623a1cd48e427c4652bd5cf6ae48fc23b0f |

modern 各自全部 1638 个生产 class 与构建绑定；三次 native F3+T、失败重载后无 set 保留效果及恢复通过。七阶段 ROI 边缘测量和原图摘要完整保留。开发启动器、synthetic native 按键与 RenderFrameEvent.Post 图像不等同于官方外部安装器、物理键盘/显示器 present 或维护者人工验收。

`python docs/architecture-refactor/evidence/2026-10-22-unified-client-native/verify.py` 可离线核对封包、14 图、退出、class 绑定以及 legacy/TCP 引用 archive 的摘要。

此外，五节点旧 `d91ca974` 与本候选的 JAR class 全量比较表明，变化全部位于 Probe 声明实现，其他生产类均逐字节一致。此记录支持复用 [旧候选真实 TCP](../2026-10-22-dynamic-tcp/README.md) 中未变实现的具体行为证据；它不是在新 JAR 上重新执行 TCP 的记录，也不替代完整 TSC、性能、分离回退或最终维护者结论。
