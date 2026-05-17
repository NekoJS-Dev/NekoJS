;(function () {
  const host = globalThis.__nekoScriptModuleLoaderHost

  function requireHost() {
    if (!host) throw new Error('NekoJS script loader host is unavailable.')
    return host
  }

  function createModule(filename) {
    return { id: filename, filename, exports: {}, loaded: false }
  }

  function executeModule(module, requireFn, resolveFn, filename, dirname, code) {
    const localRequire = function require(id) {
      return requireFn(String(id))
    }
    localRequire.resolve = function resolve(id) {
      return resolveFn(String(id))
    }
    const source = `${code}\n//# sourceURL=${filename}`
    const fn = new Function('exports', 'require', 'module', '__filename', '__dirname', source)
    fn(module.exports, localRequire, module, filename, dirname)
  }

  function resolveSpecial(specifier) {
    const resolved = globalThis.__nekoNodeResolve(String(specifier))
    if (resolved === globalThis.__nekoNodeNoModule) {
      throw new Error(`Cannot resolve module: ${specifier}`)
    }
    return resolved
  }

  requireHost().configure(executeModule, createModule, resolveSpecial, JSON.parse.bind(JSON))

  globalThis.__nekoNativeImport = function nativeImport(parentPath, specifier) {
    return Promise.resolve(requireHost().nativeImportAsync(String(parentPath), String(specifier)))
  }

  globalThis.__nekoScriptLoader = {
    loadEntry(entryPath) {
      return requireHost().loadEntry(String(entryPath))
    },
    loadEntryAsync(entryPath) {
      return Promise.resolve(requireHost().loadEntryAsync(String(entryPath)))
    },
    requireFrom(parentPath, specifier) {
      return requireHost().requireFrom(String(parentPath), String(specifier))
    },
    clearCache() {
      requireHost().clearCache()
    },
    clearRuntimeCache() {
      requireHost().clearRuntimeCache()
    },
    invalidateAffectedModules(modulePath) {
      requireHost().invalidateAffectedModules(String(modulePath))
    },
    affectedEntries(modulePath) {
      return requireHost().affectedEntries(String(modulePath))
    }
  }
})()
