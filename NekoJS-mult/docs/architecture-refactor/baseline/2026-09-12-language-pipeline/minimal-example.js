// NekoJS 模块管线最小示例 —— 普通 JS 入口（ticket 11 / AC11）
//
// 放置：nekojs/server_scripts/minimal-example.js
// 模块模式：requested mode = AUTO（.js），无 import/export 时由准备管线判定为 COMMONJS。
// 语言身份：language id = "javascript"。
//
// 缓存 / reload 行为（与 NekoModulePreparation 一致，示例说明必须与实现一致）：
// - 首次加载：引擎读文件内容 → 计算稳定 cache key（language id | 请求 mode | 源路径 |
//   内容 SHA-256）→ 准备 → 缓存。
// - 再次加载同一路径：文件 stamp（mtime + size + 内容指纹）与 language identity 都未变时
//   直接命中缓存，不重新准备；模块体只执行一次。
// - 编辑本文件后 /nekojs reload：内容指纹变化 → 旧条目失效 → 重新准备并重跑。
// - 只有 SCRIPT 类型脚本目录下的条目会被 reload 清掉；node_modules 下的共享模块不受影响。

console.info('[minimal-example] plain JS entry loaded');
