// 最小 TSX 示例依赖：TSX = TypeScript 擦除 + JSX 下，语言身份是 tsx。
export interface LabelProps {
    text: string;
}

export function label(props: LabelProps) {
    return <li class="label">{props.text}</li>;
}
