;(function () {
  const modulesTable = globalThis.__nekoNodeInternal.modules

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

  function builtinModuleNames(): string[] {
    return Object.keys(modulesTable).filter((id) => !id.startsWith('node:'))
  }

  function isBuiltin(id: unknown): boolean {
    const name = String(id)
    const bare = name.startsWith('node:') ? name.slice(5) : name
    return Object.prototype.hasOwnProperty.call(modulesTable, bare)
  }

  const moduleApi = {
    createRequire,
    get builtinModules(): string[] { return builtinModuleNames() },
    isBuiltin
  }

  globalThis.__nekoNodeDefine(['module', 'node:module'], moduleApi)
})()
