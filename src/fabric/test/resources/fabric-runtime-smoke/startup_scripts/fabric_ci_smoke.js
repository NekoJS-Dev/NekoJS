import { StartupEvents } from "nekojs:event/versioned"

if (typeof StartupEvents.registry !== "function") {
  throw new Error("Fabric smoke bindings are unavailable")
}
console.info("FABRIC-CI-SMOKE: startup bindings ok")
