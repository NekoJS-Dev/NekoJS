// NekoJS 模块管线最小示例 —— 原生 ESM 模块（ticket 11 / AC11）
//
// 放置：nekojs/server_scripts/minimal-example.mjs
// 模块模式：requested mode = ESM（.mjs 强制，NekoModuleMode.fromExtension）。
// 语言身份：language id = "javascript"；准备阶段附带 ESM AST 供 link 阶段解析 import/export。
//
// 缓存 / reload 行为：
// - cache key = language id | ESM | 源路径 | 内容 SHA-256；任一变化即失效。
// - ESM 记录按 (moduleId, revision) 缓存；reload 递增 revision，旧记录不再命中。
// - 未解析 import / 缺失导出 / 重复导出在 link 阶段失败并携带原文件行列，不会延迟成
//   无来源的 Graal 异常。
// - 依赖此模块的入口在它变化后会被重跑（依赖图反向查询）。

export const NAME = 'minimal-example';

export function greet(name) {
  return NAME + ': hello ' + name;
}
