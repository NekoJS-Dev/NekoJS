// NekoJS 工单 12 最小可运行示例：JSX（.jsx）
//
// 默认使用 classic runtime：lowering 成 globalThis.__nekoJsxFactory / __nekoJsxFragment
// 调用（由 nekojs/node/internal/define.js 提供）。开启 jsxAutomaticRuntime 后改用
// import { jsx, jsxs, Fragment } from 'nekojs/jsx-runtime'。

export const element = <div class="greeting">hello world</div>;

export const fragment = <>a<b />c</>;

/** classic runtime 的元素形状：{ type, props, children: [...] }。 */
export function textOf(node) {
  return node.children.join('');
}
