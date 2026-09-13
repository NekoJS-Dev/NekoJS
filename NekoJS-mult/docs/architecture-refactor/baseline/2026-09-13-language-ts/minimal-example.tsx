// NekoJS 工单 12 最小可运行示例：TSX（.tsx = JSX lowering + TS 类型擦除）
//
// .tsx 归 JSX 语言族（language id 与 .jsx 同为 "jsx"），但组合了 TS 擦除层：
// 类型注解与 interface 都会被擦除，JSX 结构照常 lowering。

export interface Item {
  label: string;
}

export function render(item: Item) {
  return <span class="item">{item.label}</span>;
}

export const first: Item = { label: 'x' };
