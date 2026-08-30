// Fabric 节点入口（26.1.2-fabric）：构建逻辑全部住在 buildSrc 的 nekojs.fabric-node
// convention plugin。loom-back-compat 在插件体内 apply，Loom 版本从控制器脚本的
// `apply false` 声明读取。
plugins {
    id("nekojs.fabric-node")
}
