ServerEvents.tags("minecraft:item", event => {
  const tag = "minecraft:planks"
  const marker = "minecraft:diamond"
  event.add(tag, marker)
  event.apply()
  if (!event.getEntries(tag).some(entry => entry.includes(marker))) {
    throw new Error("Fabric tag add was not applied during ServerEvents.tags")
  }
  event.remove(tag, marker)
  event.apply()
  if (event.getEntries(tag).some(entry => entry.includes(marker))) {
    throw new Error("Fabric tag remove was not applied during ServerEvents.tags")
  }
  console.info("FABRIC-CI-SMOKE: tags event ok")
})

ServerEvents.started(event => {
  console.info("FABRIC-CI-SMOKE: server started")
  event.server.reloadResources(event.server.getPackRepository().getSelectedIds())
})
