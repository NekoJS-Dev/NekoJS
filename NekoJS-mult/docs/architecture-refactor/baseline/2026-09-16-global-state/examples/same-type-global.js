// 同类型 global：server_scripts 内多个文件共享同一份状态（票 10 最小示例 1/4）
//
// 用法与 1.2.0 之前完全一致：同类型脚本直接 `global.foo` 读 / `global.foo = v` 写。
// 变化在于隔离与事务语义：
//   - STARTUP / SERVER / CLIENT / TEST 各有独立 store，同名 key 互不覆盖；
//   - reload 期间（candidate）的顶层写入先进候选写集，成功提交才发布，失败全部丢弃；
//   - 值跨普通 reload / server stop / 切世界保留，游戏关闭（root close）才释放。
//
// 本文件只使用已通过 gate 的能力：global 顶层成员读写与自增。
global.loads = (global.loads || 0) + 1;
global.mod = { loaded: true };
