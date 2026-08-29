// fabric 分支节点入口（26.1.2-fabric）：节点构建逻辑全部住在 buildSrc 的
// nekojs.fabric-node convention plugin（DEVEX-ROADMAP T2），loom-back-compat 由插件体内
// apply（从控制器脚本的 apply false 声明读 Loom 版本）。
plugins {
    id("nekojs.fabric-node")
}
