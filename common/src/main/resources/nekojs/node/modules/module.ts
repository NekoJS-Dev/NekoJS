;(function () {
  interface NekoRequire {
    (id: string): unknown
    resolve(id: string): string
  }

  function createRequire(metaUrl: string): NekoRequire {
    const parent: string = String(metaUrl)
    const require: NekoRequire = function require(id: string): unknown {
      return globalThis.__nekoScriptLoader.requireFrom(parent, String(id))
    }
    require.resolve = function resolve(id: string): string {
      return globalThis.__nekoScriptModuleLoaderHost.resolveToString(parent, String(id))
    }
    return require
  }

  globalThis.__nekoNodeDefine(['module', 'node:module'], { createRequire })
})()
