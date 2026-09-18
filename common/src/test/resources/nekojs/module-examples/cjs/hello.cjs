// 最小 CJS 示例：强制 CommonJS 模式（.cjs 后缀）；重复 require 返回同一模块身份。
// 缓存行为：同一依赖只求值一次；内容变化后经 invalidate/reload 重新求值。
const { add } = require('./greet.cjs');
const again = require('./greet.cjs');

module.exports = { sum: add(20, 22), identical: again.add === add };
