// 票 36 脚本作者试做示例 9/11：declaration 使用（probe 类型声明 + jsconfig 工作区）
//
// 放置：server_scripts/09-declaration-usage.ts（.ts 是已注册语言，票 12）。
// 公开材料：wiki/Probe-类型生成.md（/nekojs probe 命令、.neko_probe 布局、jsconfig
// 合并语义）；票 30 交付的 WorkspaceGenerator（每个脚本目录的 jsconfig 缺失才写、
// 用户编辑不被 reload/验证/迁移覆盖）；README.txt 提示行（写 .ts 或加
// // @ts-check 启用编辑器检查，/nekojs view_all_errors 查看脚本错误）。
//
// 试做流程（按 TASKS.md 记录）：
//   1) `/nekojs probe`（默认 TS backend）—— 预期在 <gameDir>/nekojs/.neko_probe/
//      生成 @package / @side-only/server / @special / @manual / @nekojs/managed/server
//      声明，并把 server_scripts/jsconfig.json 的 probe 管理条目（java:*、
//      @side-only/*、指向 .neko_probe 的 include/typeRoots）合并进去；
//   2) 在编辑器里打开本文件 —— 预期 Item / ServerEvents 有补全与类型
//      （jsconfig 的 paths 指向 .neko_probe）；
//   3) 把下面「错误成员」行取消注释 —— 预期编辑器标红（declaration 层），
//      且 `/nekojs reload server` 后在错误面板/诊断里看到 binding-preflight 的
//      「Binding 'Item' has no member 'off'」类定位诊断（预检报告不阻止执行，
//      运行时对未知成员另有明确失败）；
//   4) 手动往 server_scripts/jsconfig.json 加一条自己的 include（如
//      "./typings/custom.d.ts"）再跑 `/nekojs probe` —— 预期自己的条目原样保留
//      （probe 只刷新它管理的条目；reset_config 仅 NeoForge，可恢复出厂）。
//
// 本体只使用已验证的公开成员：Item.of / Item.empty（ItemJS）、ItemStack 扩展
// getId()（Mixin 注入，wiki/全局绑定.md）、ServerEvents.started（事件参考）。
// Item stack 的组件依赖服务器 registry；把读取放在 started，避免首次资源 reload
// 尚未绑定 components 时误把生命周期时序问题记成 declaration 失败。
ServerEvents.started(event => {
  const gem = Item.of('minecraft:diamond', 2)
  console.info('trial item: ' + gem.getId() + ' x' + gem.getCount())

  const empty = Item.empty()
  console.info('trial empty stack: ' + (empty === undefined ? 'undefined' : 'ok'))
  console.info('declaration trial server started')
})

// 错误成员（试做第 3 步取消注释；仍放在 started 回调内）：
// ServerEvents.started(event => { const bad = Item.off('minecraft:stone') })
