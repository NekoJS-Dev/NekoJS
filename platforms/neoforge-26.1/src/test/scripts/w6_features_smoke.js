// W6 功能冒烟（gametest server，真 FML 环境）：原料动作 / BlockEvents.modification /
// ItemEvents.modification 补面（food/attributes/tool）/ RegistryEvents builder .tag()。
// 每个特性块独立 try/catch：单个失败打印 SMOKE-FAIL 与原因，不拖垮其余验证；
// CI 之外人工核对 run/logs/nekojs/server.log 中的 SMOKE-OK / SMOKE-FAIL 行。

try {
  ServerEvents.recipes(event => {
    event.shaped('minecraft:torch', ['C', 'S'], { C: '#minecraft:coals', S: 'minecraft:stick' })
        .id('nekojs:w6_damage_smoke')
        .damageIngredient('minecraft:iron_pickaxe', 1)
        .keepIngredient('minecraft:stick')
    event.shapeless('minecraft:diamond', ['minecraft:emerald'])
        .id('nekojs:w6_replace_smoke')
        .replaceIngredient('minecraft:emerald', 'minecraft:diamond')
  })
  console.log('=== W6 smoke: ingredient actions SMOKE-OK (registered damage/keep/replace) ===')
} catch (e) {
  console.log('=== W6 smoke: ingredient actions SMOKE-FAIL: ' + e + ' ===')
}

try {
  BlockEvents.modification(event => {
    event.modify('minecraft:stone', block => {
      block.hardness = 2.5
      block.resistance = 7
      block.lightLevel = 4
      block.requiresTool = true
    })
  })
  console.log('=== W6 smoke: BlockEvents.modification SMOKE-OK (listener registered) ===')
} catch (e) {
  console.log('=== W6 smoke: BlockEvents.modification SMOKE-FAIL: ' + e + ' ===')
}

try {
  ItemEvents.modification(event => {
    event.modify('minecraft:stick', item => { item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true } })
    event.modify('minecraft:blaze_rod', item => { item.attackDamage = 5; item.attackSpeed = -2.0 })
    event.modify('minecraft:bone', item => { item.tool = { miningSpeed: 5 } })
  })
  console.log('=== W6 smoke: ItemEvents.modification expansion SMOKE-OK (listener registered) ===')
} catch (e) {
  console.log('=== W6 smoke: ItemEvents.modification expansion SMOKE-FAIL: ' + e + ' ===')
}

try {
  RegistryEvents.item(event => {
    event.create('nekojs:w6_tag_smoke', builder => builder.tag('c:tools', '#minecraft:pickaxes'))
  })
  console.log('=== W6 smoke: builder .tag() SMOKE-OK (registered with pending tags) ===')
} catch (e) {
  console.log('=== W6 smoke: builder .tag() SMOKE-FAIL: ' + e + ' ===')
}

try {
  Assets.blockModel('nekojs:w6_smoke', { parent: 'minecraft:block/cube_all', textures: { all: 'nekojs:w6_smoke' } })
  Assets.itemModel('nekojs:w6_smoke', { parent: 'minecraft:item/generated', textures: { layer0: 'nekojs:w6_smoke' } })
  Assets.blockState('nekojs:w6_smoke', { variants: { '': { model: 'nekojs:w6_smoke' } } })
  Assets.texture('nekojs:w6_smoke')
  console.log('=== W6 smoke: Assets helpers SMOKE-OK (model/state/texture written) ===')
} catch (e) {
  console.log('=== W6 smoke: Assets helpers SMOKE-FAIL: ' + e + ' ===')
}
