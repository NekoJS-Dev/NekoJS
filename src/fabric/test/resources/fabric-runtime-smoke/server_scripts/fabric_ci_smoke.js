import { ServerEvents } from "nekojs:event/versioned"

const schemaRecipeId = "fabric_ci:schema_smelting_result"

ServerEvents.recipes(event => {
  const recipe = event.recipes.fabric_ci.schema_smelting("minecraft:smooth_stone", "minecraft:andesite")
    .id(schemaRecipeId)
  const json = JSON.parse(recipe.json().toString())
  if (json.type !== "minecraft:smelting" || json.cookingtime !== 73) {
    throw new Error("Fabric recipe schema did not apply the serializer, field path, and default")
  }
  recipe.validate()
})

ServerEvents.afterRecipes(event => {
  if (event.count({ id: schemaRecipeId }) !== 1) {
    throw new Error("Fabric did not load the recipe created through the JSON schema")
  }
  console.info("FABRIC-CI-SMOKE: recipe JSON schema ok")
})

if (typeof ServerEvents.started !== "function") {
  throw new Error("Fabric smoke bindings are unavailable")
}

ServerEvents.started(event => {
  console.info("FABRIC-CI-SMOKE: server started")
  // 票 31 AC：真实兼容面调用。level.spawnLightning 经运行时 Graal remapper 解析到
  // inject.MixinLevel 注入的 LevelExtension#neko$spawnLightning，再经共享树 McVersionCompat
  //（ServiceLoader provider = 节点 override Fabric26xVersionCompat）取闪电实体类型。
  // provider 缺失时这里会得到 McVersionCompat 静态初始化失败（ExceptionInInitializerError，
  // 潜伏崩溃）——不允许静默通过：先打出明确 rejection 标记再抛出，CI 以 ok 标记判定。
  try {
    const level = event.server.overworld()
    const bolt = level.spawnLightning(0.5, 100.0, 0.5)
    console.info("FABRIC-CI-SMOKE: spawnLightning ok, bolt=" + (bolt != null))
  } catch (e) {
    console.info("FABRIC-CI-SMOKE: spawnLightning rejected: " + e)
    throw e
  }
})
