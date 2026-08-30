// NeoForge 节点入口（1.21.1 / 26.1.2 / 26.2.0 共用）：本文件由 stonecutter 对每个节点各
// 求值一次。构建逻辑全部住在 buildSrc 的 nekojs.neoforge-node convention plugin，节点间
// 的差异只来自 versions/<node>/gradle.properties。
plugins {
    id("nekojs.neoforge-node")
}
