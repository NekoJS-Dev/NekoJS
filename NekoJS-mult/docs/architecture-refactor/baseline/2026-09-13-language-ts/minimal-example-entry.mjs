// NekoJS 工单 12 最小可运行示例入口：同一个 ESM 入口混合 import .ts / .jsx / .tsx。
//
// 三种扩展名共享同一套 prepared module / 模块身份 / cache key / source map；
// 入口把结果写到 globalThis.result，供示例测试断言真实执行结果（不是静态文本断言）。

import { greet, double, answer } from './minimal-example.ts';
import { element, textOf } from './minimal-example.jsx';
import { render, first } from './minimal-example.tsx';

globalThis.result = [
  greet('world'),
  double(answer),
  textOf(element),
  render(first).children.join('')
].join('|');
