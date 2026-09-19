// 最小 TS 示例（ESM）：接口/类型注解/泛型/class 类型不进入运行时，
// 值、控制流与导出形状与等价 JS 完全一致（类型擦除语义）。
import { greet, TAG, type Greeting } from './greet.ts';

interface Box<T> {
    value: T;
}

const box: Box<number> = { value: 20 + 22 };
const greeting: Greeting = greet('neko');

export const message: string = greeting.message;
export const tag: string = TAG;
export const answer: number = box.value;
