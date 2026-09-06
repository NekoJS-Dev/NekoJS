const smokeTag = "minecraft:planks"
const smokeMarker = "minecraft:diamond"

ServerEvents.tags("minecraft:item", event => {
  // The production TagLoaderMixin applies this mutation after dispatch.
  event.add(smokeTag, smokeMarker)
  console.info("FABRIC-CI-SMOKE: tags event ok")
})

ServerEvents.tagsUpdated(event => {
  const itemRegistry = event.registries.registries().toArray()
    .find(entry => entry.key().identifier().toString() === "minecraft:item").value()
  const tag = itemRegistry.getTags().toArray()
    .find(candidate => candidate.key().location().toString() === smokeTag)
  const applied = tag.stream().toArray()
    .some(holder => itemRegistry.getKey(holder.value()).toString() === smokeMarker)
  if (!applied) {
    throw new Error("Fabric TagLoaderMixin automatic apply was not visible after reload")
  }
  console.info("FABRIC-CI-SMOKE: tags applied ok")
})

ServerEvents.started(event => {
  console.info("FABRIC-CI-SMOKE: server started")
  event.server.reloadResources(event.server.getPackRepository().getSelectedIds())
})
