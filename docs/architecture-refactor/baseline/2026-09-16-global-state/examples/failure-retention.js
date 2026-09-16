// 失败保留：候选 reload 失败时顶层写不污染旧 active（票 10 最小示例 4/4）
//
// 场景：这个文件已经在线上跑过（global.retained 累加到 1）。现在假设同批 reload 里
// 另一个脚本坏了（例如 `boom.js` 写了 `global.bad = 1` 之后死循环烧尽语句上限）——
// reload 整体失败，boom.js 的 `global.bad` 不会发布；本文件的旧值原样保留。
// 修好 boom.js（或删掉）再 reload，本文件重新执行，retained 在旧值上继续累加到 2。
//
// 坏脚本示例（失败注入，勿真的放进 scripts 目录）：
//   global.bad = 1
//   while (true) { }   // 触发语句上限 → 候选被终止 → reload 失败 → 写集丢弃
global.retained = (global.retained || 0) + 1;
