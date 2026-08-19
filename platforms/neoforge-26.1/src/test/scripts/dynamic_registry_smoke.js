// 26.x 运行时注册 smoke（参考副本 —— 复制到游戏运行目录 nekojs/server_scripts/ 后生效）
// 前置条件：
//   1) nekojs/config/engine.toml 中 [dynamicRegistry] enabled = true（默认 false），改后需重启游戏
//   2) 服务器运行中（server_scripts 本身在服务器启动时执行，天然满足）
// 本脚本验证：
//   1) DynamicRegistry.soundEvent / mobEffect / item 三类运行时注册（B1 + B2）
//   2) 选项面：mode / stackSize / rarity / fireResistant / fixedRange / category / color
//   3) 返回 handle 的 id()/mode()/owner()
//   4) reload 幂等：/nekojs reload server 重跑脚本 → 同 id 复用（re-claim），不重复注册
// 验证结果：日志出现三条 "DynamicRegistry: ... registered by nekojs:server/..."，
// /nekojs registry（后续批次接入命令）应显示 item/sound_event/mob_effect 各 1 registered, 0 stale；
// 删除本文件后再 /nekojs reload server → 对应条目变为 stale（保留不卸载）。

console.log('=== dynamic registry smoke ===');

// B1: SoundEvent（plain registry —— 验证冻结绕过骨架）
const boom = DynamicRegistry.soundEvent('nekojs_smoke:boom', { mode: 'world', fixedRange: 16.0 });
console.log(`soundEvent handle: ${boom.id()} mode=${boom.mode()} owner=${boom.owner()}`);

// B1: MobEffect（plain registry）
const witherTouch = DynamicRegistry.mobEffect('nekojs_smoke:wither_touch', {
    mode: 'world',
    category: 'harmful',
    color: 0x8B0000,
});
console.log(`mobEffect handle: ${witherTouch.id()} mode=${witherTouch.mode()}`);

// B2: Item（26.x 组件路径：注册后立即绑定 holder 默认组件）
const ruby = DynamicRegistry.item('nekojs_smoke:ruby', {
    mode: 'world',
    stackSize: 64,
    rarity: 'epic',
    fireResistant: true,
});
console.log(`item handle: ${ruby.id()} mode=${ruby.mode()}`);

// 幂等复跑：同 id 二次注册 → 复用已注册条目并 re-claim（不抛错、不重复）
const rubyAgain = DynamicRegistry.item('nekojs_smoke:ruby', { mode: 'world', stackSize: 64 });
console.log(`item re-claim: ${rubyAgain.id()} (same entry reused)`);

// RELOADABLE 模式：v1 行为与 WORLD 相同（仅记账差异），验证模式解析可用
const ping = DynamicRegistry.soundEvent('nekojs_smoke:ping', { mode: 'reloadable' });
console.log(`reloadable handle: ${ping.id()} mode=${ping.mode()}`);

console.log('=== dynamic registry smoke registered (reload server to see re-claims; remove file to see stale) ===');
