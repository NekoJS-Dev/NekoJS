// 最小 JS 示例（CommonJS）：入口 require 同目录依赖，导出问候语。
// 运行：把本目录两文件放入 server_scripts/ 任一子目录，入口被加载后 message 即结果。
const { greet } = require('./greet.js');

module.exports = { message: greet('neko') };
