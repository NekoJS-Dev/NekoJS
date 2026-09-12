// NekoJS managed API 最小示例（ticket 09 / AC7）
// 只使用已通过 gate 且经契约 invoker 验证的 managed 能力：ID / Text / NBT 值构造与序列化。
// 不展示 not verified / unavailable 能力。
//
// 本脚本与 ManagedSurfaceEndToEndChainTest.MINIMAL_EXAMPLE_SCRIPT 保持一致，
// 该测试把它作为「真实脚本调用穿透 contract 反射 → manifest → Probe TS declaration」
// 的端到端证据执行（AC11）。
//
// 已知边界（见同目录 REPORT.md「问题与处理」）：NBT.compound()（CompoundBuilder host
// object）虽被契约与 TS/Py declaration 承诺，但当前经契约 invoker 返回 host object 会触发
// NATIVE_TYPE_LEAK；示例因此使用 NBT.of(...) 值路径。

const id = ID.of('nekojs', 'chain');
const label = Text.of('chain-').append('example');
if (label.isEmpty()) {
  throw new Error('label must not be empty');
}

const tag = NBT.of({
  id: ID.asString(id),
  label: 'chain-example',
  count: 3
});

// SNBT 形如 {id:"nekojs:chain",label:"chain-example",count:3}
NBT.toSnbt(tag)
