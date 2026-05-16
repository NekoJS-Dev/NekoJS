;(function () {
  function createRequire(metaUrl) {
    const parent = String(metaUrl)
    const require = function require(id) {
      return globalThis.__nekoScriptLoader.requireFrom(parent, String(id))
    }
    require.resolve = function resolve(id) {
      return globalThis.__nekoScriptModuleLoaderHost.resolveToString(parent, String(id))
    }
    return require
  }

  globalThis.__nekoNodeDefine(['module', 'node:module'], { createRequire })
})()
