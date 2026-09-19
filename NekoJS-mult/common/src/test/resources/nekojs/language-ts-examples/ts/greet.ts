// 最小 TS 示例依赖：类型擦除不改变运行时值；导出形状与 JS 一致。
export interface Greeting {
    message: string;
}

export function greet(name: string): Greeting {
    const formatted: string = 'hello, ' + name + '!';
    return { message: formatted } as Greeting;
}

export const TAG: string = 'ts';
