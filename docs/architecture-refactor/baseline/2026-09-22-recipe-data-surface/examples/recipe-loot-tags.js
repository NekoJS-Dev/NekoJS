// Ticket 23 example — the existing recipe / loot / tags event domain.
//
// Everything here reuses the EXISTING buses (ServerEvents.recipes / afterRecipes /
// lootTables / tags): no parallel events were added. Phase order inside one server data
// reload (pinned by Ticket23RecipeDataPhaseTraceTest):
//   lootTables JSON pass  ->  generateData batch  ->  ...  ->  recipes + commit
//   ->  afterRecipes (AFTER the recipe map is fully committed).

// ---- recipes: modify existing JSON + generate new recipes -------------------------
ServerEvents.recipes(event => {
  // remove by filter (id-based filters shown; registry-backed filters like {output: ...}
  // need the running server, see the ticket 23 report for the capability note)
  event.remove({ idStartsWith: 'minecraft:chest' })

  // modify matching recipes through JSON paths
  event.forEach({ mod: 'minecraft' }, recipe => {
    recipe.setPath('result.count', 4)
  })

  // generate a new recipe from a custom JSON shape; the type is stamped and a
  // nekojs:<prefix>_<hash> id is generated deterministically (no silent clobber of
  // existing ids — a collision gets a distinct suffix)
  event.custom('minecraft:smelting', {
    ingredient: { item: 'nekojs:ruby_ore' },
    result: { item: 'nekojs:ruby', count: 1 },
    experience: 0.7,
    cookingtime: 100
  }).id('nekojs:smelting/ruby')
})

// ---- afterRecipes: post-commit phase ----------------------------------------------
// afterRecipes now fires only AFTER the recipe data is fully committed to the
// RecipeManager: use it to observe/query the committed state. Modifications made here no
// longer enter the commit — do modifications in ServerEvents.recipes instead.
ServerEvents.afterRecipes(event => {
  const total = event.count()
  console.info(`recipes committed, total=${total}`)
})

// ---- loot tables: JSON-level management (NeoForge nodes) ---------------------------
ServerEvents.lootTables(event => {
  // modify an existing table (or start one from scratch if unknown)
  event.modify('minecraft:blocks/stone', table => {
    table.addPool(pool => {
      pool.rolls(1)
    })
  })

  // full replacement / creation via raw JSON
  event.setJson('nekojs:entities/ruby_golem', {
    type: 'minecraft:entity',
    pools: []
  })

  // "delete" a table for the next reload
  event.remove('minecraft:blocks/dirt')
})

// ---- tags: add / remove / replace per registry (NeoForge nodes) --------------------
ServerEvents.tags('minecraft:item', event => {
  event.add('nekojs:gems', 'nekojs:ruby')
  event.remove('c:gems', 'minecraft:emerald')
  event.replaceAll('nekojs:tools', 'nekojs:ruby_pickaxe')
})
