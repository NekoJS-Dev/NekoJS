// NekoJS 模块管线最小示例 —— CommonJS 模块（ticket 11 / AC11）
//
// 放置：nekojs/server_scripts/minimal-example.cjs
// 模块模式：requested mode = COMMONJS（.cjs 强制，NekoModuleMode.fromExtension）。
// 语言身份：language id = "javascript"；准备阶段附带 CjsModuleRecord 静态分析
// （字面量 require 依赖 + 导出形状）。
//
// 缓存 / reload 行为：
// - cache key = language id | COMMONJS | 源路径 | 内容 SHA-256；任一变化即失效。
// - 重复 require 同一路径命中 CJS 模块缓存，模块体只执行一次（Node 语义）。
// - 循环 require 拿到对端「部分 exports」，不会无限重入。
// - 编辑后 reload 会重新准备并重跑依赖它的入口。

function greet(name) {
  return 'hello ' + name;
}

module.exports = { greet };
