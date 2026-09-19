// 最小 JSX 示例（CommonJS）：element、嵌套 element 与 fragment 都 lower 成
// __nekoJsxFactory / __nekoJsxFragment 调用，children 由 runtime factory 归一化。
const { tag, empty } = require('./greet.jsx');

const view = <div id="root">{tag()}</div>;

module.exports = {
    rootTag: view.tag,
    rootId: view.props.id,
    childTag: view.children[0].tag,
    childText: view.children[0].props.children,
    fragmentChildren: empty().children.length
};
