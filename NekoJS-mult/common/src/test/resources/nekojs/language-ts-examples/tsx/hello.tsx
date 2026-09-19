// 最小 TSX 示例（ESM）：类型注解擦除与 JSX 下降共存；
// TSX 泛型组件/泛型箭头歧义由既有 TSX 语料覆盖，本示例只用已过 gate 的形态。
import { label, type LabelProps } from './greet.tsx';

const props: LabelProps = { text: 'tsx' };
const names: string[] = ['a', 'b'];

const view = <ul id="list">{names.map((item: string) => <li>{item}</li>)}</ul>;
const single = label(props);

export const listId: string = view.props.id;
export const childCount: number = view.children.length;
export const labelText: string = single.props.children;
