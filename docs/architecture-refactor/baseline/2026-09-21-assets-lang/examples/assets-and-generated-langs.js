// 票 29 最小可运行示例：Assets typed binding + generateAssets/lang 聚合 + plugin-only 语言声明。
// 只使用已通过 gate 的能力：ClientEvents.generateAssets / ClientEvents.lang（既有唯一事件）、
// Assets typed binding（写入 <gameDir>/nekojs/assets，与 generateAssets 同根同校验）、
// 以及 NekoJSPlugin.generatedLangs()（默认 Set.of("en_us")，非 en_us 必须显式声明）。

// ---- 1) Assets typed binding（startup/server/client 脚本顶层直接调用） ----
// 所有文件落在 <gameDir>/nekojs/assets 资源包，与 ClientEvents.generateAssets 同一目录，
// 经同一个 DataGeneratorJS 做路径包含性校验 + 容量上限 + sibling temp 原子写；
// 在下一次资源 reload 时生效（懒读磁盘，保证 reload 时序正确）。
Assets.blockState('mymod:my_block', 'mymod:block/my_block')   // 单空 variant 简写
Assets.blockModel('mymod:my_block', {
  parent: 'minecraft:block/cube_all',
  textures: { all: 'my_block' }                                // 无 ':' 且无 '/' → mymod:block/my_block
})
Assets.itemModel('mymod:my_item', {
  parent: 'minecraft:item/generated',
  textures: { layer0: 'my_item' }                              // → mymod:item/my_item
})
Assets.texture('mymod:block/my_block')                         // 16x16 洋红占位 PNG（路径已含 '/'，原样使用）
Assets.texture('mymod:my_icon', 'item')                        // 显式 kind，要求 id 路径不含 '/'

// ---- 2) 脚本侧资产生成事件（唯一 Assets 事件；不新增第二事件/第二资源根） ----
ClientEvents.generateAssets(event => {
  // event 是 DataGeneratorJS，stage 为 'after_mods'；手写 JSON 与上面的 typed binding 同根同校验。
  event.json('mymod/models/item/custom.json', {
    parent: 'minecraft:item/generated',
    textures: { layer0: 'mymod:item/custom' }
  })
})

// ---- 3) 脚本侧语言条目（既有 lang 事件，按语言代码 keyed dispatch） ----
// 这里的 key（'en_us'/'ja_jp'）会进入平台的语言集合并集：即使没有插件声明，也会为该语言生成文件。
ClientEvents.lang('en_us', event => {
  event.add('mymod.item.thing', 'Thing')
})
ClientEvents.lang('ja_jp', event => {
  event.add('mymod.item.thing', '物')
})
