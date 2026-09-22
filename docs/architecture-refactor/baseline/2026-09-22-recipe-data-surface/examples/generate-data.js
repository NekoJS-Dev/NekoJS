// Ticket 23 example — non-Assets data generation through the single shared batch path.
//
// ServerEvents.generateData writes datapack JSON (NOT assets: texture/sound/lang generation
// keeps using ClientEvents.generateAssets). Plugins implementing NekoJSPlugin.generateData
// and this script event share ONE generation path: plugins contribute first, scripts second,
// everything lands in a candidate area, is validated (paths, JSON, read-back) and is then
// atomically published into <gameDir>/nekojs/data.
//
// Data protection: a file you (the user) edited by hand is NEVER overwritten — the batch
// skips it and reports it. A failed batch publishes nothing and keeps the previous data.
//
// Capabilities used (gated): ServerEvents.generateData with the 'after_mods' stage key is
// available on NeoForge nodes (26.x / 1.21.1). On fabric nodes the bus is not declared —
// scripts referencing it fail loudly instead of silently doing nothing.

// stage-keyed listener (the dispatch key is the stage, 'after_mods' today)
ServerEvents.generateData('after_mods', event => {
  // 1) write a datapack JSON (JS object or JSON string both work)
  event.json('nekojs:loot_tables/blocks/ruby_ore.json', {
    type: 'minecraft:block',
    pools: [{
      rolls: 1,
      entries: [{ type: 'minecraft:item', name: 'nekojs:ruby' }]
    }]
  })

  // 2) write raw text (mcmeta, .txt, anything non-JSON)
  event.text('nekojs/notes/generated-by.txt', 'nekojs generateData example')

  // 3) read back what THIS batch produced (reads the candidate area before publish,
  //    the published active root after publish — same call either way)
  const loot = event.getJson('nekojs:loot_tables/blocks/ruby_ore.json')
  if (loot.pools.length !== 1) {
    throw new Error(`unexpected pool count: ${loot.pools.length}`)
  }
})
