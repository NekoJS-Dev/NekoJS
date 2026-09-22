// Ticket 23 example — recipe viewer (JEI) integration with CONDITIONAL capability.
//
// The RecipeViewerEvents group is registered ONLY when ALL of these hold:
//   * the node is a NeoForge node (fabric nodes compile none of the JEI wiring),
//   * the game is a client,
//   * the JEI mod is actually loaded.
// On any other environment the group is NOT registered: referencing RecipeViewerEvents
// fails loudly (unknown member) instead of registering listeners that never fire.
// If you ship scripts to mixed environments, gate the block from the outside yourself
// (e.g. a per-environment script set) — do NOT rely on a silent no-op.

RecipeViewerEvents.addEntries('item', event => {
  event.add('nekojs:ruby')
})

RecipeViewerEvents.removeRecipes(event => {
  // hide one recipe outright, or scope the removal to one category
  event.remove('nekojs:smelting/ruby')
})

RecipeViewerEvents.removeCategories(event => {
  event.remove('minecraft:composting')
})

RecipeViewerEvents.addInformation(event => {
  event.add('nekojs:ruby', ['A generated gem.', 'Drop it in lava for a surprise.'])
})
