// NeoForge 分支节点入口（1.21.1 / 26.1.2 / 26.2.0 共享）：本文件由 stonecutter 对每个
// 节点各求值一次。节点构建逻辑全部住在 buildSrc 的 nekojs.neoforge-node convention
// plugin（DEVEX-ROADMAP T2）——节点可变项只看 versions/<node>/gradle.properties。
plugins {
    id("nekojs.neoforge-node")
}
