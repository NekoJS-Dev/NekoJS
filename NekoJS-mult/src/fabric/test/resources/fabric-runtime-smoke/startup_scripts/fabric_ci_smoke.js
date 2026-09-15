if (typeof RegistryEvents === "undefined" || typeof ServerEvents === "undefined") {
  throw new Error("Fabric smoke bindings are unavailable")
}
console.info("FABRIC-CI-SMOKE: startup bindings ok")
