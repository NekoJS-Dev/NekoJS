// NekoJS 模块管线最小示例 —— ESM 入口（ticket 11 / AC11）
//
// 放置：nekojs/server_scripts/minimal-example-entry.mjs
// 演示跨模块 import：link 阶段解析 './minimal-example.mjs'，执行阶段读取其导出。
//
// 缓存 / reload 行为：helper（minimal-example.mjs）内容变化后 reload，本入口会被依赖图
// 反向查询命中并重新执行，因此 greet() 的返回值会更新。

import { greet, NAME } from './minimal-example.mjs';

console.info('[minimal-example-entry] ' + NAME + ': ' + greet('world'));
