// 最小 ESM 示例：命名导入 + 默认导入 + 命名空间一致性（.mjs 强制 ESM 模式）。
// link 行为：缺失导出在 link 阶段报错（带文件行列），不延迟到执行时。
import greet, { TAG } from './greet.mjs';
import * as ns from './greet.mjs';

export const message = greet('neko');
export const tag = TAG;
export const same = ns.default === greet;
