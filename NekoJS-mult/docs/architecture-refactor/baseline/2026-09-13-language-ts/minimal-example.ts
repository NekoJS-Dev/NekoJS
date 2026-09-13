// NekoJS 工单 12 最小可运行示例：可擦除 TypeScript（.ts）
//
// 只使用已通过 gate 的语法：
//   - interface / type 别名（整条擦除，不产生运行时代码）
//   - 参数、返回值、变量的 : T 类型标注
//   - 泛型 <T>（擦除后不留痕迹）
// 不使用装饰器、namespace、enum 之外的不可擦除构造（装饰器会明确报错）。

export interface Greeting {
  name: string;
}

export type Count = number;

export function greet(name: string): string {
  return 'hello ' + name;
}

export function double<T extends Count>(value: T): Count {
  return value * 2;
}

export const answer: Count = 40 + 2;
